package Server.db;
import Server.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import Model.User;

import java.sql.*;
import java.util.Locale;
 public class UserDAO{

    public boolean isEmail(String email){
        String sql = "SELECT id FROM users WHERE email = ? AND is_verified = TRUE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean saveOTP(String email, String otp) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                replaceOTP(conn, email, otp);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void replaceOTP(Connection conn, String email, String otp) throws SQLException {
        String invalidateSql = "UPDATE otp_verifications SET is_used = TRUE "
                + "WHERE email = ? AND is_used = FALSE";
        try (PreparedStatement ps = conn.prepareStatement(invalidateSql)) {
            ps.setString(1, email);
            ps.executeUpdate();
        }

        String insertSql = "INSERT INTO otp_verifications (email, otp_code, expired_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, email);
            ps.setString(2, otp);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis() + (10 * 60 * 1000)));
            ps.executeUpdate();
        }
    }

    public boolean verifyOTPAndRegister(String fullName, String gender, String email,
                                        String dob, String password, String inputOTP) {
        String otpSql = "SELECT id FROM otp_verifications WHERE email = ? AND otp_code = ? "
                + "AND is_used = FALSE AND expired_at > CURRENT_TIMESTAMP "
                + "ORDER BY created_at DESC LIMIT 1 FOR UPDATE";
        String userSql = "INSERT INTO users "
                + "(full_name, gender, email, dob, password_hash, is_verified) "
                + "VALUES (?, ?, ?, ?, ?, TRUE)";
        String promoteSql = "UPDATE users SET full_name = ?, gender = ?, dob = ?, "
                + "password_hash = ?, is_verified = TRUE WHERE email = ? AND is_verified = FALSE";
        String existingUserSql = "SELECT is_verified FROM users WHERE email = ? FOR UPDATE";
        String usedSql = "UPDATE otp_verifications SET is_used = TRUE "
                + "WHERE id = ? AND is_used = FALSE AND expired_at > CURRENT_TIMESTAMP";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int otpId;
                try (PreparedStatement otpPs = conn.prepareStatement(otpSql)) {
                    otpPs.setString(1, email);
                    otpPs.setString(2, inputOTP);
                    try (ResultSet otpRs = otpPs.executeQuery()) {
                        if (!otpRs.next()) {
                            conn.rollback();
                            return false;
                        }
                        otpId = otpRs.getInt("id");
                    }
                }

                try (PreparedStatement usedPs = conn.prepareStatement(usedSql)) {
                    usedPs.setInt(1, otpId);
                    if (usedPs.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }

                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
                boolean existingUnverifiedUser = false;
                try (PreparedStatement existingPs = conn.prepareStatement(existingUserSql)) {
                    existingPs.setString(1, email);
                    try (ResultSet existingRs = existingPs.executeQuery()) {
                        if (existingRs.next()) {
                            if (existingRs.getBoolean("is_verified")) {
                                conn.rollback();
                                return false;
                            }
                            existingUnverifiedUser = true;
                        }
                    }
                }

                if (existingUnverifiedUser) {
                    try (PreparedStatement promotePs = conn.prepareStatement(promoteSql)) {
                        promotePs.setString(1, fullName);
                        promotePs.setString(2, gender);
                        promotePs.setDate(3, Date.valueOf(dob));
                        promotePs.setString(4, hashedPassword);
                        promotePs.setString(5, email);
                        if (promotePs.executeUpdate() != 1) {
                            conn.rollback();
                            return false;
                        }
                    }
                } else {
                    try (PreparedStatement userPs = conn.prepareStatement(userSql)) {
                        userPs.setString(1, fullName);
                        userPs.setString(2, gender);
                        userPs.setString(3, email);
                        userPs.setDate(4, Date.valueOf(dob));
                        userPs.setString(5, hashedPassword);
                        userPs.executeUpdate();
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean checkPassword(String rawPassword, String hashedPasswordFromDB) {
        // So sánh mật khẩu người dùng nhập vào với chuỗi hash BCrypt trong DB
        return BCrypt.checkpw(rawPassword, hashedPasswordFromDB);
    }

    public static boolean setVerified(String email) {
        String sql = "UPDATE users SET is_verified = TRUE WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean createUser(User newUser) {
        throw new UnsupportedOperationException("Unimplemented method 'createUser'");
    }

    public String Login(String email, String password) {
        // 1. Chỉ tìm theo email để lấy full_name, password_hash và is_verified lưu trong DB
        String sql = "SELECT full_name, password_hash, is_verified FROM users WHERE email = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hashedPasswordFromDB = rs.getString("password_hash");
                    String fullName = rs.getString("full_name");
                    boolean isVerified = rs.getBoolean("is_verified");
                    
                    if (!isVerified) {
                        return "UNREGISTERED";
                    }
                    // 2. Dùng BCrypt.checkpw để so sánh mật khẩu nhập vào với chuỗi hash
                    if (BCrypt.checkpw(password, hashedPasswordFromDB)) {
                        return fullName; // Đăng nhập thành công -> Trả về tên hiển thị
                    }
                    return null;
                }
                return "UNREGISTERED";
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Đăng nhập thất bại (sai email hoặc sai mật khẩu)
    }

    public static User getUserByEmail(String email) {
        String sql = "SELECT id, full_name, gender, email, dob FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setFullName(rs.getString("full_name"));
                user.setGender(rs.getString("gender"));
                user.setEmail(rs.getString("email"));
                user.setDob(rs.getString("dob"));
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static boolean updateUser (User user){
        String newPassword = user.getPassword(); // Lấy mật khẩu từ đối tượng User
        boolean hasNewPassword = newPassword != null && !newPassword.trim().isEmpty();
        
        String sql = hasNewPassword 
            ? "UPDATE users SET full_name = ?, gender = ?, dob = ?, password_hash = ? WHERE email = ?"
            : "UPDATE users SET full_name = ?, gender = ?, dob = ? WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getGender());
            stmt.setString(3, user.getDob());
            
            if (hasNewPassword) {
                // Hash mật khẩu mới bằng BCrypt trước khi lưu
                String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
                stmt.setString(4, hashedPassword);
                stmt.setString(5, user.getEmail());
            } else {
                stmt.setString(4, user.getEmail());
            }
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Ném lỗi database lên console thay vì trả một số 0 giả khi truy vấn thất bại.
    public static int getTotalUserCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        throw new SQLException("Không thể đọc tổng số người dùng.");
    }

    // 2. Cập nhật cờ khóa theo tên cột cụ thể trong CSDL của bạn
    // Chỉ cho phép các loại khóa hợp lệ trước khi đưa tên cột vào câu SQL.
    public static boolean setBlockOption(String email, String blockType, boolean blocked) throws SQLException {
        String column;
        switch (blockType.toLowerCase(Locale.ROOT)) {
            case "login":
                column = "is_blocked";
                break;
            case "create":
            case "create_quiz":
                column = "block_create_quiz";
                break;
            case "exam":
            case "take_exam":
                column = "block_take_exam";
                break;
            case "all":
                column = null;
                break;
            default:
                throw new IllegalArgumentException("Loại khóa không hợp lệ: " + blockType);
        }

        String sql = column == null
                ? "UPDATE users SET is_blocked = ?, block_create_quiz = ?, block_take_exam = ? WHERE email = ?"
                : "UPDATE users SET " + column + " = ? WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (column == null) {
                stmt.setBoolean(1, blocked);
                stmt.setBoolean(2, blocked);
                stmt.setBoolean(3, blocked);
                stmt.setString(4, email);
            } else {
                stmt.setBoolean(1, blocked);
                stmt.setString(2, email);
            }
            return stmt.executeUpdate() > 0;
        }
    }

    // 3. Kiểm tra xem user có bị cấm tính năng cụ thể hay không
    // Phân biệt rõ tài khoản không bị khóa với lỗi không đọc được trạng thái khóa.
    public static boolean isBlocked(String email, String type) throws SQLException {
        String column;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "login":
                column = "is_blocked";
                break;
            case "create":
            case "create_quiz":
                column = "block_create_quiz";
                break;
            case "exam":
            case "take_exam":
                column = "block_take_exam";
                break;
            default:
                throw new IllegalArgumentException("Loại khóa không hợp lệ: " + type);
        }

        String sql = "SELECT " + column + " FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(column);
            }
        }
    }
}
