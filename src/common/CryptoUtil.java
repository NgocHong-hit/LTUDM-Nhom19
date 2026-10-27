package common;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtil {

    // 1. SINH KHÓA & CHUYỂN ĐỔI KHÓA 
    // Sinh cặp khóa RSA 2048-bit cho Server 
    public static KeyPair generateRSAKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    //  Sinh khóa phiên AES 128-bit cho Client 
    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        return keyGen.generateKey();
    }

    public static String publicKeyToString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    //  Chuyển chuỗi Base64 nhận từ Socket thành PublicKey đối tượng 
    public static PublicKey stringToPublicKey(String base64PublicKey) throws Exception {
        byte[] publicKeyBytes = Base64.getDecoder().decode(base64PublicKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    }

    // Chuyển chuỗi khóa AES Base64 thành đối tượng SecretKeySpec 
    public static SecretKeySpec stringToAESKey(String base64AESKey) {
        byte[] decodedKey = Base64.getDecoder().decode(base64AESKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    // MÃ HÓA & GIẢI MÃ RSA (BẤT ĐỐI XỨNG - DÙNG CHO BẮT TAY)

    //  Client mã hóa chuỗi khóa AES bằng RSA Public Key của Server 
    public static String encryptRSA(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    //  Server giải mã lấy khóa AES bằng RSA Private Key của mình 
    public static String decryptRSA(String encryptedAESKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decoded = Base64.getDecoder().decode(encryptedAESKey);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // 3. MÃ HÓA & GIẢI MÃ AES 

    // Mã hóa dữ liệu bằng khóa AES (Dùng chung cho cả Client & Server) 
    public static String encryptAES(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    // Giải mã dữ liệu bằng khóa AES
    public static String decryptAES(String encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}