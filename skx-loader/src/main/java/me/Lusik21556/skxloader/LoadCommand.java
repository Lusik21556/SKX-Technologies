package me.Lusik21556.skxloader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class LoadCommand implements TabExecutor {

    static final String TAG = ChatColor.GRAY + "[" + ChatColor.WHITE + "Skx"
            + ChatColor.AQUA + "Loader" + ChatColor.GRAY + "] " + ChatColor.WHITE;

    private final SkxLoader plugin;

    public LoadCommand(SkxLoader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(TAG + "loading all .skx files...");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.loadAll();
                sender.sendMessage(TAG + "done.");
            });
            return true;
        }

        String name = args[0].endsWith(".skx") ? args[0] : args[0] + ".skx";
        File file = new File(plugin.getScriptsDir(), name);
        if (!file.exists()) {
            sender.sendMessage(TAG + ChatColor.RED + "no such file: " + name);
            return true;
        }

        sender.sendMessage(TAG + "loading " + ChatColor.AQUA + name + ChatColor.WHITE + "...");
        Path path = file.toPath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.loadSkx(path);
                sender.sendMessage(TAG + ChatColor.GREEN + "loaded " + name);
            } catch (Exception e) {
                sender.sendMessage(TAG + ChatColor.RED + "failed: " + e.getMessage());
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return SkxTab.complete(plugin.getScriptsDir(), args);
    }
}
