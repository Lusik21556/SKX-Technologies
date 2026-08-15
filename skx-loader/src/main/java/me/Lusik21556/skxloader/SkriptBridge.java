package me.Lusik21556.skxloader;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.config.Config;
import ch.njol.util.OpenCloseable;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class SkriptBridge {

    private final Method loadScripts;
    private final Path scriptsDir;
    private final PreLoadListenerDetector watch;
    private final Logger log;

    public SkriptBridge(Plugin plugin, Logger log) throws NoSuchMethodException {
        this.log = log;
        this.loadScripts = ScriptLoader.class.getDeclaredMethod("loadScripts", List.class, OpenCloseable.class);
        this.loadScripts.setAccessible(true);

        Plugin skript = plugin.getServer().getPluginManager().getPlugin("Skript");
        this.scriptsDir = skript.getDataFolder().toPath().resolve("scripts");
        this.watch = new PreLoadListenerDetector(skript, plugin, log);
    }

    @SuppressWarnings("unchecked")
    public void loadFromMemory(byte[] plaintext, String fileName) throws Exception {
        // someone else hooking PreScriptLoadEvent can read the decrypted source mid-load. bail.
        List<String> hooked = watch.otherPlugins();
        if (!hooked.isEmpty()) {
            Arrays.fill(plaintext, (byte) 0);
            throw new SecurityException("won't load " + fileName + ", foreign PreScriptLoadEvent listener: "
                    + String.join(", ", hooked));
        }

        String text = new String(plaintext, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Arrays.fill(plaintext, (byte) 0);

        Config config = new Config(
                new ByteArrayInputStream(bytes),
                fileName,
                scriptsDir.resolve(fileName).toFile().getCanonicalFile(),
                true,
                false,
                ":"
        );

        CompletableFuture<ScriptLoader.ScriptInfo> future =
                (CompletableFuture<ScriptLoader.ScriptInfo>) loadScripts.invoke(null, List.of(config), OpenCloseable.EMPTY);

        ScriptLoader.ScriptInfo info = future.get();
        Arrays.fill(bytes, (byte) 0);
        log.info("loaded " + fileName + " (" + info.structures + " structures)");
    }

    public void unloadScript(String fileName) {
        try {
            Method getLoaded = ScriptLoader.class.getMethod("getLoadedScripts");
            Collection<?> scripts = (Collection<?>) getLoaded.invoke(null);

            Object match = null;
            for (Object script : scripts) {
                Object cfg = script.getClass().getMethod("getConfig").invoke(script);
                String name = (String) cfg.getClass().getMethod("getFileName").invoke(cfg);
                if (fileName.equals(name)) { match = script; break; }
            }
            if (match == null) return;

            for (Method m : ScriptLoader.class.getMethods()) {
                if (m.getName().equals("unloadScript") && m.getParameterCount() == 1) {
                    Object r = m.invoke(null, match);
                    if (r instanceof CompletableFuture<?> f) f.get();
                    log.info("unloaded " + fileName);
                    return;
                }
            }
            log.warning("no unloadScript(..) on this Skript build, skipping unload of " + fileName);
        } catch (Exception e) {
            log.warning("could not unload " + fileName + ": " + e.getMessage());
        }
    }
}
