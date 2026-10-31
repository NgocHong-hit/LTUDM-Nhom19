package Client_Socket;

import common.CryptoUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.PublicKey;
import javax.crypto.SecretKey;

public class Client {
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private SecretKey aesKey;
    private PublicKey serverPublicKey;
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
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            output = new PrintWriter(socket.getOutputStream(), true);

            // Bắt tay trao đổi khóa mã hóa RSA/AES
            String publicKeyStr = input.readLine();
            serverPublicKey = CryptoUtil.stringToPublicKey(publicKeyStr);

            aesKey = CryptoUtil.generateAESKey();
            String aesKeyStr = java.util.Base64.getEncoder().encodeToString(aesKey.getEncoded());
            String encryptedAESKey = CryptoUtil.encryptRSA(aesKeyStr, serverPublicKey);

            output.println(encryptedAESKey);
            System.out.println("Kết nối mã hóa lai (RSA/AES) thành công!");
            return true;
        } catch (Exception e) {
            System.err.println("Lỗi kết nối Server: " + e.getMessage());
            return false;
        }
    }

    public synchronized String sendRequest(String plainTextData) {
        try {
            if (socket == null || socket.isClosed()) {
                if (!connectToServer()) return "ERROR|Không thể kết nối đến Server";
            }

            String encryptedRequest = CryptoUtil.encryptAES(plainTextData, aesKey);
            output.println(encryptedRequest);

            String encryptedResponse = input.readLine();
            if (encryptedResponse == null) return "ERROR|Server ngắt kết nối";

            return CryptoUtil.decryptAES(encryptedResponse, aesKey);
        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }
}
