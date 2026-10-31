package Client_GUI.Controller;

import Client_Socket.Client;
import Model.Option;
import Model.Question;
import Model.Quiz;
import Model.User;
import com.google.gson.Gson;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ExamManagement implements Initializable {

    @FXML private Label lblFormTitle;
    @FXML private TextField txtQuizTitle;
    @FXML private Spinner<Integer> spnDuration;
    @FXML private ComboBox<String> cmbSuggestedQuestions;
    @FXML private Label lblScoreInfo;
    @FXML private TextArea txtQuizDescription;

    @FXML private Label lblQuestionIndex;
    @FXML private TextArea txtQuestionContent;
    @FXML private TextField txtImageUrl;

    @FXML private ToggleGroup correctAnswerGroup;
    @FXML private RadioButton rbOptionA, rbOptionB, rbOptionC, rbOptionD;
    @FXML private TextField txtOptionA, txtOptionB, txtOptionC, txtOptionD;

    @FXML private ListView<Question> listQuestions;
    @FXML private Label lblQuestionCount;
    @FXML private Label lblStatus;
    @FXML private Button btnAddQuestion;
    @FXML private Button btnSaveQuiz;

    private ObservableList<Question> questionList = FXCollections.observableArrayList();
    private int editingIndex = -1; // -1: Thêm mới, >=0: Đang sửa câu hỏi trong danh sách
    private int currentQuizId = -1; // -1: Tạo bộ đề mới, >0: Đang chỉnh sửa bộ đề cũ

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cấu hình Spinner Thời gian (5 -> 180 phút)
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 180, 15, 5);
        spnDuration.setValueFactory(valueFactory);

        // Gợi ý số câu hỏi
        cmbSuggestedQuestions.getItems().addAll("5 câu", "10 câu", "15 câu", "20 câu", "30 câu", "40 câu", "50 câu");
        cmbSuggestedQuestions.setValue("10 câu");

        // Gắn danh sách câu hỏi vào ListView
        listQuestions.setItems(questionList);

        // click vào câu hỏi trong bảng để sửa
        listQuestions.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.intValue() >= 0) {
                populateQuestionToForm(newValue.intValue());
            }
        });

        updateScoreInfo();
    }

    public void loadQuizDataForEdit(Quiz quiz) {
        if (quiz == null) return;
        
        this.currentQuizId = quiz.getId();
        
        if (lblFormTitle != null) {
            lblFormTitle.setText("Chỉnh sửa bộ đề thi");
        }
        if (btnSaveQuiz != null) {
            btnSaveQuiz.setText("Cập nhật bộ đề thi");
        }

        txtQuizTitle.setText(quiz.getTitle() != null ? quiz.getTitle() : "");
        if (spnDuration != null && spnDuration.getValueFactory() != null) {
            spnDuration.getValueFactory().setValue(quiz.getDurationMinutes());
        }
        txtQuizDescription.setText(quiz.getDescription() != null ? quiz.getDescription() : "");

        // Đổ danh sách câu hỏi
        questionList.clear();
        if (quiz.getQuestions() != null && !quiz.getQuestions().isEmpty()) {
            questionList.addAll(quiz.getQuestions());
        }

        // Làm mới bảng
        if (listQuestions != null) {
            listQuestions.setItems(questionList);
            listQuestions.refresh();
        }

        updateScoreInfo();
    }

    // Đổ thông tin 1 câu hỏi lên các ô nhập liệu bên trái
    private void populateQuestionToForm(int index) {
        if (index < 0 || index >= questionList.size()) return;

        Question q = questionList.get(index);
        editingIndex = index;

        txtQuestionContent.setText(q.getContent() != null ? q.getContent() : "");
        txtImageUrl.setText(q.getImageUrl() != null ? q.getImageUrl() : "");

        List<Option> opts = q.getOptions();
        if (opts != null && opts.size() >= 4) {
            txtOptionA.setText(opts.get(0).getContent());
            rbOptionA.setSelected(opts.get(0).isCorrect());

            txtOptionB.setText(opts.get(1).getContent());
            rbOptionB.setSelected(opts.get(1).isCorrect());

            txtOptionC.setText(opts.get(2).getContent());
            rbOptionC.setSelected(opts.get(2).isCorrect());

            txtOptionD.setText(opts.get(3).getContent());
            rbOptionD.setSelected(opts.get(3).isCorrect());
        }

        btnAddQuestion.setText("Cập nhật câu hỏi");
        lblQuestionIndex.setText("Đang sửa câu hỏi thứ " + (index + 1));
    }

    // Cập nhật Quy chế & số lượng câu
    private void updateScoreInfo() {
        int count = questionList.size();
        if (lblQuestionCount != null) {
            lblQuestionCount.setText("(" + count + " câu)");
        }
        if (lblScoreInfo != null) {
            if (count == 0) {
                lblScoreInfo.setText("Tổng 10 điểm (Chưa có câu hỏi)");
            } else {
                double scorePerQ = 10.0 / count;
                lblScoreInfo.setText(String.format("10 điểm (~%.2f đ/câu)", scorePerQ));
            }
        }
    }

    // Chọn ảnh đính kèm từ máy tính
    @FXML
    private void handleBrowseImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh minh họa câu hỏi");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = fileChooser.showOpenDialog(txtImageUrl.getScene().getWindow());
        if (file != null) {
            txtImageUrl.setText(file.getAbsolutePath());
        }
    }

    // Nút Thêm / Cập nhật câu hỏi vào danh sách tạm
    @FXML
    private void handleAddQuestion(ActionEvent event) {
        String content = txtQuestionContent.getText().trim();
        String optA = txtOptionA.getText().trim();
        String optB = txtOptionB.getText().trim();
        String optC = txtOptionC.getText().trim();
        String optD = txtOptionD.getText().trim();

        if (content.isEmpty() || optA.isEmpty() || optB.isEmpty() || optC.isEmpty() || optD.isEmpty()) {
            lblStatus.setText("Vui lòng nhập đề bài và đủ 4 đáp án!");
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        List<Option> options = new ArrayList<>();
        options.add(new Option(optA, rbOptionA.isSelected()));
        options.add(new Option(optB, rbOptionB.isSelected()));
        options.add(new Option(optC, rbOptionC.isSelected()));
        options.add(new Option(optD, rbOptionD.isSelected()));

        Question q = new Question(content, txtImageUrl.getText().trim(), options);

        if (editingIndex == -1) {
            questionList.add(q);
        } else {
            questionList.set(editingIndex, q);
            editingIndex = -1;
            btnAddQuestion.setText("+ Thêm câu hỏi");
        }

        handleClearQuestionForm(null);
        updateScoreInfo();
        lblStatus.setText("Đã lưu câu hỏi vào danh sách!");
        lblStatus.setStyle("-fx-text-fill: #10b981;");
    }

    // Làm sạch ô nhập câu hỏi
    @FXML
    private void handleClearQuestionForm(ActionEvent event) {
        txtQuestionContent.clear();
        txtImageUrl.clear();
        txtOptionA.clear();
        txtOptionB.clear();
        txtOptionC.clear();
        txtOptionD.clear();
        rbOptionA.setSelected(true);
        editingIndex = -1;
        listQuestions.getSelectionModel().clearSelection();
        btnAddQuestion.setText("+ Thêm câu hỏi");
        lblQuestionIndex.setText("Soạn nội dung câu hỏi");
    }

    // Nút "Chỉnh sửa" dưới ListView
    @FXML
    private void handleEditSelected(ActionEvent event) {
        int index = listQuestions.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            lblStatus.setText("Vui lòng chọn 1 câu từ danh sách để sửa!");
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }
        populateQuestionToForm(index);
    }

    // Nút "Xóa câu"
    @FXML
    private void handleDeleteSelected(ActionEvent event) {
        int index = listQuestions.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            questionList.remove(index);
            handleClearQuestionForm(null);
            updateScoreInfo();
        }
    }

    // Lưu / Cập nhật bộ đề thi sang Server bằng JSON
    @FXML
    private void handleSaveQuiz(ActionEvent event) {
        String title = txtQuizTitle.getText().trim();
        int duration = spnDuration.getValue();
        String description = txtQuizDescription.getText().trim();

        if (title.isEmpty()) {
            lblStatus.setText("Tên bộ đề không được để trống!");
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        if (questionList.isEmpty()) {
            lblStatus.setText("Bộ đề thi phải có ít nhất 1 câu hỏi!");
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        User currentUser = User.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            lblStatus.setText("Phiên đăng nhập hết hạn!");
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
            return;
        }

        // Mã hóa file ảnh của từng câu (nếu có chọn ảnh local mới)
        for (Question q : questionList) {
            if (q.getImageUrl() != null && !q.getImageUrl().trim().isEmpty()) {
                File file = new File(q.getImageUrl());
                if (file.exists()) {
                    q.setImageUrl(file.getName());
                    q.setImageUrl(common.FileUtil.convertFileToBase64(file));
                }
            }
        }

        Quiz quiz = new Quiz();
        quiz.setId(currentQuizId); // -1 nếu tạo mới, >0 nếu đang cập nhật bộ đề cũ
        quiz.setTitle(title);
        quiz.setDurationMinutes(duration);
        quiz.setDescription(description);
        quiz.setCreatorEmail(currentUser.getEmail());
        quiz.setQuestions(new ArrayList<>(questionList));

        Gson gson = new Gson();
        String jsonQuiz = gson.toJson(quiz);

        String command = (currentQuizId > 0) ? "UPDATE_QUIZ" : "CREATE_QUIZ";
        String request = command + "|" + jsonQuiz;

        String response = Client.getInstance().sendRequest(request);

        if (response != null && (response.startsWith("CREATE_QUIZ_SUCCESS") || response.startsWith("UPDATE_QUIZ_SUCCESS"))) {
            lblStatus.setText(currentQuizId > 0 ? "Cập nhật bộ đề thi thành công!" : "Tạo bộ đề thi thành công!");
            lblStatus.setStyle("-fx-text-fill: #10b981;");
            handleBack(event);
        } else {
            String errorMsg = (response != null && response.contains("|")) ? response.split("\\|")[1] : "Lưu bộ đề thất bại!";
            lblStatus.setText(errorMsg);
            lblStatus.setStyle("-fx-text-fill: #ef4444;");
        }
    }

    // Quay lại Trang chủ
    @FXML
    private void handleBack(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/home.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1000, 750));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}