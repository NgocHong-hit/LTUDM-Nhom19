package Client_GUI;

import javafx.application.Application; 
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Nạp file FXML Đăng ký
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        primaryStage.setTitle("Đăng ký tài khoản - Quiz Application");
        primaryStage.setScene(new Scene(root, 1000, 750));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}