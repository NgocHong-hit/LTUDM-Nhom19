package Client_GUI.Controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

public class OtpVerify {

    @FXML private Label lblEmailDisplay;
    @FXML private Label lblCountdown;
    @FXML private TextField txtDigit1;
    @FXML private Label lblStatus;
    @FXML private Button btnActivate;

    private String fullName, gender, email, dob, password;
    private Timeline timeline;
    private int timeSeconds = 600; // 10 phút = 600 giây

    // Hàm nhận dữ liệu và bắt đầu đếm ngược 10 phút
    public void setRegisterData(String fullName, String gender, String email, String dob, String password) {
        this.fullName = fullName;
        this.gender = gender;
        this.email = email;
        this.dob = dob;
        this.password = password;

        if (lblEmailDisplay != null) {
            lblEmailDisplay.setText(email);
        }

        startTimer();
    }

    // Bộ đếm ngược 10:00 -> 00:00
    private void startTimer() {
        if (timeline != null) {
            timeline.stop();
        }
        timeSeconds = 600;
        timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.getKeyFrames().add(
            new KeyFrame(Duration.seconds(1), event -> {
                timeSeconds--;
                int minutes = timeSeconds / 60;
                int seconds = timeSeconds % 60;
                lblCountdown.setText(String.format("%02d:%02d", minutes, seconds));

                if (timeSeconds <= 0) {
                    timeline.stop();
                    lblStatus.setText("Mã OTP đã hết hạn! Vui lòng bấm gửi lại.");
                    lblStatus.setStyle("-fx-text-fill: #ef4444;");
                    btnActivate.setDisable(true);
                }
            })
        );
        timeline.play();
    }

   @FXML
    private void handleVerifyOtp(ActionEvent event) {
        String otp = txtDigit1.getText().trim();
        if (otp.isEmpty() || otp.length() != 6) {
            lblStatus.setText("Vui lòng nhập đủ 6 chữ số OTP!");
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        String request = String.format("REGISTER|%s|%s|%s|%s|%s|%s", fullName, gender, email, dob, password, otp);
        // Gửi request qua Client Socket
        String response = Client_Socket.Client.getInstance().sendRequest(request);

        // 2. Bắt đúng phản hồi REGISTER_SUCCESS từ Server
        if (response != null && (response.startsWith("REGISTER_SUCCESS") || response.startsWith("SUCCESS"))) {
            if (timeline != null) timeline.stop(); // Tắt bộ đếm 10 phút
            try {
                // Chuyển thẳng vào Trang chủ (home.fxml)
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/home.fxml"));
                Parent root = loader.load();

                Stage stage = (Stage) txtDigit1.getScene().getWindow();
                stage.setScene(new Scene(root, 1000, 750));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
                lblStatus.setText("Lỗi khi tải trang chủ!");
                lblStatus.setStyle("-fx-text-fill: #ef4444;");
            }
        } else {
            // Bắt chuỗi lỗi từ Server (ví dụ: REGISTER_FAIL|Mã OTP không đúng...)
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : "Kích hoạt thất bại!";
            lblStatus.setText(errorMsg);
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    // Xử lý gửi lại mã OTP
    @FXML
    private void handleResendOtp(ActionEvent event) {
        String request = "REQUEST_OTP|" + email;
        String response = Client_Socket.Client.getInstance().sendRequest(request);

        if (response != null && (response.startsWith("SUCCESS") || response.contains("OTP Sent"))) {
            lblStatus.setText("Đã gửi lại mã OTP mới!");
            lblStatus.setStyle("-fx-text-fill: #10b981;");
            btnActivate.setDisable(false);
            startTimer(); // Reset đếm lại 10 phút
        } else {
            lblStatus.setText("Gửi lại mã OTP thất bại!");
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    // Quay lại Đăng ký
    @FXML
    private void handleBackToRegister(ActionEvent event) {
        if (timeline != null) timeline.stop();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/register.fxml"));
            Stage stage = (Stage) txtDigit1.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}