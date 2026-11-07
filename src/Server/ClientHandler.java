package Server;

import common.CryptoUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import com.google.gson.Gson;

import Model.Quiz;
import Server.db.QuizDAO;
import Server.db.UserDAO;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private KeyPair rsaKeyPair;
    private Server_socket server;
    private SecretKey aesKey;
    private UserDAO userDAO;
    private volatile String authenticatedEmail;
    private final Set<Integer> activeExamSessions =
            Collections.synchronizedSet(new HashSet<>());
    private static final Gson gson = new Gson();

    public ClientHandler(Socket clientSocket, KeyPair rsaKeyPair, Server_socket server) {
        this.clientSocket = clientSocket;
        this.rsaKeyPair = rsaKeyPair;
        this.server = server;
        this.userDAO = new UserDAO();
    }

    @Override
    public void run() {
        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter output = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            // 1. [TRAO ĐỔI KHÓA] Gửi RSA Public Key dạng Base64 cho Client
            output.println(Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded()));

            // 2. Nhận khóa AES đã được mã hóa bằng RSA từ Client và giải mã
            String encryptedAESKey = input.readLine();
            if (encryptedAESKey == null || encryptedAESKey.isEmpty()) return;

            String aesKeyStr = CryptoUtil.decryptRSA(encryptedAESKey, rsaKeyPair.getPrivate());
            this.aesKey = CryptoUtil.stringToAESKey(aesKeyStr);

            // 3. Vòng lặp nhận và xử lý request mã hóa AES
            String encryptedRequest;
            while ((encryptedRequest = input.readLine()) != null) {
                // Giải mã Request
                String request = CryptoUtil.decryptAES(encryptedRequest, aesKey);
                System.out.println("Request nhận được: " + request);

                // Xử lý Request (LOGIN, CREATE_QUIZ,...)
                String response = processRequest(request);

                // Mã hóa Response bằng AES và gửi về Client
                String encryptedResponse = CryptoUtil.encryptAES(response, aesKey);
                output.println(encryptedResponse);
            }
        } catch (Exception e) {
            System.err.println("Lỗi luồng ClientHandler: " + e.getMessage());
        } finally {
            server.removeClient(this);
            closeSocket();
        }
        }

    private String processRequest(String request) {
        String[] parts = request.split("\\|", -1);
        String action = parts[0];

        switch (action) {
            case "REQUEST_OTP":
                if (parts.length != 2 || parts[1].trim().isEmpty()) {
                    return "ERROR|Email đăng ký không hợp lệ!";
                }
                String emailForOTP = parts[1].trim();
                if (userDAO.isEmail(emailForOTP)) {
                    return "ERROR|Email này đã được sử dụng!";
                }

                String otpCode = EmailUtil.generateOTP();
                if (!UserDAO.saveOTP(emailForOTP, otpCode)) {
                    return "ERROR|Không thể lưu mã OTP. Vui lòng thử lại!";
                }

                if (EmailUtil.sendOTP(emailForOTP, otpCode)) {
                    return "SEND_OTP_SUCCESS|OTP Sent";
                }
                return "ERROR|Không thể gửi Email OTP. Vui lòng kiểm tra lại Email!";

            case "VERIFY_OTP":
                if (parts.length != 7) {
                    return "REGISTER_FAIL|Thông tin xác thực OTP không hợp lệ!";
                }
                if (parts[1].trim().isEmpty() || parts[2].trim().isEmpty()
                        || parts[3].trim().isEmpty() || parts[5].isEmpty()
                        || parts[5].length() < 6 || !parts[6].matches("\\d{6}")
                        || !(parts[2].equals("Nam") || parts[2].equals("Nữ")
                                || parts[2].equals("Khác"))) {
                    return "REGISTER_FAIL|Thông tin đăng ký không hợp lệ!";
                }
                try {
                    java.sql.Date.valueOf(parts[4]);
                } catch (IllegalArgumentException e) {
                    return "REGISTER_FAIL|Ngày sinh không hợp lệ!";
                }
                if (userDAO.verifyOTPAndRegister(
                        parts[1], parts[2], parts[3], parts[4], parts[5], parts[6])) {
                    return "REGISTER_SUCCESS|Tạo tài khoản thành công";
                }
                return "REGISTER_FAIL|Mã OTP không đúng hoặc đã hết hạn!";

            case "LOGIN":
                if (parts.length < 3) {
                    return "LOGIN_FAIL|Thông tin đăng nhập không hợp lệ!";
                }
                String loginEmail = parts[1];
                String loginPass = parts[2];

                clearAuthentication();
                try {
                    String loginSuccess = userDAO.Login(loginEmail, loginPass);
                    if ("UNREGISTERED".equals(loginSuccess)) {
                        return "LOGIN_FAIL|Tài khoản chưa đăng ký!";
                    }
                    if (loginSuccess == null) {
                        return "LOGIN_FAIL|Tài khoản hoặc mật khẩu không chính xác!";
                    }
                    // Chỉ kiểm tra trạng thái khóa sau khi thông tin đăng nhập hợp lệ.
                    if (UserDAO.isBlocked(loginEmail, "login")) {
                        return "LOGIN_FAIL|Tài khoản đã bị khóa đăng nhập!";
                    }
                    authenticatedEmail = loginEmail;
                    return "LOGIN_SUCCESS|" + loginEmail;
                } catch (Exception e) {
                    System.err.println("Lỗi kiểm tra đăng nhập: " + e.getMessage());
                    return "LOGIN_FAIL|Không thể xác thực tài khoản lúc này!";
                }

            case "LOGOUT":
                clearAuthentication();
                return "LOGOUT_SUCCESS";
            
            case "GET_PROFILE":
                if (parts.length < 2) {
                    return "GET_PROFILE_FAIL|Thiếu tham số Email!";
                }
                String reqEmail = parts[1].trim();
                if (!isAuthenticatedAs(reqEmail)) {
                    return "GET_PROFILE_FAIL|Vui lòng đăng nhập đúng tài khoản!";
                }
                
                // Truy vấn dữ liệu từ MySQL qua UserDAO
                Model.User user = UserDAO.getUserByEmail(reqEmail);
                if (user != null) {
                    // Trả về định dạng: GET_PROFILE_SUCCESS|fullName|gender|dob
                    return String.format("GET_PROFILE_SUCCESS|%s|%s|%s", 
                            user.getFullName() != null ? user.getFullName() : "", 
                            user.getGender() != null ? user.getGender() : "Nam", 
                            user.getDob() != null ? user.getDob() : "");
                } else {
                    return "GET_PROFILE_FAIL|Không tìm thấy thông tin tài khoản!";
                }

            case "UPDATE_PROFILE":
                if (parts.length < 5) {
                    return "UPDATE_PROFILE_FAIL|Dữ liệu cập nhật không đầy đủ!";
                }
                if (!isAuthenticatedAs(parts[1].trim())) {
                    return "UPDATE_PROFILE_FAIL|Vui lòng đăng nhập đúng tài khoản!";
                }
                // Khởi tạo đối tượng User từ model
                Model.User updateUser = new Model.User();
                updateUser.setEmail(parts[1]);
                updateUser.setFullName(parts[2]);
                updateUser.setGender(parts[3]);
                updateUser.setDob(parts[4]);
                if (parts.length >= 6) {
                    updateUser.setPassword(parts[5]);
                }
                if (UserDAO.updateUser(updateUser)) {
                    return "UPDATE_PROFILE_SUCCESS|Cập nhật thông tin thành công!";
                } else {
                    return "UPDATE_PROFILE_FAIL|Cập nhật dữ liệu thất bại!";
                }
            
            case "CREATE_QUIZ":
                try {
                    // Tách duy nhất làm 2 phần: parts[0] = "CREATE_QUIZ", parts[1] = chuỗi JSON
                    String[] quizParts = request.split("\\|", 2);
                    if (quizParts.length < 2 || quizParts[1].trim().isEmpty()) {
                        return "CREATE_QUIZ_FAIL|Dữ liệu JSON bài thi rỗng!";
                    }

                    String jsonQuiz = quizParts[1].trim();

                    // Dùng Gson chuyển chuỗi JSON thành Object Model.Quiz
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    Model.Quiz quiz = gson.fromJson(jsonQuiz, Model.Quiz.class);

                    // Lấy thông tin người tạo bài thi từ CreatorEmail
                    if (quiz.getCreatorEmail() == null || !isAuthenticatedAs(quiz.getCreatorEmail())) {
                        return "CREATE_QUIZ_FAIL|Vui lòng đăng nhập đúng tài khoản tạo đề!";
                    }
                    // Luôn lấy creatorId từ tài khoản đã xác thực, không tin ID client gửi lên.
                    if (UserDAO.isBlocked(quiz.getCreatorEmail(), "create")) {
                        return "CREATE_QUIZ_FAIL|Tài khoản bị cấm tạo bộ đề!";
                    }
                    Model.User creator = UserDAO.getUserByEmail(quiz.getCreatorEmail());
                    if (creator == null) {
                        return "CREATE_QUIZ_FAIL|Không xác thực được người tạo bộ đề!";
                    }
                    quiz.setCreatorId(creator.getId());

                    // Xử lý ảnh đính kèm 
                    if (quiz.getQuestions() != null) {
                        for (Model.Question q : quiz.getQuestions()) {
                            if (common.FileUtil.isBase64Image(q.getImageUrl())) {
                                String savedPath = common.FileUtil.saveBase64Image(q.getImageUrl());
                                if (savedPath == null) {
                                    return "CREATE_QUIZ_FAIL|Không thể lưu ảnh câu hỏi!";
                                }
                                q.setImageUrl(savedPath);
                            }
                        }
                    }

                    // Lưu vào CSDL 
                    if (QuizDAO.createQuiz(quiz)) {
                        return "CREATE_QUIZ_SUCCESS|Tạo bộ đề thi thành công!";
                    } else {
                        return "CREATE_QUIZ_FAIL|Lưu vào Cơ sở dữ liệu thất bại!";
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    return "CREATE_QUIZ_FAIL|Lỗi giải mã Gson phía Server: " + e.getMessage();
                }

            case "GET_ALL_QUIZZES":
                try {
                    java.util.List<Quiz> quizList = QuizDAO.getAllQuizzes();
                    
                    // Chuyển toàn bộ danh sách Quiz thành 1 chuỗi JSON
                    String jsonList = gson.toJson(quizList);
                    return "GET_ALL_QUIZZES_SUCCESS|" + jsonList;
                } catch (Exception e) {
                    e.printStackTrace();
                    return "GET_ALL_QUIZZES_FAIL|Không thể lấy danh sách bộ đề!";
                }

            case "START_EXAM":
                try {
                    int quizId = Integer.parseInt(parts[1].trim());
                    String userEmail = parts[2].trim();

                    if (!isAuthenticatedAs(userEmail)) {
                        return "START_EXAM_FAIL|Vui lòng đăng nhập đúng tài khoản!";
                    }
                    if (UserDAO.isBlocked(userEmail, "exam")) {
                        return "START_EXAM_FAIL|Tài khoản bị cấm tham gia thi!";
                    }
                    Model.User examUser = UserDAO.getUserByEmail(userEmail);
                    if (examUser == null) return "START_EXAM_FAIL|Không xác thực được người dùng!";

                    Model.Quiz quiz = Server.db.ExamDAO.getQuizForExam(quizId);
                    if (quiz == null) return "START_EXAM_FAIL|Bộ đề thi không tồn tại!";

                    if (quiz.getQuestions() != null) {
                        for (Model.Question question : quiz.getQuestions()) {
                            if (question.getImageUrl() != null && !question.getImageUrl().isEmpty()) {
                                question.setImageUrl(
                                        common.FileUtil.imageFileAsDataUri(question.getImageUrl()));
                            }
                        }
                    }

                    int sessionId = Server.db.ExamDAO.startExamSession(quizId, examUser.getId());
                    if (sessionId <= 0) return "START_EXAM_FAIL|Không thể tạo phiên làm bài!";
                    activeExamSessions.add(sessionId);

                    // Đóng gói Quiz (đã xáo trộn) + SessionID trả về Client bằng JSON
                    com.google.gson.JsonObject jsonResp = new com.google.gson.JsonObject();
                    jsonResp.addProperty("sessionId", sessionId);
                    jsonResp.add("quiz", gson.toJsonTree(quiz));

                    return "START_EXAM_SUCCESS|" + gson.toJson(jsonResp);
                } catch (Exception e) {
                    e.printStackTrace();
                    return "START_EXAM_FAIL|Lỗi dữ liệu phía Server!";
                }
            case "SUBMIT_ANSWER":
                // SUBMIT_ANSWER|sessionId|questionId|selectedOptionId
                try {
                    int sessionId = Integer.parseInt(parts[1].trim());
                    int questionId = Integer.parseInt(parts[2].trim());
                    int selectedOptId = Integer.parseInt(parts[3].trim());
                    if (!activeExamSessions.contains(sessionId)) {
                        return "SUBMIT_ANSWER_FAIL|Phiên thi không thuộc kết nối này!";
                    }
                    if (UserDAO.isBlocked(authenticatedEmail, "exam")) {
                        return "SUBMIT_ANSWER_FAIL|Tài khoản bị cấm tham gia thi!";
                    }

                    // DAO kiểm tra phiên, câu hỏi, đáp án và chống gửi lặp trước khi ghi nhận.
                    boolean isCorrect = Server.db.ExamDAO.submitAnswer(sessionId, questionId, selectedOptId);

                    return "SUBMIT_ANSWER_SUCCESS|" + isCorrect;
                } catch (Exception e) {
                    e.printStackTrace();
                    return "SUBMIT_ANSWER_FAIL|Lỗi chấm điểm câu hỏi!";
                }

            case "FINISH_EXAM":
                // Client gửi thời gian theo protocol cũ; kết quả server không sử dụng giá trị này.
                try {
                    int sessionId = Integer.parseInt(parts[1].trim());
                    if (!activeExamSessions.contains(sessionId)) {
                        return "FINISH_EXAM_FAIL|Phiên thi không thuộc kết nối này!";
                    }
                    if (UserDAO.isBlocked(authenticatedEmail, "exam")) {
                        return "FINISH_EXAM_FAIL|Tài khoản bị cấm tham gia thi!";
                    }

                    // Không dùng thời gian client gửi; server tính thời gian từ started_at trong DB.
                    double finalScore = Server.db.ExamDAO.finishExamSession(sessionId);

                    // Lấy quiz_id từ phiên vừa hoàn tất để truy vấn đúng bảng xếp hạng.
                    int quizId = Server.db.ExamDAO.getQuizIdBySession(sessionId);

                    // Lấy Bảng xếp hạng mới nhất của bộ đề nếu có quizId
                    List<Model.ExamResult> leaderboard = java.util.Collections.emptyList();
                    if (quizId > 0) {
                        leaderboard = Server.db.ExamDAO.getLeaderboardByQuiz(quizId);
                    }
                    activeExamSessions.remove(sessionId);

                    // Đóng gói kết quả tổng hợp + Bảng xếp hạng bằng Gson
                    com.google.gson.JsonObject jsonResp = new com.google.gson.JsonObject();
                    jsonResp.addProperty("finalScore", finalScore);
                    jsonResp.addProperty("quizId", quizId);
                    jsonResp.add("leaderboard", gson.toJsonTree(leaderboard));

                    return "FINISH_EXAM_SUCCESS|" + gson.toJson(jsonResp);
                } catch (Exception e) {
                    e.printStackTrace();
                    return "FINISH_EXAM_FAIL|Lỗi hoàn tất bài thi!";
                }

            case "CHECK_CAN_EDIT_QUIZ":
                try {
                    int quizId = Integer.parseInt(parts[1].trim());
                    String userEmail = parts[2].trim();
                    if (!isAuthenticatedAs(userEmail)) {
                        return "CHECK_CAN_EDIT_FAIL|Vui lòng đăng nhập đúng tài khoản!";
                    }

                    Model.User editingUser = UserDAO.getUserByEmail(userEmail);
                    if (editingUser == null) return "CHECK_CAN_EDIT_FAIL|Không xác thực được người dùng!";

                    // LẤY ĐẦY ĐỦ CÂU HỎI VÀ ĐÁP ÁN BẰNG HÀM getQuizByIdForEdit
                    Model.Quiz quiz = QuizDAO.getQuizByIdForEdit(quizId);
                    if (quiz == null) return "CHECK_CAN_EDIT_FAIL|Không tìm thấy bộ đề thi!";

                    // Kiểm tra quyền sở hữu bộ đề: Chỉ người tạo mới được sửa đề của chính mình
                    if (quiz.getCreatorId() != editingUser.getId()) {
                        return "CHECK_CAN_EDIT_FAIL|Bạn chỉ có thể chỉnh sửa bộ đề do chính bạn tạo ra!";
                    }

                    // Kiểm tra xem bộ đề đã có ai làm bài chưa
                    if (QuizDAO.hasQuizBeenTaken(quizId)) {
                        return "CHECK_CAN_EDIT_FAIL|Bộ đề thi này đã có người làm bài nên không được phép chỉnh sửa!";
                    }

                    // Trả chuỗi JSON chứa đầy đủ câu hỏi về Client
                    return "CHECK_CAN_EDIT_SUCCESS|" + gson.toJson(quiz);
                } catch (Exception e) {
                    e.printStackTrace();
                    return "CHECK_CAN_EDIT_FAIL|Lỗi hệ thống phía Server!";
                }

            case "UPDATE_QUIZ":
                try {
                    // Tách làm 2 phần: parts[0] = "UPDATE_QUIZ", parts[1] = chuỗi JSON
                    String[] updateParts = request.split("\\|", 2);
                    if (updateParts.length < 2 || updateParts[1].trim().isEmpty()) {
                        return "UPDATE_QUIZ_FAIL|Dữ liệu JSON cập nhật rỗng!";
                    }

                    String jsonQuiz = updateParts[1].trim();

                    // Giải mã chuỗi JSON thành Object Model.Quiz bằng Gson
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    Model.Quiz quiz = gson.fromJson(jsonQuiz, Model.Quiz.class);

                    // Kiểm tra quyền sở hữu bộ đề: chỉ người tạo mới được cập nhật
                    if (quiz.getCreatorEmail() == null || !isAuthenticatedAs(quiz.getCreatorEmail())) {
                        return "UPDATE_QUIZ_FAIL|Vui lòng đăng nhập đúng tài khoản tạo đề!";
                    }
                    if (UserDAO.isBlocked(quiz.getCreatorEmail(), "create")) {
                        return "UPDATE_QUIZ_FAIL|Tài khoản bị cấm quản lý bộ đề!";
                    }
                    if (!quiz.getCreatorEmail().isEmpty()) {
                        Model.User updater = UserDAO.getUserByEmail(quiz.getCreatorEmail());
                        Model.Quiz existing = QuizDAO.getQuizByIdForEdit(quiz.getId());
                        if (updater == null || existing == null || existing.getCreatorId() != updater.getId()) {
                            return "UPDATE_QUIZ_FAIL|Bạn không có quyền chỉnh sửa bộ đề của người khác!";
                        }
                    }

                    // Kiểm tra lại ở Server xem bài thi đã có ai thi chưa
                    if (QuizDAO.hasQuizBeenTaken(quiz.getId())) {
                        return "UPDATE_QUIZ_FAIL|Bộ đề thi này vừa có người làm bài, không thể chỉnh sửa!";
                    }

                    // Xử lý lưu các ảnh mới đính kèm (nếu người dùng đổi/thêm ảnh dạng Base64)
                    if (quiz.getQuestions() != null) {
                        for (Model.Question q : quiz.getQuestions()) {
                            if (common.FileUtil.isBase64Image(q.getImageUrl())) {
                                String savedPath = common.FileUtil.saveBase64Image(q.getImageUrl());
                                if (savedPath == null) {
                                    return "UPDATE_QUIZ_FAIL|Không thể lưu ảnh câu hỏi!";
                                }
                                q.setImageUrl(savedPath);
                            }
                        }
                    }

                    // Gọi QuizDAO để cập nhật CSDL MySQL
                    if (QuizDAO.updateQuiz(quiz)) {
                        return "UPDATE_QUIZ_SUCCESS|Cập nhật bộ đề thi thành công!";
                    } else {
                        return "UPDATE_QUIZ_FAIL|Lưu dữ liệu cập nhật vào MySQL thất bại!";
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    return "UPDATE_QUIZ_FAIL|Lỗi giải mã Gson phía Server: " + e.getMessage();
                }

                case "GET_USER_STATS":
                    try {
                        if (parts.length < 2) {
                            return "GET_USER_STATS_FAIL|Thiếu email người dùng!";
                        }
                        String statsEmail = parts[1].trim();
                        if (!isAuthenticatedAs(statsEmail)) {
                            return "GET_USER_STATS_FAIL|Vui lòng đăng nhập đúng tài khoản!";
                        }
                        String stats = Server.db.ExamDAO.getUserStats(statsEmail);
                        return "GET_USER_STATS_SUCCESS|" + stats;
                    } catch (Exception e) {
                        System.err.println("Lỗi lấy thống kê người dùng: " + e.getMessage());
                        return "GET_USER_STATS_FAIL|Không thể lấy thống kê người dùng!";
                    }
            default:
                return "ERROR|Lệnh không hợp lệ";
        }
    }

    public String getAuthenticatedEmail() {
        return authenticatedEmail;
    }

    public boolean isAuthenticatedAs(String email) {
        return authenticatedEmail != null && email != null
                && authenticatedEmail.equalsIgnoreCase(email);
    }

    public void clearAuthentication() {
        authenticatedEmail = null;
        activeExamSessions.clear();
    }

    public void closeConnection() {
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}