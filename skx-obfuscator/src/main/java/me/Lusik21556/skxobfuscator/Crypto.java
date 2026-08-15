package me.Lusik21556.skxobfuscator;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class Crypto {

    private static final String ALGO = "AES/CBC/PKCS5Padding";

    public static String newKey() throws Exception {
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256, new SecureRandom());
        return Base64.getEncoder().encodeToString(gen.generateKey().getEncoded());
    }

    public static String newIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
    }

    public static String encrypt(byte[] plaintext, String keyB64, String ivB64) throws Exception {
        Cipher c = cipher(Cipher.ENCRYPT_MODE, keyB64, ivB64);
        return Base64.getEncoder().encodeToString(c.doFinal(plaintext));
    }

    public static byte[] decrypt(String bodyB64, String keyB64, String ivB64) throws Exception {
        Cipher c = cipher(Cipher.DECRYPT_MODE, keyB64, ivB64);
        return c.doFinal(Base64.getDecoder().decode(bodyB64));
    }

    public static String checksum(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static Cipher cipher(int mode, String keyB64, String ivB64) throws Exception {
        Cipher c = Cipher.getInstance(ALGO);
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(keyB64), "AES");
        IvParameterSpec iv = new IvParameterSpec(Base64.getDecoder().decode(ivB64));
        c.init(mode, key, iv);
        return c;
    }
}
