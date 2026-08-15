package me.Lusik21556.skxobfuscator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkxFiles {

    private static final String HEADER_END = "---";
    private static final String CONFIG_CUT = "# --- SKX ---";

    public record SkxFile(String licenseKey, String pluginId, String version,
                          String iv, String checksum, String encryptedBody, String plainConfig) {

        public SkxFile(String licenseKey, String pluginId, String version,
                       String iv, String checksum, String encryptedBody) {
            this(licenseKey, pluginId, version, iv, checksum, encryptedBody, "");
        }
    }

    public static void write(SkxFile skx, Path dest) throws IOException {
        StringBuilder sb = new StringBuilder();

        if (skx.plainConfig() != null && !skx.plainConfig().isBlank()) {
            sb.append(skx.plainConfig().stripTrailing()).append('\n');
            sb.append(CONFIG_CUT).append('\n');
        }

        sb.append("license-key: ").append(skx.licenseKey()).append('\n');
        sb.append("plugin-id: ").append(skx.pluginId()).append('\n');
        sb.append("version: ").append(skx.version()).append('\n');
        sb.append("iv: ").append(skx.iv()).append('\n');
        sb.append("checksum: ").append(skx.checksum()).append('\n');
        sb.append(HEADER_END).append('\n');
        sb.append(skx.encryptedBody()).append('\n');

        Files.writeString(dest, sb.toString(), StandardCharsets.UTF_8);
    }

    public static SkxFile read(Path src) throws IOException {
        List<String> lines = Files.readAllLines(src, StandardCharsets.UTF_8);

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
                headers.get("iv"),
                headers.get("checksum"),
                body.toString().trim(),
                plain.toString()
        );
    }
}
