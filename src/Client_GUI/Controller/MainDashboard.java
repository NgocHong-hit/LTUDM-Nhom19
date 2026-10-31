package Client_GUI.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import Model.Quiz;
import Model.User;
import Client_Socket.Client;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class MainDashboard {

    @FXML private Label lblUserName;
    @FXML private Label lblTotalQuizzes;
    @FXML private Label lblUserCompletedExams;
    @FXML private Label lblAvgScore;
    
    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh;
    @FXML private Button btnCreateQuiz;
    @FXML private Button btnLogout;

    @FXML private TableView<Quiz> tblQuizzes;
    @FXML private TableColumn<Quiz, Integer> colId;
    @FXML private TableColumn<Quiz, String> colTitle;
    @FXML private TableColumn<Quiz, String> colCreator;
    @FXML private TableColumn<Quiz, Integer> colDuration;
    @FXML private TableColumn<Quiz, Integer> colTotalQuestions;
    @FXML private TableColumn<?, ?> colAttemptCount;
    @FXML private TableColumn<?, ?> colAvgScore;
    @FXML private TableColumn<?, ?> colMaxScore;
    @FXML private TableColumn<Quiz, Void> colAction;
    @FXML private Button btnUser;

    

    private final ObservableList<Quiz> masterQuizList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colCreator.setCellValueFactory(new PropertyValueFactory<>("creatorName"));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        colTotalQuestions.setCellValueFactory(new PropertyValueFactory<>("totalQuestions"));
        
        // LINH KẾT CÁC CỘT THỐNG KÊ VÀO TABLEVIEW
        colAttemptCount.setCellValueFactory(new PropertyValueFactory<>("attemptCount"));
        colAvgScore.setCellValueFactory(new PropertyValueFactory<>("avgScore"));
        colMaxScore.setCellValueFactory(new PropertyValueFactory<>("maxScore"));

        // Cấu hình Nút bấm trên cột colAction
        colAction.setCellFactory(column -> new TableCell<Quiz, Void>() {
            private final Button takeButton = new Button("Làm bài");
            private final Button editButton = new Button("Sửa");
            private final HBox buttons = new HBox(4, takeButton, editButton);

            {
                buttons.setAlignment(Pos.CENTER);
                takeButton.getStyleClass().add("btn-table-take");
                editButton.getStyleClass().add("btn-table-edit");

                takeButton.setOnAction(event -> {
                    Quiz quiz = getTableView().getItems().get(getIndex());
                    openExamScreen(quiz.getId(), event);
                });

                editButton.setOnAction(event -> {
                    Quiz quiz = getTableView().getItems().get(getIndex());
                    onEditQuizClicked(quiz, event);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Quiz quiz = getTableView().getItems().get(getIndex());
                    User currentUser = User.getCurrentUser();
                    boolean isOwner = currentUser != null && quiz != null && quiz.getCreatorEmail() != null
                            && quiz.getCreatorEmail().equalsIgnoreCase(currentUser.getEmail());
                    editButton.setVisible(isOwner);
                    editButton.setManaged(isOwner);
                    setText(null);
                    setGraphic(buttons);
                }
            }
        });

        tblQuizzes.setItems(masterQuizList);
        loadQuizList();
        loadUserOverviewStats();
    }

    // Nạp các con số thống kê tổng quan ở 3 thẻ trên cùng
    private void loadUserOverviewStats() {
        User currentUser = User.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            String response = Client.getInstance().sendRequest("GET_USER_STATS|" + currentUser.getEmail());
            if (response != null && response.startsWith("GET_USER_STATS_SUCCESS")) {
                String[] parts = response.split("\\|");
                if (parts.length >= 3) {
                    if (lblUserCompletedExams != null) lblUserCompletedExams.setText(parts[1]);
                    if (lblAvgScore != null) lblAvgScore.setText(parts[2] + " / 10");
                }
            }
        }
    }

    //  Xử lý chỉnh sửa bộ đề được bấm từ dòng trong bảng
    private void onEditQuizClicked(Quiz selectedQuiz, ActionEvent event) {
        if (selectedQuiz == null) {
            showAlert("Vui lòng chọn 1 bộ đề thi từ bảng để sửa!");
            return;
        }

        User currentUser = User.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) return;

        // Bổ sung kiểm tra quyền sở hữu: Chỉ người tạo mới được sửa đề của chính mình
        if (selectedQuiz.getCreatorEmail() != null && !selectedQuiz.getCreatorEmail().isEmpty()
                && !selectedQuiz.getCreatorEmail().equalsIgnoreCase(currentUser.getEmail())) {
            showAlert("Bạn chỉ có thể chỉnh sửa bộ đề do chính bạn tạo ra!");
            return;
        }

        // Gửi request kiểm tra xem bộ đề đã có ai thi chưa
        String request = "CHECK_CAN_EDIT_QUIZ|" + selectedQuiz.getId() + "|" + currentUser.getEmail();
        String response = Client.getInstance().sendRequest(request);

        if (response != null && response.startsWith("CHECK_CAN_EDIT_SUCCESS")) {
            String jsonQuiz = response.split("\\|", 2)[1];
            Gson gson = new Gson();
            Quiz quizToEdit = gson.fromJson(jsonQuiz, Quiz.class);

            // Chuyển sang màn hình soạn bài thi và nạp dữ liệu cũ lên
            openCreateQuizScreenForEdit(quizToEdit, event);
        } else {
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : "Bộ đề đã có lượt thi hoặc không có quyền sửa!";
            showAlert(errorMsg);
        }
    }

    @FXML
    private void goToCreateQuiz(ActionEvent event) {
        openCreateQuiz(event);
    }

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

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadQuizList();
    }

    @FXML
    public void openProfile(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/profile.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void openCreateQuiz(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/create_quiz.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1050, 760));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadQuizList() {
        String response = Client.getInstance().sendRequest("GET_ALL_QUIZZES");

        if (response == null || !response.startsWith("GET_ALL_QUIZZES_SUCCESS|")) {
            System.err.println("Lỗi tải danh sách bộ đề: " + response);
            return;
        }

        String[] parts = response.split("\\|", 2);
        if (parts.length < 2) return;

        Gson gson = new Gson();
        Type listType = new TypeToken<List<Quiz>>(){}.getType();
        List<Quiz> quizzes = gson.fromJson(parts[1], listType);

        masterQuizList.clear();
        if (quizzes != null) {
            masterQuizList.addAll(quizzes);
        }
        if (lblTotalQuizzes != null) {
            lblTotalQuizzes.setText(String.valueOf(masterQuizList.size()));
        }
    }

    private void openExamScreen(int quizId, ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/exam.fxml"));
            Parent root = loader.load();

            TakeExam controller = loader.getController();
            controller.startExamWithQuizId(quizId);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 960, 700));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openCreateQuizScreenForEdit(Quiz quizToEdit, ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_quiz.fxml"));
            Parent root = loader.load();

            ExamManagement controller = loader.getController();
            try {
                controller.getClass()
                        .getMethod("loadQuizDataForEdit", Quiz.class)
                        .invoke(controller, quizToEdit);
            } catch (NoSuchMethodException e) {
                System.err.println("ExamManagement chưa có hàm loadQuizDataForEdit(Quiz).");
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1050, 760));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    
}