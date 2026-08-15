package me.Lusik21556.skxloader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Set;

public class PinnedTrustManager implements X509TrustManager {

    // SHA-256 of the leaf cert SPKI. keep the backup pin set to your renewal cert.
    private static final Set<String> PINS = Set.of(
            "xAGCPTKzSKbYm5m4MnLJVUzVJ9iP6vCkJZaMLuKGsS8=",
            "REPLACE_WITH_BACKUP_PIN_BASE64="
    );

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) throw new CertificateException("empty cert chain");
        try {
            byte[] spki = chain[0].getPublicKey().getEncoded();
            String pin = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(spki));
            if (!PINS.contains(pin)) throw new CertificateException("pin mismatch, dropping connection");
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(e);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    public static SSLSocketFactory factory() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{ new PinnedTrustManager() }, new SecureRandom());
        return ctx.getSocketFactory();
    }
}
