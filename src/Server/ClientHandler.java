package Server;

import common.CryptoUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyPair;
import javax.crypto.SecretKey;

import Server.db.UserDAO;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private KeyPair rsaKeyPair;
    private Server_socket server;
    private SecretKey aesKey;
    private UserDAO userDAO;

    public ClientHandler(Socket clientSocket, KeyPair rsaKeyPair, Server_socket server) {
        this.clientSocket = clientSocket;
        this.rsaKeyPair = rsaKeyPair;
        this.server = server;
        this.userDAO = new UserDAO();
    }

    @Override
    public void run() {
        try (
            PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"))
        ) {
            // 1. Gửi RSA Public Key cho Client dưới dạng String Base64
            String publicKeyStr = CryptoUtil.publicKeyToString(rsaKeyPair.getPublic());
            output.println(publicKeyStr);

            // 2. Nhận Encrypted AES Key và dùng RSA Private Key để giải mã
            String encryptedAESKey = input.readLine();
            if (encryptedAESKey == null || encryptedAESKey.isEmpty()) return;

            String aesKeyStr = CryptoUtil.decryptRSA(encryptedAESKey, rsaKeyPair.getPrivate());
            this.aesKey = CryptoUtil.stringToAESKey(aesKeyStr);

            // 3. Vòng lặp nhận dữ liệu đã mã hóa AES từ Client
            while (true) {
                String encryptedRequest = input.readLine();
                if (encryptedRequest == null) break;

                // Giải mã Request bằng AES
                String request = CryptoUtil.decryptAES(encryptedRequest, aesKey);
                System.out.println("Request nhận được: " + request);

                // Xử lý request (ví dụ: LOGIN, REGISTER...)
                String response = processRequest(request);

                // Mã hóa Response bằng AES và gửi lại Client
                String encryptedResponse = CryptoUtil.encryptAES(response, aesKey);
                output.println(encryptedResponse);
            }
        } catch (Exception e) {
            System.err.println("Lỗi luồng ClientHandler: " + e.getMessage());
        } finally {
            server.removeClient(this);
            closeSocket();
        }
    }

    private String processRequest(String request) {
        String[] parts = request.split("\\|", -1);
        String action = parts[0];

        switch (action) {
            case "REQUEST_OTP":
            // Cấu trúc: REQUEST_OTP|email
            String emailForOTP = parts[1];
            
            // 1. Kiểm tra Email đã tồn tại trong CSDL chưa
            if (userDAO.isEmail(emailForOTP)) {
                return "ERROR|Email này đã được sử dụng!";
            }

            // 2. Tạo mã OTP 6 số & Lưu vào CSDL/Bộ nhớ
            String otpCode = EmailUtil.generateOTP();
            userDAO.saveOTP(emailForOTP, otpCode);

            // 3. Gửi email cho người dùng
            boolean isSent = EmailUtil.sendOTP(emailForOTP, otpCode);
            if (isSent) {
                return "SEND_OTP_SUCCESS|OTP Sent";
            } else {
                return "ERROR|Không thể gửi Email OTP. Vui lòng kiểm tra lại Email!";
            }

            case "REGISTER":
                // Cấu trúc: REGISTER|fullName|gender|email|dob|password|otp
                String fullName = parts[1];
                String gender = parts[2];
                String email = parts[3];
                String dob = parts[4];
                String password = parts[5];
                String inputOtp = parts[6];

                // 1. Kiểm tra mã OTP
                boolean isValidOtp = userDAO.verifyOTP(email, inputOtp);
                if (!isValidOtp) {
                    return "REGISTER_FAIL|Mã OTP không đúng hoặc đã hết hạn!";
                }

                // 2. Thêm tài khoản mới vào CSDL
                boolean isSuccess = userDAO.registerUser(fullName, gender, email, dob, password);
                if (isSuccess) {
                    return "REGISTER_SUCCESS|Tạo tài khoản thành công";
                } else {
                    return "REGISTER_FAIL|Lỗi hệ thống khi lưu thông tin người dùng!";
                }
            case "LOGIN":
                if (parts.length < 3) {
                    return "LOGIN_FAIL|Thống tin đăng nhập không hợp lệ!";
                }
                String loginEmail = parts[1];
                String loginPass = parts[2];

                String loginSuccess = userDAO.Login(loginEmail, loginPass);
                if (loginSuccess!= null) {
                    return "LOGIN_SUCCESS|" + loginEmail;
                } else {
                    return "LOGIN_FAIL|Tài khoản hoặc mật khẩu không chính xác!";
                }

            default:
                return "ERROR|Lệnh không hợp lệ";
        }
    }

    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}