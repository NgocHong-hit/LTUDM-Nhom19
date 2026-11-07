package common;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtil {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    private static final String RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    // =========================================================================
    // 1. TẠO & LOAD KHÓA (RSA / AES)
    // =========================================================================

    public static KeyPair generateRSAKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    public static KeyPair loadOrCreateRSAKeyPair(String pubFile, String privFile) throws Exception {
        Path pubPath = Path.of(pubFile), privPath = Path.of(privFile);
        if (Files.exists(pubPath) != Files.exists(privPath)) {
            throw new IOException("Cặp khóa Server không đầy đủ!");
        }
        if (Files.exists(pubPath)) {
            return new KeyPair(loadPublic(pubFile), loadPrivate(privFile));
        }
        KeyPair pair = generateRSAKeyPair();
        Files.write(privPath, pair.getPrivate().getEncoded(), StandardOpenOption.CREATE_NEW);
        Files.write(pubPath, pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
        return pair;
    }

    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        return keyGen.generateKey();
    }

    public static PublicKey stringToPublicKey(String base64Key) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64Key)));
    }

    public static SecretKeySpec stringToAESKey(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static PublicKey loadPublic(String file) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(file))));
    }

    public static PrivateKey loadPrivate(String file) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(file))));
    }

    // =========================================================================
    // 2. MÃ HÓA & GIẢI MÃ (RSA / AES)
    // =========================================================================

    private static Cipher createOAEPCipher(int mode, Key key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA_OAEP);
        cipher.init(mode, key, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT));
        return cipher;
    }

    public static String encryptRSA(String data, PublicKey pubKey) throws Exception {
        byte[] encrypted = createOAEPCipher(Cipher.ENCRYPT_MODE, pubKey).doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decryptRSA(String encryptedData, PrivateKey privKey) throws Exception {
        byte[] decrypted = createOAEPCipher(Cipher.DECRYPT_MODE, privKey).doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static String encryptAES(String data, SecretKey key) throws Exception {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
    }

    public static String decryptAES(String encryptedData, SecretKey key) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, decoded, 0, 12));
        byte[] decrypted = cipher.doFinal(decoded, 12, decoded.length - 12);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // =========================================================================
    // 3. KHUNG TIN NETWORK & XÁC THỰC SỐ THỨ TỰ (STREAMING)
    // =========================================================================

    public static void sendFrame(DataOutputStream out, byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_FRAME_BYTES) throw new IOException("Frame không hợp lệ");
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    public static byte[] readFrameOrNull(DataInputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) return null;
        int len = firstByte << 24 | in.readUnsignedByte() << 16 | in.readUnsignedByte() << 8 | in.readUnsignedByte();
        if (len <= 0 || len > MAX_FRAME_BYTES) throw new IOException("Frame không hợp lệ");
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    public static byte[] encrypt(SecretKey key, long seq, byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        c.updateAAD(ByteBuffer.allocate(8).putLong(seq).array());
        byte[] ct = c.doFinal(plain);
        return ByteBuffer.allocate(20 + ct.length).putLong(seq).put(iv).put(ct).array();
    }

    public static byte[] decrypt(SecretKey key, byte[] pkt) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, pkt, 8, 12));
        c.updateAAD(pkt, 0, 8);
        return c.doFinal(pkt, 20, pkt.length - 20);
    }

    // =========================================================================
    // 4. WRAP KEY & FINGERPRINT
    // =========================================================================

    public static byte[] wrapKey(PublicKey pub, SecretKey k) throws GeneralSecurityException {
        return createOAEPCipher(Cipher.ENCRYPT_MODE, pub).doFinal(k.getEncoded());
    }

    public static SecretKey unwrapKey(PrivateKey priv, byte[] wrapped) throws GeneralSecurityException {
        return new SecretKeySpec(createOAEPCipher(Cipher.DECRYPT_MODE, priv).doFinal(wrapped), "AES");
    }

    public static String fingerprint(PublicKey pub) throws GeneralSecurityException {
        byte[] fp = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fp.length; i++) {
            if (i > 0) sb.append(":");
            sb.append(String.format("%02x", fp[i]));
        }
        return sb.toString();
    }
}