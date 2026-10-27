package Client_GUI.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class MainDashboard {

    @FXML private Label lblUserName;
    @FXML private Label lblTotalQuizzes;
    @FXML private Label lblUserCompletedExams;
    @FXML private Label lblAvgScore;
    
    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh;
    @FXML private Button btnCreateQuiz;
    @FXML private Button btnLogout;

    @FXML private TableView<?> tblQuizzes;
    @FXML private TableColumn<?, ?> colId;
    @FXML private TableColumn<?, ?> colTitle;
    @FXML private TableColumn<?, ?> colCreator;
    @FXML private TableColumn<?, ?> colDuration;
    @FXML private TableColumn<?, ?> colTotalQuestions;
    @FXML private TableColumn<?, ?> colAttemptCount;
    @FXML private TableColumn<?, ?> colAvgScore;
    @FXML private TableColumn<?, ?> colMaxScore;
    @FXML private TableColumn<?, ?> colAction;

    @FXML
    public void initialize() {
        // Tự động chạy khi nạp xong giao diện home.fxml
        if (lblUserName != null) {
            lblUserName.setText("Thí sinh");
        }
    }

    // Xử lý nút "+ Tạo bộ đề mới" (onAction="#goToCreateQuiz")
    @FXML
    private void goToCreateQuiz(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/create_quiz.fxml"));
            Stage stage = (Stage) btnCreateQuiz.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Xử lý nút "Đăng xuất" (onAction="#handleLogout")
    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage stage = (Stage) btnLogout.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Xử lý nút "Làm mới" (onAction="#handleRefresh")
    @FXML
    private void handleRefresh(ActionEvent event) {
        System.out.println("Đang làm mới danh sách bộ đề...");
    }
}