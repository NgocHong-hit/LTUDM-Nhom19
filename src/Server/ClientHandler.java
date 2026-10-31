package Server;

import common.CryptoUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyPair;
import java.util.List;

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
            PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"))
        ) {
            // 1. Gửi RSA Public Key cho Client dưới dạng String Base64
            String publicKeyStr = CryptoUtil.publicKeyToString(rsaKeyPair.getPublic());
            output.println(publicKeyStr);

            // 2. Nhận Encrypted AES Key và dùng RSA Private Key để giải mã
            String encryptedAESKey = input.readLine();
            if (encryptedAESKey == null || encryptedAESKey.isEmpty()) return;

            String aesKeyStr = CryptoUtil.decryptRSA(encryptedAESKey, rsaKeyPair.getPrivate());
            this.aesKey = CryptoUtil.stringToAESKey(aesKeyStr);

            // 3. Vòng lặp nhận dữ liệu đã mã hóa AES từ Client
            while (true) {
                String encryptedRequest = input.readLine();
                if (encryptedRequest == null) break;

                // Giải mã Request bằng AES
                String request = CryptoUtil.decryptAES(encryptedRequest, aesKey);
                System.out.println("Request nhận được: " + request);

                // Xử lý request (ví dụ: LOGIN, REGISTER...)
                String response = processRequest(request);

                // Mã hóa Response bằng AES và gửi lại Client
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
            String emailForOTP = parts[1];
            // Kiểm tra Email đã tồn tại trong CSDL chưa
            if (userDAO.isEmail(emailForOTP)) {
                return "ERROR|Email này đã được sử dụng!";
            }
            // Tạo mã OTP 6 số & Lưu vào CSDL/Bộ nhớ
            String otpCode = EmailUtil.generateOTP();

            // userDAO.saveOTP(emailForOTP, otpCode);
            
            // Gửi email cho người dùng
            boolean isSent = EmailUtil.sendOTP(emailForOTP, otpCode);
            if (isSent) {
                return "SEND_OTP_SUCCESS|OTP Sent";
            } else {
                return "ERROR|Không thể gửi Email OTP. Vui lòng kiểm tra lại Email!";
            }

            case "REGISTER":
                //REGISTER|fullName|gender|email|dob|password|otp
                String fullName = parts[1];
                String gender = parts[2];
                String email = parts[3];
                String dob = parts[4];
                String password = parts[5];
                String inputOtp = parts[6];

                // Kiểm tra mã OTP
                boolean isValidOtp = userDAO.verifyOTP(email, inputOtp);
                if (!isValidOtp) {
                    return "REGISTER_FAIL|Mã OTP không đúng hoặc đã hết hạn!";
                }

                // Thêm tài khoản mới vào CSDL
                boolean isSuccess = userDAO.registerUser(fullName, gender, email, dob, password);
                if (isSuccess) {
                    return "REGISTER_SUCCESS|Tạo tài khoản thành công";
                } else {
                    return "REGISTER_FAIL|Lỗi hệ thống khi lưu thông tin người dùng!";
                }

            case "LOGIN":
                if (parts.length < 3) {
                    return "LOGIN_FAIL|Thông tin đăng nhập không hợp lệ!";
                }
                String loginEmail = parts[1];
                String loginPass = parts[2];

                String loginSuccess = userDAO.Login(loginEmail, loginPass);
                if ("UNVERIFIED".equals(loginSuccess)) {
                    return "LOGIN_FAIL|Tài khoản chưa được kích hoạt xác thực OTP!";
                } else if (loginSuccess != null) {
                    return "LOGIN_SUCCESS|" + loginEmail;
                } else {
                    return "LOGIN_FAIL|Tài khoản hoặc mật khẩu không chính xác!";
                }
            
            case "GET_PROFILE":
                if (parts.length < 2) {
                    return "GET_PROFILE_FAIL|Thiếu tham số Email!";
                }
                String reqEmail = parts[1].trim();
                
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
                    if (quiz.getCreatorEmail() != null && !quiz.getCreatorEmail().isEmpty()) {
                        Model.User creator = UserDAO.getUserByEmail(quiz.getCreatorEmail());
                        if (creator != null) {
                            quiz.setCreatorId(creator.getId());
                        } else {
                            return "CREATE_QUIZ_FAIL|Không xác thực được người tạo bộ đề!";
                        }
                    }

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

                    // Server đối chiếu đáp án và ghi nhận
                    boolean isCorrect = Server.db.ExamDAO.submitAnswer(sessionId, questionId, selectedOptId);

                    return "SUBMIT_ANSWER_SUCCESS|" + isCorrect;
                } catch (Exception e) {
                    e.printStackTrace();
                    return "SUBMIT_ANSWER_FAIL|Lỗi chấm điểm câu hỏi!";
                }

            case "FINISH_EXAM":
                // FINISH_EXAM|sessionId|completionTimeSeconds
                try {
                    int sessionId = Integer.parseInt(parts[1].trim());
                    int timeSeconds = Integer.parseInt(parts[2].trim());

                    // Tính tổng điểm & Lưu phiên thi vào MySQL
                    double finalScore = Server.db.ExamDAO.finishExamSession(sessionId, timeSeconds);

                    // Lấy quiz_id từ phiên vừa hoàn tất để truy vấn đúng bảng xếp hạng.
                    int quizId = Server.db.ExamDAO.getQuizIdBySession(sessionId);

                    // Lấy Bảng xếp hạng mới nhất của bộ đề nếu có quizId
                    List<Model.ExamResult> leaderboard = java.util.Collections.emptyList();
                    if (quizId > 0) {
                        leaderboard = Server.db.ExamDAO.getLeaderboardByQuiz(quizId);
                    }

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
                    if (quiz.getCreatorEmail() != null && !quiz.getCreatorEmail().isEmpty()) {
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
                        String statsEmail = parts[1].trim();
                        String stats = Server.db.ExamDAO.getUserStats(statsEmail);
                        return "GET_USER_STATS_SUCCESS|" + stats;
                    } catch (Exception e) {
                        return "GET_USER_STATS_FAIL|0|0.00";
                    }
            default:
                return "ERROR|Lệnh không hợp lệ";
        }
    }

    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}