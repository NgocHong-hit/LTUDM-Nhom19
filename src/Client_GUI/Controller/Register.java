package Client_GUI.Controller;

import Client_Socket.Client;
import java.time.LocalDate;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Register {
    
    @FXML private TextField txtFullName;
    @FXML private ComboBox<String> cbGender;
    @FXML private TextField txtEmail;
    @FXML private DatePicker dpDob;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmPassword;
    
    @FXML private VBox otpBox;
    @FXML private TextField txtOtp;
    @FXML private Label lblMessage;
    @FXML private Button btnRegister;

    private Client client = Client.getInstance();

    @FXML
    public void initialize() {
        if (client == null) {
            client = Client.getInstance();
        }
    }


    @FXML 
    private void handleRegister(ActionEvent event){
        String fullName = txtFullName.getText().trim();
        String gender = cbGender.getValue();
        String email = txtEmail.getText().trim();
        LocalDate dob = dpDob.getValue();
        String password = txtPassword.getText();
        String confirmPassword = txtConfirmPassword.getText();

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || gender == null || dob == null) {
            showMessage("Vui lòng điền đầy đủ tất cả thông tin!", true);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showMessage("Mật khẩu xác nhận không trùng khớp!", true);
            return;
        }

        if (password.length() < 6) {
            showMessage("Mật khẩu phải chứa tối thiểu 6 ký tự!", true);
            return;
        }

        // Trong Register.java - tại hàm xử lý nút Đăng ký tài khoản:
        String response = client.sendRequest("REQUEST_OTP|" + email);

        // Sửa điều kiện: Chấp nhận nếu phản hồi bắt đầu bằng "SUCCESS" HOẶC chứa "OTP Sent"
        if (response != null && (response.startsWith("SUCCESS") || response.contains("OTP Sent"))) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/verify_otp.fxml"));
                Parent root = loader.load();

                // Truyền dữ liệu sang OtpVerify Controller
                OtpVerify controller = loader.getController();
                controller.setRegisterData(fullName, gender, email, dob.toString(), password);

                // Chuyển Scene trên cửa sổ hiện tại
                Stage stage = (Stage) btnRegister.getScene().getWindow();
                stage.setScene(new Scene(root, 1000, 750));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
                showMessage("Lỗi khi tải giao diện xác thực OTP!", true);
            }
        } else {
            // Nếu thất bại thật sự thì mới báo lỗi ra giao diện
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : response;
            showMessage(errorMsg, true);
        }
    }

    @FXML 
        private void handleVerifyOtp(ActionEvent event) {
        String otp = txtOtp.getText().trim();
        if (otp.isEmpty() || otp.length() != 6) {
            showMessage("Vui lòng nhập đủ 6 chữ số OTP!", true);
            return;
        }

        String fullName = txtFullName.getText().trim();
        String gender = cbGender.getValue();
        String email = txtEmail.getText().trim();
        String dob = dpDob.getValue().toString();
        String password = txtPassword.getText();

        String request = String.format("REGISTER|%s|%s|%s|%s|%s|%s", fullName, gender, email, dob, password, otp);
        String response = client.sendRequest(request);

        if (response != null && response.startsWith("REGISTER_SUCCESS")) {
            showMessage("Đăng ký tài khoản thành công!", false);
        } else {
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : "Mã OTP không đúng hoặc hết hạn!";
            showMessage(errorMsg, true);
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            // Nạp file FXML giao diện đăng nhập
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            // Lấy Stage hiện tại từ sự kiện Click
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi khi chuyển sang màn hình Đăng nhập: " + e.getMessage());
        }
    }

    private void showMessage(String text, boolean isError) {
        lblMessage.setText(text);
        if (isError) {
            lblMessage.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        } else {
            lblMessage.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        }
    }
}