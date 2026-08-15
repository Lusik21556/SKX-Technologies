package me.Lusik21556.skxobfuscator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public class LocalKeyStore {

    private final Path storePath;
    private final Map<String, KeyEntry> entries = new LinkedHashMap<>();

    public record KeyEntry(String licenseKey, String aesKey, String pluginId, String created) {}

    public LocalKeyStore(Path storePath) throws IOException {
        this.storePath = storePath;
        if (Files.exists(storePath)) load();
    }

    public static LocalKeyStore defaultStore() throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), ".skx");
        Files.createDirectories(dir);
        return new LocalKeyStore(dir.resolve("keys.json"));
    }

    public void put(KeyEntry entry) throws IOException {
        entries.put(entry.licenseKey(), entry);
        save();
    }

    public Optional<KeyEntry> get(String licenseKey) {
        return Optional.ofNullable(entries.get(licenseKey));
    }

    public Collection<KeyEntry> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public boolean remove(String licenseKey) throws IOException {
        boolean removed = entries.remove(licenseKey) != null;
        if (removed) save();
        return removed;
    }

    private void load() throws IOException {
        String raw = Files.readString(storePath, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty() || raw.equals("{}")) return;

        raw = raw.substring(1, raw.length() - 1).trim();
        if (raw.isEmpty()) return;

        String[] pairs = splitTopLevel(raw);
        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            String licenseKey = pair.substring(0, colon).trim().replace("\"", "");
            String obj = pair.substring(colon + 1).trim();
            obj = obj.substring(1, obj.length() - 1);

            Map<String, String> fields = parseFields(obj);
            entries.put(licenseKey, new KeyEntry(
                licenseKey,
                fields.get("aesKey"),
                fields.get("pluginId"),
                fields.get("created")
            ));
        }
    }

    private void save() throws IOException {
        StringBuilder sb = new StringBuilder("{\n");
        Iterator<KeyEntry> it = entries.values().iterator();
        while (it.hasNext()) {
            KeyEntry e = it.next();
            sb.append("  \"").append(e.licenseKey()).append("\": {\n");
            sb.append("    \"aesKey\": \"").append(e.aesKey()).append("\",\n");
            sb.append("    \"pluginId\": \"").append(e.pluginId()).append("\",\n");
            sb.append("    \"created\": \"").append(e.created()).append("\"\n");
            sb.append("  }");
            if (it.hasNext()) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        Files.writeString(storePath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseFields(String obj) {
        Map<String, String> map = new HashMap<>();
        for (String pair : splitTopLevel(obj)) {
            int colon = pair.indexOf(':');
            String key = pair.substring(0, colon).trim().replace("\"", "");
            String val = pair.substring(colon + 1).trim().replace("\"", "");
            map.put(key, val);
        }
        return map;
    }

    private static String[] splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '{') depth++;
                if (c == '}') depth--;
                if (c == ',' && depth == 0) {
                    parts.add(s.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        String last = s.substring(start).trim();
        if (!last.isEmpty()) parts.add(last);
        return parts.toArray(new String[0]);
    }

    public Path getStorePath() {
        return storePath;
    }

    public static String generateLicenseKey() {
        Random rng = new Random();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return "LS-" + rand(chars, 4, rng) + "-" + rand(chars, 4, rng);
    }

    private static String rand(String chars, int len, Random rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    public static String today() {
        return LocalDate.now().toString();
    }
}
