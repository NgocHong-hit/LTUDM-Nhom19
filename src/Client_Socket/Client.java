package Client_Socket;

import common.CryptoUtil;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.SecretKey;

public class Client {
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private SecretKey aesKey;
    private String serverAddress;
    private int serverPort;

    private static Client instance;

    public Client(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.connectToServer();
    }

    public static synchronized Client getInstance() {
        if (instance == null) {
            instance = new Client("127.0.0.1", 12345);
        }
        return instance;
    }

    public synchronized boolean connectToServer() {
        if (socket != null && !socket.isClosed() && socket.isConnected()) {
            return true;
        }

        try {
            socket = new Socket(serverAddress, serverPort);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // 1. Nhận Public Key từ Server và kiểm tra khóa ghim
            String serverPubKeyStr = input.readLine();
            PublicKey serverPublicKey = CryptoUtil.stringToPublicKey(serverPubKeyStr);
            
            PublicKey pinnedKey = CryptoUtil.loadPublic("server_public.key");
            if (!CryptoUtil.fingerprint(serverPublicKey).equals(CryptoUtil.fingerprint(pinnedKey))) {
                throw new SecurityException("Khóa Server không khớp với server_public.key đã ghim!");
            }

            // 2. Tạo khóa AES phiên và mã hóa gửi lên Server bằng RSA
            aesKey = CryptoUtil.generateAESKey();
            String base64AESKey = Base64.getEncoder().encodeToString(aesKey.getEncoded());
            String encryptedAESKey = CryptoUtil.encryptRSA(base64AESKey, serverPublicKey);
            output.println(encryptedAESKey);

            System.out.println("Kết nối mã hóa lai RSA/AES thành công!");
            return true;
        } catch (Exception e) {
            closeConnection();
            System.err.println("Lỗi kết nối Server: " + e.getMessage());
            return false;
        }
    }

    public synchronized String sendRequest(String plainTextData) {
        try {
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                if (!connectToServer()) return "ERROR|Không thể kết nối đến Server";
            }

            // Mã hóa Request bằng AES
            String encryptedRequest = CryptoUtil.encryptAES(plainTextData, aesKey);
            output.println(encryptedRequest);

            // Nhận và giải mã Response từ Server
            String encryptedResponse = input.readLine();
            if (encryptedResponse == null) {
                throw new IOException("Server ngắt kết nối đột ngột!");
            }

            return CryptoUtil.decryptAES(encryptedResponse, aesKey);
        } catch (Exception e) {
            closeConnection();
            return "ERROR|" + e.getMessage();
        }
    }

    public void closeConnection() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
        socket = null;
        input = null;
        output = null;
        aesKey = null;
    }
}