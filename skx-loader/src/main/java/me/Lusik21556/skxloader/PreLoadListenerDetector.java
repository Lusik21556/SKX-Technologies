package me.Lusik21556.skxloader;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PreLoadListenerDetector {

    private static final String EVENT = "ch.njol.skript.events.bukkit.PreScriptLoadEvent";

    private final Plugin skript;
    private final Plugin loader;
    private final Method handlerList;

    public PreLoadListenerDetector(Plugin skript, Plugin loader, Logger log) {
        this.skript = skript;
        this.loader = loader;
        this.handlerList = resolve(log);
    }

    // names of plugins hooked into PreScriptLoadEvent that aren't us or Skript
    public List<String> otherPlugins() {
        if (handlerList == null) return List.of();
        try {
            HandlerList handlers = (HandlerList) handlerList.invoke(null);
            List<String> out = new ArrayList<>();
            for (RegisteredListener l : handlers.getRegisteredListeners()) {
                Plugin owner = l.getPlugin();
                if (owner != skript && owner != loader) out.add(owner.getName());
            }
            return out;
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    private static Method resolve(Logger log) {
        try {
            return Class.forName(EVENT).getMethod("getHandlerList");
        } catch (ClassNotFoundException e) {
            log.info("no PreScriptLoadEvent on this Skript build, skipping the listener check.");
            return null;
        } catch (NoSuchMethodException e) {
            log.warning("PreScriptLoadEvent has no getHandlerList(), skipping the listener check.");
            return null;
        }
    }
}
