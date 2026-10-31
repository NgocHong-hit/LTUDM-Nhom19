package Client_GUI.Controller;

import Client_Socket.Client;
import Model.User;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class Profile implements Initializable {

    @FXML private TextField txtFullName;
    @FXML private TextField txtEmail;
    @FXML private ComboBox<String> cbGender;
    @FXML private DatePicker dpDob;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmNewPassword;
    
    @FXML private Label lblMessage;
    @FXML private Button btnBack;
    @FXML private Button btnSave;

    private Client client;
    private User currentUser;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client = Client.getInstance();
        
        if (cbGender != null) {
            cbGender.getItems().setAll("Nam", "Nữ", "Khác");
        }

        // Lấy đối tượng User đang đăng nhập từ Model.User
        currentUser = User.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            txtEmail.setText(currentUser.getEmail());
            loadUserProfile();
        } else {
            lblMessage.setText("Không tìm thấy thông tin phiên đăng nhập!");
            lblMessage.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    private void loadUserProfile() {
        String request = "GET_PROFILE|" + currentUser.getEmail();
        String response = client.sendRequest(request);

        if (response != null && response.startsWith("GET_PROFILE_SUCCESS")) {
            String[] parts = response.split("\\|", -1);
            if (parts.length >= 4) {
                txtFullName.setText(parts[1]);
                cbGender.setValue(parts[2].isEmpty() ? "Nam" : parts[2]);
                if (!parts[3].isEmpty()) {
                    try {
                        dpDob.setValue(LocalDate.parse(parts[3]));
                    } catch (Exception e) {
                        dpDob.setValue(null);
                    }
                }
            }
        }
    }

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        String fullName = txtFullName.getText().trim();
        String gender = cbGender.getValue() != null ? cbGender.getValue() : "Nam";
        String dob = dpDob.getValue() != null ? dpDob.getValue().toString() : "";
        String newPass = txtNewPassword.getText().trim();
        String confirmPass = txtConfirmNewPassword.getText().trim();

        if (fullName.isEmpty()) {
            lblMessage.setText("Họ và tên không được để trống!");
            lblMessage.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        if (!newPass.isEmpty() && !newPass.equals(confirmPass)) {
            lblMessage.setText("Mật khẩu mới không khớp!");
            lblMessage.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        String request = String.format("UPDATE_PROFILE|%s|%s|%s|%s|%s", currentUser.getEmail(), fullName, gender, dob, newPass);
        String response = client.sendRequest(request);

        if (response != null && response.startsWith("UPDATE_PROFILE_SUCCESS")) {
            lblMessage.setText("Cập nhật thông tin thành công!");
            lblMessage.setStyle("-fx-text-fill: #10b981;");
            
            txtNewPassword.clear();
            txtConfirmNewPassword.clear();
            
            // Cập nhật lại họ tên vào User session hiện tại
            currentUser.setFullName(fullName);
        } else {
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : "Cập nhật thất bại!";
            lblMessage.setText(errorMsg);
            lblMessage.setStyle("-fx-text-fill: #ef4444;");
        }
    }

   @FXML
    private void handleBack(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/home.fxml"));
            Stage stage = (Stage) btnBack.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}