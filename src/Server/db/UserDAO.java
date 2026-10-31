package Server.db;
import Server.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import Model.User;

import java.sql.*;
 public class UserDAO{

    public boolean isEmail(String email){
        String sql = "SELECT id FROM users WHERE email = ?";
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
        // Thêm cột expired_at vào câu lệnh INSERT
        String sql = "INSERT INTO otp_verifications (email, otp_code, expired_at) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE otp_code = VALUES(otp_code), expired_at = VALUES(expired_at)";
        
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            stmt.setString(2, otp);
            
            // Cấu hình thời gian hết hạn sau 10 phút tính từ hiện tại
            java.sql.Timestamp expiredAt = new java.sql.Timestamp(System.currentTimeMillis() + (10 * 60 * 1000));
            stmt.setTimestamp(3, expiredAt);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean verifyOTP (String email, String inputOTP){
        String sql = "SELECT id FROM otp_verifications WHERE email = ? AND otp_code = ? " +
                     "AND is_used = FALSE AND created_at >= NOW() - INTERVAL 10 MINUTE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, inputOTP);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int otpId = rs.getInt("id");
                String updateSql = "UPDATE otp_verifications SET is_used = TRUE WHERE id = ?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setInt(1, otpId);
                    updatePs.executeUpdate();
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean registerUser(String fullName, String gender, String email, String dob, String password) {
        String sql = "INSERT INTO users (full_name, gender, email, dob, password_hash, is_verified) VALUES (?, ?, ?, ?, ?, TRUE)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
            ps.setString(1, fullName);
            ps.setString(2, gender);
            ps.setString(3, email);
            ps.setString(4, dob);
            ps.setString(5, hashedPassword); 
            return ps.executeUpdate() > 0;
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
                    
                    // 2. Dùng BCrypt.checkpw để so sánh mật khẩu nhập vào với chuỗi hash
                    if (BCrypt.checkpw(password, hashedPasswordFromDB)) {
                        if (!isVerified) {
                            return "UNVERIFIED";
                        }
                        return fullName; // Đăng nhập thành công -> Trả về tên hiển thị
                    }
                }
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
}
