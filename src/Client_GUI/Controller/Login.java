package Client_GUI.Controller;

import java.util.ResourceBundle;

import Client_Socket.Client;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;


    public class Login {

        @FXML private TextField txtEmail;
        @FXML private PasswordField txtPassword;
        @FXML private Label lblMessage;
        @FXML private Button btnLogin;

    //     @Override 
    // public void initialize(URL location, ResourceBundle resources) {
    //     client = new Client("localhost", 12345); // <--- LỖI: Khởi tạo mới một Socket Client
    //     new Thread(() -> client.connectToServer()).start(); // <--- LỖI: Tạo thêm một kết nối mới tới Server
    // }

    // Xử lý nút Đăng nhập hoặc nhấn Enter
    @FXML
    private void handleLogin(ActionEvent event) {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText().trim();

        if (email.isEmpty() || password.isEmpty()) {
            lblMessage.setText("Vui lòng nhập đầy đủ Email và Mật khẩu!");
            lblMessage.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        btnLogin.setDisable(true);
        lblMessage.setText("Đang đăng nhập...");
        lblMessage.setStyle("-fx-text-fill: #64748b;");

        String request = String.format("LOGIN|%s|%s", email, password);
        Thread loginThread = new Thread(() -> {
            String response = Client.getInstance().sendRequest(request);
            Platform.runLater(() -> {
                btnLogin.setDisable(false);
                handleLoginResponse(email, response);
            });
        }, "login-request");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private void handleLoginResponse(String email, String response) {
        if (response != null && response.startsWith("LOGIN_SUCCESS")) {
            try {
                String userName = response.contains("|") ? response.split("\\|")[1] : "Thí sinh";

                // --- LƯU SESSION ĐĂNG NHẬP ---
                Model.User loggedInUser = new Model.User();
                loggedInUser.setEmail(email);
                loggedInUser.setFullName(userName);
                Model.User.setCurrentUser(loggedInUser); // Lưu vào static session

                // Nạp giao diện home.fxml
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/home.fxml"));
                Parent root = loader.load();

                Stage stage = (Stage) btnLogin.getScene().getWindow();
                stage.setScene(new Scene(root, 1000, 750));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
                lblMessage.setText("Lỗi khi chuyển sang màn hình Trang chủ!");
                lblMessage.setStyle("-fx-text-fill: #ef4444;");
            }

        }else {
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : "Đăng nhập thất bại!";
            lblMessage.setText(errorMsg);
            lblMessage.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    // Nút "Đăng ký ngay"
    @FXML
    private void goToRegister(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/register.fxml"));
            Stage stage = (Stage) txtEmail.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}