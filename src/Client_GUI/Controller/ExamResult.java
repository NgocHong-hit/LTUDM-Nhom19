package Client_GUI.Controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.net.URL;
import java.lang.reflect.Type;
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
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class ExamResult implements Initializable {

    @FXML private Label lblScore;
    @FXML private Label lblTimeInfo;

    @FXML private TableView<Model.ExamResult> tblLeaderboard;
    @FXML private TableColumn<Model.ExamResult, Integer> colRank;
    @FXML private TableColumn<Model.ExamResult, String> colUser;
    @FXML private TableColumn<Model.ExamResult, Double> colScore;
    @FXML private TableColumn<Model.ExamResult, Integer> colTime;
    @FXML private TableColumn<Model.ExamResult, String> colSubmittedAt;

    private final ObservableList<Model.ExamResult> leaderboardList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colRank.setCellValueFactory(new PropertyValueFactory<>("rank"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
        colScore.setCellValueFactory(new PropertyValueFactory<>("score"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("completionTimeSeconds"));
        colSubmittedAt.setCellValueFactory(new PropertyValueFactory<>("submittedAt"));

        tblLeaderboard.setItems(leaderboardList);
    }

    public void displayResult(JsonObject jsonResp, int elapsedSeconds) {
        double score = jsonResp.get("finalScore").getAsDouble();
        lblScore.setText(String.format("%.2f / 10.0", score));
        lblTimeInfo.setText("Thời gian làm bài: " + elapsedSeconds + " giây");

        Type listType = new TypeToken<List<Model.ExamResult>>(){}.getType();
        List<Model.ExamResult> results = new Gson().fromJson(jsonResp.get("leaderboard"), listType);

        leaderboardList.clear();
        if (results != null) {
            leaderboardList.addAll(results);
        }
    }

    @FXML
    private void handleReturnHome(ActionEvent event) {
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