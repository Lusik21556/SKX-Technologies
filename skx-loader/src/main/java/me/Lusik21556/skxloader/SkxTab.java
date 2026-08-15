package me.Lusik21556.skxloader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class SkxTab {

    private SkxTab() {}

    static List<String> complete(File scriptsDir, String[] args) {
        if (args.length != 1) return List.of();

        File[] files = scriptsDir.listFiles(f -> f.getName().endsWith(".skx"));
        if (files == null) return List.of();

        String typed = args[0].toLowerCase();
        List<String> out = new ArrayList<>();
        for (File f : files) {
            if (f.getName().toLowerCase().startsWith(typed)) out.add(f.getName());
        }
        return out;
    }
}
