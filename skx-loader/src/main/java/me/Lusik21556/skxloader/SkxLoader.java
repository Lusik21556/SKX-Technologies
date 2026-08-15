package me.Lusik21556.skxloader;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SkxLoader extends JavaPlugin {

    private static final String KEY_URL       = "https://lusik.dev/api/loader/key?license=";
    private static final String HEARTBEAT_URL = "https://lusik.dev/api/loader/heartbeat";
    private static final String SHUTDOWN_URL  = "https://lusik.dev/api/loader/shutdown";

    // pull fresh keys every 30 min so a revoked license stops working without a restart
    private static final long ROTATE_TICKS = 20L * 60 * 30;

    private record Bundle(byte[] aesKey, byte[] iv, String checksum) {
        void wipe() {
            Arrays.fill(aesKey, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    private SkriptBridge bridge;
    private File scriptsDir;
    private SSLSocketFactory pinned;

    private final Set<String> licenses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onEnable() {
        if (AgentDetector.present(getLogger())) {
            getLogger().severe("Java agent on the JVM. Not starting.");
            disableSelf();
            return;
        }

        try {
            pinned = PinnedTrustManager.factory();
        } catch (Exception e) {
            getLogger().severe("cert pinning setup failed: " + e.getMessage());
            disableSelf();
            return;
        }

        Plugin skript = getServer().getPluginManager().getPlugin("Skript");
        if (skript == null) {
            getLogger().severe("Skript not installed.");
            disableSelf();
            return;
        }

        try {
            Path jar = Path.of(skript.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (!new SkriptJarVerifier(getLogger()).verify(jar)) {
                disableSelf();
                return;
            }
        } catch (Exception e) {
            getLogger().severe("could not locate Skript jar: " + e.getMessage());
            disableSelf();
            return;
        }

        try {
            bridge = new SkriptBridge(this, getLogger());
        } catch (NoSuchMethodException e) {
            getLogger().severe("Skript version not supported. Update SkxLoader or roll Skript back.");
            disableSelf();
            return;
        }

        scriptsDir = new File(skript.getDataFolder(), "scripts");

        getCommand("skxload").setExecutor(new LoadCommand(this));
        getCommand("skxreload").setExecutor(new ReloadCommand(this));

        Bukkit.getScheduler().runTaskAsynchronously(this, this::loadAll);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::heartbeat, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::rotate, ROTATE_TICKS, ROTATE_TICKS);
    }

    @Override
    public void onDisable() {
        if (!licenses.isEmpty()) postJson(SHUTDOWN_URL, licenses);
    }

    private void disableSelf() {
        getServer().getPluginManager().disablePlugin(this);
    }

    private void heartbeat() {
        if (!licenses.isEmpty()) postJson(HEARTBEAT_URL, licenses);
    }

    private void rotate() {
        File[] files = listSkx();
        if (files == null) return;
        for (File f : files) {
            try {
                reloadSkx(f.toPath());
            } catch (Exception e) {
                getLogger().warning("rotate failed on " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    void loadAll() {
        File[] files = listSkx();
        if (files == null) {
            getLogger().info("no .skx scripts in the scripts folder.");
            return;
        }
        int ok = 0, bad = 0;
        for (File f : files) {
            try { loadSkx(f.toPath()); ok++; }
            catch (Exception e) { getLogger().severe("load failed on " + f.getName() + ": " + e.getMessage()); bad++; }
        }
        getLogger().info(ok + " loaded, " + bad + " failed.");
    }

    void loadSkx(Path path) throws Exception {
        byte[] content = build(path);
        String name = scriptName(path);
        Bukkit.getScheduler().runTask(this, () -> {
            try { bridge.loadFromMemory(content, name); }
            catch (Exception e) { getLogger().severe("bridge error on " + name + ": " + e.getMessage()); }
            finally { Arrays.fill(content, (byte) 0); }
        });
    }

    void reloadSkx(Path path) throws Exception {
        byte[] content = build(path);
        String name = scriptName(path);
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                bridge.unloadScript(name);
                bridge.loadFromMemory(content, name);
            } catch (Exception e) {
                getLogger().severe("reload error on " + name + ": " + e.getMessage());
            } finally {
                Arrays.fill(content, (byte) 0);
            }
        });
    }

    private byte[] build(Path path) throws Exception {
        SkxFiles.SkxFile skx = SkxFiles.read(path);
        if (skx.licenseKey() == null || skx.encryptedBody().isEmpty()) {
            throw new Exception("bad .skx header in " + path.getFileName());
        }

        Bundle b = fetchKey(skx.licenseKey());
        if (b == null) throw new Exception("server rejected the license");

        try {
            byte[] plain = Crypto.decrypt(skx.encryptedBody(), b.aesKey(), b.iv());
            if (!Crypto.checksumOk(plain, b.checksum())) {
                throw new Exception("checksum mismatch on " + path.getFileName());
            }
            licenses.add(skx.licenseKey());
            String full = skx.plainConfig() + new String(plain, StandardCharsets.UTF_8);
            return full.getBytes(StandardCharsets.UTF_8);
        } finally {
            b.wipe();
        }
    }

    private File[] listSkx() {
        File[] files = scriptsDir.listFiles(f -> f.getName().endsWith(".skx"));
        return (files == null || files.length == 0) ? null : files;
    }

    private static String scriptName(Path path) {
        return path.getFileName().toString().replace(".skx", ".sk");
    }

    File getScriptsDir() {
        return scriptsDir;
    }

    private HttpsURLConnection open(String url) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setSSLSocketFactory(pinned);
        conn.setRequestProperty("User-Agent", "SkxLoader/1.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    private Bundle fetchKey(String license) throws Exception {
        HttpsURLConnection conn = open(KEY_URL + license);
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() != 200) return null;

        String json;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            for (String line; (line = br.readLine()) != null; ) sb.append(line);
            json = sb.toString().trim();
        }

        String key = jsonValue(json, "aesKey");
        String iv  = jsonValue(json, "iv");
        String sum = jsonValue(json, "checksum");
        if (key == null || iv == null || sum == null) return null;

        return new Bundle(Base64.getDecoder().decode(key), Base64.getDecoder().decode(iv), sum);
    }

    private void postJson(String url, Set<String> keys) {
        try {
            HttpsURLConnection conn = open(url);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            StringBuilder json = new StringBuilder("{\"licenses\":[");
            boolean first = true;
            for (String k : keys) {
                if (!first) json.append(',');
                json.append('"').append(k).append('"');
                first = false;
            }
            json.append("]}");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
        } catch (Exception ignored) {
        }
    }

    private static String jsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start == -1) return null;
        start += needle.length();
        int end = json.indexOf('"', start);
        return end == -1 ? null : json.substring(start, end);
    }
}
