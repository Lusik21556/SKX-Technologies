package me.Lusik21556.skxobfuscator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public class Main {

    private static final String CUT = "# --- SKX ---";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { help(); return; }
        LocalKeyStore keys = LocalKeyStore.defaultStore();
        switch (args[0].toLowerCase()) {
            case "encrypt" -> encrypt(args, keys);
            case "decrypt" -> decrypt(args, keys);
            case "keys"    -> keys(args, keys);
            default        -> help();
        }
    }

    private static void encrypt(String[] args, LocalKeyStore keys) throws Exception {
        if (args.length < 2) { System.err.println("usage: encrypt <file.sk> [options]"); return; }

        Path in = Path.of(args[1]);
        if (!Files.exists(in)) { System.err.println("no such file: " + in); return; }

        String pluginId = arg(args, "--plugin-id", in.getFileName().toString().replace(".sk", ""));
        String version  = arg(args, "--version", "1.0.0");
        String license  = arg(args, "--license-key", LocalKeyStore.generateLicenseKey());

        if (keys.get(license).isPresent()) {
            System.err.println("license key already used: " + license);
            return;
        }

        String source = Files.readString(in, StandardCharsets.UTF_8);

        // everything above the # --- SKX --- line stays readable (options, config)
        // everything below gets encrypted.
        String plainConfig = "";
        String body = source;
        int cut = source.indexOf("\n" + CUT);
        if (cut != -1) {
            plainConfig = source.substring(0, cut + 1);
            body = source.substring(cut + 1 + CUT.length()).stripLeading();
        } else if (source.startsWith(CUT)) {
            body = source.substring(CUT.length()).stripLeading();
        }

        byte[] plain = body.getBytes(StandardCharsets.UTF_8);
        String checksum = Crypto.checksum(plain);
        String aesKey = Crypto.newKey();
        String iv = Crypto.newIv();
        String encrypted = Crypto.encrypt(plain, aesKey, iv);

        Path out = sibling(in, in.getFileName().toString().replace(".sk", ".skx"));
        SkxFiles.write(new SkxFiles.SkxFile(license, pluginId, version, iv, checksum, encrypted, plainConfig), out);
        keys.put(new LocalKeyStore.KeyEntry(license, aesKey, pluginId, LocalKeyStore.today()));

        System.out.println("encrypted " + in + " -> " + out);
        System.out.println("license key: " + license);
        System.out.println("plugin id:   " + pluginId);
        System.out.println("config kept: " + (plainConfig.isEmpty() ? "none" : plainConfig.lines().count() + " lines"));
        System.out.println("key stored:  " + keys.getStorePath());
        System.out.println();
        System.out.println("your key server needs these four for license " + license + ":");
        System.out.println("  aesKey:   " + aesKey);
        System.out.println("  iv:       " + iv);
        System.out.println("  checksum: " + checksum);
    }

    private static void decrypt(String[] args, LocalKeyStore keys) throws Exception {
        if (args.length < 2) { System.err.println("usage: decrypt <file.skx> [--key <aesKey>]"); return; }

        Path in = Path.of(args[1]);
        if (!Files.exists(in)) { System.err.println("no such file: " + in); return; }

        SkxFiles.SkxFile skx = SkxFiles.read(in);
        String aesKey = arg(args, "--key", null);
        if (aesKey == null) {
            Optional<LocalKeyStore.KeyEntry> e = keys.get(skx.licenseKey());
            if (e.isEmpty()) {
                System.err.println("no stored key for " + skx.licenseKey() + ", pass --key <aesKey>");
                return;
            }
            aesKey = e.get().aesKey();
        }

        byte[] plain = Crypto.decrypt(skx.encryptedBody(), aesKey, skx.iv());
        if (!Crypto.checksum(plain).equals(skx.checksum())) {
            System.err.println("checksum mismatch, wrong key or broken file.");
            return;
        }

        String full = skx.plainConfig() + new String(plain, StandardCharsets.UTF_8);
        Path out = sibling(in, in.getFileName().toString().replace(".skx", ".sk"));
        Files.writeString(out, full, StandardCharsets.UTF_8);
        System.out.println("decrypted -> " + out);
    }

    private static void keys(String[] args, LocalKeyStore keys) throws Exception {
        String sub = args.length > 1 ? args[1] : "list";

        switch (sub) {
            case "list" -> {
                Collection<LocalKeyStore.KeyEntry> all = keys.all();
                if (all.isEmpty()) { System.out.println("no keys stored."); return; }
                System.out.printf("%-18s %-20s %-12s %s%n", "license", "plugin id", "created", "aes (short)");
                System.out.println("-".repeat(72));
                for (LocalKeyStore.KeyEntry e : all) {
                    System.out.printf("%-18s %-20s %-12s %s%n",
                            e.licenseKey(), e.pluginId(), e.created(), e.aesKey().substring(0, 12) + "...");
                }
            }
            case "show" -> {
                if (args.length < 3) { System.err.println("usage: keys show <license-key>"); return; }
                keys.get(args[2]).ifPresentOrElse(e -> {
                    System.out.println("license: " + e.licenseKey());
                    System.out.println("plugin:  " + e.pluginId());
                    System.out.println("created: " + e.created());
                    System.out.println("aes key: " + e.aesKey());
                }, () -> System.err.println("not found: " + args[2]));
            }
            case "remove" -> {
                if (args.length < 3) { System.err.println("usage: keys remove <license-key>"); return; }
                System.out.println(keys.remove(args[2]) ? "removed " + args[2] : "not found: " + args[2]);
            }
            default -> System.err.println("usage: keys list | keys show <key> | keys remove <key>");
        }
    }

    private static void help() {
        System.out.println("skx obfuscator");
        System.out.println();
        System.out.println("mark where encryption starts in your .sk:");
        System.out.println("    options:");
        System.out.println("        my_value: 5");
        System.out.println("    " + CUT + "      (everything above stays plain text)");
        System.out.println("    on load:");
        System.out.println("        ...");
        System.out.println();
        System.out.println("commands:");
        System.out.println("  encrypt <file.sk> [--plugin-id <id>] [--version <ver>] [--license-key <key>]");
        System.out.println("  decrypt <file.skx> [--key <aesKey>]");
        System.out.println("  keys list | keys show <key> | keys remove <key>");
        System.out.println();
        System.out.println("keys live in ~/.skx/keys.json");
    }

    private static Path sibling(Path file, String name) {
        return file.getParent() != null ? file.getParent().resolve(name) : Path.of(name);
    }

    private static String arg(String[] args, String flag, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) return args[i + 1];
        }
        return fallback;
    }
}
