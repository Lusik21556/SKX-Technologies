package me.Lusik21556.skxloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkxFiles {

    private static final String CONFIG_CUT = "# --- SKX ---";
    private static final String HEADER_END = "---";

    public record SkxFile(String licenseKey, String pluginId, String version,
                          String encryptedBody, String plainConfig) {}

    public static SkxFile read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

        StringBuilder plain = new StringBuilder();
        int i = 0;

        if (lines.stream().anyMatch(l -> l.trim().equals(CONFIG_CUT))) {
            while (i < lines.size() && !lines.get(i).trim().equals(CONFIG_CUT)) {
                plain.append(lines.get(i)).append('\n');
                i++;
            }
            i++;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        int bodyStart = -1;
        for (; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.equals(HEADER_END)) { bodyStart = i + 1; break; }
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        if (bodyStart == -1) throw new IOException("no --- separator in .skx");

        StringBuilder body = new StringBuilder();
        for (int j = bodyStart; j < lines.size(); j++) body.append(lines.get(j));

        return new SkxFile(
                headers.get("license-key"),
                headers.get("plugin-id"),
                headers.get("version"),
                body.toString().trim(),
                plain.toString()
        );
    }
}
