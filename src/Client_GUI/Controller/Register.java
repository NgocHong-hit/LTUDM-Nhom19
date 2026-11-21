package Client_GUI.Controller;

import Client_Socket.Client;
import common.ValidationUtil;
import java.time.LocalDate;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class Register {
    
    @FXML private TextField txtFullName;
    @FXML private ComboBox<String> cbGender;
    @FXML private TextField txtEmail;
    @FXML private DatePicker dpDob;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmPassword;
    
    @FXML private Label lblMessage;
    @FXML private Button btnRegister;

    private Client client = Client.getInstance();

    @FXML
    public void initialize() {
        if (client == null) {
            client = Client.getInstance();
        }
        dpDob.setEditable(false);
        dpDob.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item.isAfter(LocalDate.now()));
            }
        });
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

        if (!ValidationUtil.isValidEmail(email)) {
            showMessage("Email không đúng định dạng. Ví dụ: ten@example.com", true);
            return;
        }

        if (dob.isAfter(LocalDate.now())) {
            showMessage("Ngày sinh không được sau ngày hiện tại!", true);
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

        String response = client.sendRequest("REQUEST_OTP|" + email);

        if (response != null && response.startsWith("SEND_OTP_SUCCESS")) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/verify_otp.fxml"));
                Parent root = loader.load();

                OtpVerify controller = loader.getController();
                controller.setRegistrationData(fullName, gender, email, dob.toString(), password);

                Stage stage = (Stage) btnRegister.getScene().getWindow();
                stage.setScene(new Scene(root, 1000, 750));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
                showMessage("Lỗi khi tải giao diện xác thực OTP!", true);
            }
        } else {
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : response;
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