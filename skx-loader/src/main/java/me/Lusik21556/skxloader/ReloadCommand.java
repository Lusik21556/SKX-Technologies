package me.Lusik21556.skxloader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static me.Lusik21556.skxloader.LoadCommand.TAG;

public class ReloadCommand implements TabExecutor {

    private final SkxLoader plugin;

    public ReloadCommand(SkxLoader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(TAG + ChatColor.RED + "usage: /skxreload <file.skx>");
            return true;
        }

        String name = args[0].endsWith(".skx") ? args[0] : args[0] + ".skx";
        File file = new File(plugin.getScriptsDir(), name);
        if (!file.exists()) {
            sender.sendMessage(TAG + ChatColor.RED + "no such file: " + name);
            return true;
        }

        sender.sendMessage(TAG + "reloading " + ChatColor.AQUA + name + ChatColor.WHITE + "...");
        Path path = file.toPath();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.reloadSkx(path);
                sender.sendMessage(TAG + ChatColor.GREEN + "reloaded " + name);
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
