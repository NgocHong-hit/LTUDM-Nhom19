package Client_GUI.Controller;

import Client_Socket.Client;
import Model.Option;
import Model.Question;
import Model.Quiz;
import Model.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.ByteArrayInputStream;
import java.util.Base64;

public class TakeExam implements Initializable {

    @FXML private Label lblQuizTitle;
    @FXML private Label lblQuestionProgress;
    @FXML private Label lblTimer;

    @FXML private Label lblQuestionContent;
    @FXML private ImageView imgQuestion;

    @FXML private ToggleGroup tgOptions;
    @FXML private RadioButton rbOptionA, rbOptionB, rbOptionC, rbOptionD;

    @FXML private Label lblFeedback;
    @FXML private Button btnSubmitAnswer;
    @FXML private Button btnNextQuestion;

    private Quiz currentQuiz;
    private int sessionId;
    private int currentQuestionIndex = 0;

    private int totalTimeSeconds;
    private int elapsedSeconds = 0;
    private Timeline timerTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Kích hoạt nút "Xác nhận câu này" ngay khi người dùng tích chọn 1 RadioButton
        tgOptions.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                btnSubmitAnswer.setDisable(false);
            }
        });
    }

    // Nhận ID bộ đề từ MainDashboard chuyển sang
    public void startExamWithQuizId(int quizId) {
        User user = User.getCurrentUser();
        if (user == null) return;

        String request = "START_EXAM|" + quizId + "|" + user.getEmail();
        String response = Client.getInstance().sendRequest(request);

        if (response != null && response.startsWith("START_EXAM_SUCCESS")) {
            String jsonStr = response.split("\\|", 2)[1];
            JsonObject jsonObj = JsonParser.parseString(jsonStr).getAsJsonObject();

            this.sessionId = jsonObj.get("sessionId").getAsInt();
            this.currentQuiz = new Gson().fromJson(jsonObj.get("quiz"), Quiz.class);

            // Thiết lập giao diện ban đầu
            lblQuizTitle.setText(currentQuiz.getTitle());
            totalTimeSeconds = currentQuiz.getDurationMinutes() * 60;

            startTimer();
            displayQuestion(0);
        } else {
            showErrorAlert("Không thể bắt đầu thi: " + response);
        }
    }

    // Đếm ngược thời gian làm bài thi
    private void startTimer() {
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds++;
            int remaining = totalTimeSeconds - elapsedSeconds;

            if (remaining <= 0) {
                lblTimer.setText("Thời gian còn: 00:00");
                timerTimeline.stop();
                autoSubmitAndFinish("Hết giờ làm bài! Hệ thống tự động nộp bài.");
            } else {
                int mins = remaining / 60;
                int secs = remaining % 60;
                lblTimer.setText(String.format("Thời gian còn: %02d:%02d", mins, secs));
            }
        }));
        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();
    }

    // Hiển thị câu hỏi
    private void displayQuestion(int index) {
        if (currentQuiz == null || currentQuiz.getQuestions().isEmpty()) return;

        Question q = currentQuiz.getQuestions().get(index);
        lblQuestionProgress.setText(String.format("Câu hỏi %d / %d", index + 1, currentQuiz.getQuestions().size()));
        lblQuestionContent.setText(q.getContent());

        // Xử lý nạp hình ảnh
        String imgPath = q.getImageUrl();
        imgQuestion.setImage(null);
        if (imgPath != null && !imgPath.trim().isEmpty()) {
            try {
                Image image;
                if (imgPath.startsWith("data:image/")) {
                    int separator = imgPath.indexOf(',');
                    if (separator < 0) {
                        throw new IllegalArgumentException("Data URI ảnh không hợp lệ");
                    }
                    byte[] imageBytes = Base64.getDecoder().decode(imgPath.substring(separator + 1));
                    image = new Image(new ByteArrayInputStream(imageBytes));
                } else {
                    File imgFile = new File(imgPath);
                    if (!imgFile.isFile()) {
                        imgFile = new File(System.getProperty("user.dir"), imgPath);
                    }
                    if (!imgFile.isFile()) {
                        imgFile = new File("image", new File(imgPath).getName());
                    }
                    if (!imgFile.isFile()) {
                        throw new java.io.IOException("Không tìm thấy tệp ảnh: " + imgPath);
                    }
                    image = new Image(imgFile.toURI().toString());
                }

                if (image.isError()) {
                    throw new java.io.IOException("JavaFX không đọc được ảnh: " + imgPath);
                }
                imgQuestion.setImage(image);
                imgQuestion.setVisible(true);
                imgQuestion.setManaged(true);
            } catch (Exception e) {
                System.err.println("Lỗi nạp ảnh câu hỏi: " + e.getMessage());
                imgQuestion.setVisible(false);
                imgQuestion.setManaged(false);
            }
        } else {
            imgQuestion.setVisible(false);
            imgQuestion.setManaged(false);
        }

        // Đặt lại các RadioButton và nút bấm
        tgOptions.selectToggle(null);
        List<Option> opts = q.getOptions();
        RadioButton[] rbs = {rbOptionA, rbOptionB, rbOptionC, rbOptionD};

        for (int i = 0; i < 4; i++) {
            if (i < opts.size()) {
                rbs[i].setText(opts.get(i).getContent());
                rbs[i].setUserData(opts.get(i).getId());
                rbs[i].setVisible(true);
                rbs[i].setDisable(false);
            } else {
                rbs[i].setVisible(false);
            }
        }

        lblFeedback.setText("");
        btnSubmitAnswer.setVisible(true);
        btnSubmitAnswer.setDisable(true);
        btnNextQuestion.setDisable(true);

        if (index == currentQuiz.getQuestions().size() - 1) {
            btnNextQuestion.setText("Nộp bài thi");
        } else {
            btnNextQuestion.setText("Câu tiếp theo →");
        }
    }

        // Nút "Xác nhận câu này": Gửi lựa chọn lên Server chấm ngay
    @FXML
    private void handleSubmitAnswer(ActionEvent event) {
        Toggle selected = tgOptions.getSelectedToggle();
        if (selected == null) return;

        int selectedOptId = (int) selected.getUserData();
        Question currentQ = currentQuiz.getQuestions().get(currentQuestionIndex);

        String request = String.format("SUBMIT_ANSWER|%d|%d|%d", sessionId, currentQ.getId(), selectedOptId);
        String response = Client.getInstance().sendRequest(request);

        if (response != null && response.startsWith("SUBMIT_ANSWER_SUCCESS")) {
            boolean isCorrect = Boolean.parseBoolean(response.split("\\|")[1]);

            // Khóa không cho chọn lại câu này
            rbOptionA.setDisable(true);
            rbOptionB.setDisable(true);
            rbOptionC.setDisable(true);
            rbOptionD.setDisable(true);

            // Phản hồi Đúng / Sai ngay lập tức
            if (isCorrect) {
                lblFeedback.setText("Chính xác!");
                lblFeedback.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 15px;");
            } else {
                lblFeedback.setText("Rất tiếc, đáp án chưa đúng!");
                lblFeedback.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 15px;");
            }

            btnSubmitAnswer.setVisible(false);
            btnNextQuestion.setDisable(false);
        }
    }

    // Nút "Câu tiếp theo" / "Nộp bài thi"
    @FXML
    private void handleNextQuestion(ActionEvent event) {
        if (currentQuestionIndex < currentQuiz.getQuestions().size() - 1) {
            currentQuestionIndex++;
            displayQuestion(currentQuestionIndex);
        } else {
            // Đã hết câu hỏi -> Nộp bài
            if (timerTimeline != null) timerTimeline.stop();
            finishExam();
        }
    }

    // Gọi Server tính tổng điểm và hoàn tất bài thi
    private void finishExam() {
        String request = String.format("FINISH_EXAM|%d|%d", sessionId, elapsedSeconds);
        String response = Client.getInstance().sendRequest(request);

        if (response != null && response.startsWith("FINISH_EXAM_SUCCESS")) {
            String jsonStr = response.split("\\|", 2)[1];
            JsonObject jsonResp = JsonParser.parseString(jsonStr).getAsJsonObject();

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/result.fxml"));
                Parent root = loader.load();

                ExamResult controller = loader.getController();
                controller.displayResult(jsonResp, elapsedSeconds);

                Stage stage = (Stage) lblQuizTitle.getScene().getWindow();
                stage.setScene(new Scene(root, 960, 700));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void autoSubmitAndFinish(String reasonMsg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("HẾT GIỜ");
        alert.setHeaderText(null);
        alert.setContentText(reasonMsg);
        alert.showAndWait();

        finishExam();
    }

    private void returnToHome() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/home.fxml"));
            Stage stage = (Stage) lblQuizTitle.getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showErrorAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(msg);
        alert.showAndWait();
        returnToHome();
    }
}