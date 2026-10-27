package Server;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;
import javax.mail.*;
import javax.mail.internet.*;

public class EmailUtil{
    private static String SENDER_EMAIL;
    private static String APP_PASSWORD;
    private static String SMTP_HOST;
    private static String SMTP_PORT;

    static {
        try (InputStream input = new FileInputStream("config.properties")){
            Properties prop = new Properties();
            prop.load(input);
            SENDER_EMAIL = prop.getProperty("SMTP_EMAIL");
            APP_PASSWORD = prop.getProperty("SMTP_APP_PASSWORD");
            SMTP_HOST = prop.getProperty("SMTP_HOST", "smtp.gmail.com");
            SMTP_PORT = prop.getProperty("SMTP_PORT", "587");
        }catch (Exception e) {
            System.err.println("Lỗi");
            e.printStackTrace();
        }
    }

    public static String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    public static boolean sendOTP(String recipientEmail, String otpCode) {
        if (SENDER_EMAIL == null || APP_PASSWORD == null) {
            System.err.println("Cấu hình Email chưa được thiết lập trong config.properties!");
            return false;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL, "Quiz App System"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
            message.setSubject("MÃ XÁC THỰC OTP ĐĂNG KÝ TÀI KHOẢN QUIZ");
            message.setText("Mã OTP xác thực tài khoản của bạn là: " + otpCode + "\nMã này có hiệu lực trong 10 phút.");

            Transport.send(message);
            System.out.println(" Đã gửi OTP thành công tới: " + recipientEmail);
            return true;
        } catch (Exception e) {
            System.err.println("Lỗi gửi email OTP: " + e.getMessage());
            return false;
        }
    }
}