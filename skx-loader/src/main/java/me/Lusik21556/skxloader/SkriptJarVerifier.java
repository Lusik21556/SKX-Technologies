package me.Lusik21556.skxloader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class SkriptJarVerifier {

    private static final String HASHES = "https://lusik.dev/api/loader/skript-hashes";

    private final Logger log;

    public SkriptJarVerifier(Logger log) {
        this.log = log;
    }

    public boolean verify(Path jar) {
        String hash;
        try {
            hash = sha256(jar);
        } catch (Exception e) {
            log.severe("could not hash Skript jar: " + e.getMessage());
            return false;
        }

        Set<String> approved = fetch();
        if (approved == null) {
            log.severe("lusik.dev unreachable, can't verify Skript. disabling.");
            return false;
        }
        if (approved.contains(hash)) return true;

        log.severe("Skript jar hash not on the list: " + hash);
        log.severe("looks like a patched Skript. disabling.");
        return false;
    }

    private Set<String> fetch() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(HASHES).openConnection();
            conn.setRequestProperty("User-Agent", "SkxLoader/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) {
                log.warning("skript-hashes returned " + conn.getResponseCode());
                return null;
            }

            String body;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                for (String line; (line = br.readLine()) != null; ) sb.append(line);
                body = sb.toString().replaceAll("[\\[\\]\\s]", "");
            }

            Set<String> out = new HashSet<>();
            for (String part : body.split(",")) {
                String h = part.replace("\"", "").trim().toLowerCase();
                if (h.matches("[0-9a-f]{64}")) out.add(h);
            }
            log.info("got " + out.size() + " approved Skript hashes.");
            return out;
        } catch (Exception e) {
            log.warning("could not fetch Skript hashes: " + e.getMessage());
            return null;
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1; ) md.update(buf, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
