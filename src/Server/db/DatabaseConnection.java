package Server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DatabaseConnection {

    private static final Properties config = new Properties();

    private static String URL;
    private static String USER;
    private static String PASSWORD;

    static {
        try (InputStream input = new FileInputStream("config.properties")) {
            config.load(input);

            URL = config.getProperty("URL");
            USER = config.getProperty("USER");
            PASSWORD = config.getProperty("PASSWORD");

            if (URL == null || USER == null || PASSWORD == null) {
                throw new IllegalStateException(
                    "Thiếu URL, USER hoặc PASSWORD trong config.properties"
                );
            }

        } catch (IOException e) {
            throw new ExceptionInInitializerError(
                "Không thể đọc file config.properties: " + e.getMessage()
            );
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                "Chưa thêm MySQL JDBC Driver vào thư viện dự án."
            );
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

}

