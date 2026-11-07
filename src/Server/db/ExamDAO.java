package Server.db;

import Model.Option;
import Model.Question;
import Model.Quiz;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public  class ExamDAO{
    // 1. Lấy dữ liệu đề thi, xáo trộn câu hỏi và đề thi
    public static Quiz getQuizForExam(int quizId) {
        Quiz quiz = null;
        String sqlQuiz = "SELECT * FROM quizzes WHERE id = ?";
        String sqlQ = "SELECT * FROM questions WHERE quiz_id = ?";
        String sqlOpt = "SELECT id, question_id, content FROM options WHERE question_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            PreparedStatement stmtQuiz = conn.prepareStatement(sqlQuiz);
            stmtQuiz.setInt(1, quizId);
            ResultSet rsQuiz = stmtQuiz.executeQuery();

            if (rsQuiz.next()) {
                quiz = new Quiz();
                quiz.setId(rsQuiz.getInt("id"));
                quiz.setTitle(rsQuiz.getString("title"));
                quiz.setDescription(rsQuiz.getString("description"));
                quiz.setDurationMinutes(rsQuiz.getInt("duration_minutes"));
                quiz.setTotalScore(rsQuiz.getDouble("total_score"));

                // Lấy danh sách câu hỏi
                PreparedStatement stmtQ = conn.prepareStatement(sqlQ);
                stmtQ.setInt(1, quizId);
                ResultSet rsQ = stmtQ.executeQuery();
                List<Question> questions = new ArrayList<>();

                while (rsQ.next()) {
                    Question q = new Question();
                    q.setId(rsQ.getInt("id"));
                    q.setQuizId(quizId);
                    q.setContent(rsQ.getString("content"));
                    q.setImageUrl(rsQ.getString("image_url"));

                    // Lấy các phương án (không lấy field is_correct)
                    PreparedStatement stmtOpt = conn.prepareStatement(sqlOpt);
                    stmtOpt.setInt(1, q.getId());
                    ResultSet rsOpt = stmtOpt.executeQuery();
                    List<Option> options = new ArrayList<>();

                    while (rsOpt.next()) {
                        Option opt = new Option();
                        opt.setId(rsOpt.getInt("id"));
                        opt.setQuestionId(rsOpt.getInt("question_id"));
                        opt.setContent(rsOpt.getString("content"));
                        // Bảo mật: Không gán isCorrect ở đây!
                        options.add(opt);
                    }

                    // Xáo trộn 4 đáp án ngẫu nhiên
                    Collections.shuffle(options);
                    q.setOptions(options);
                    questions.add(q);
                }

                // Xáo trộn thứ tự các câu hỏi ngẫu nhiên
                Collections.shuffle(questions);
                quiz.setQuestions(questions);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return quiz;
    }

    // Tạo phiên thi mới trong exam_sessions
    public static int startExamSession(int quizId, int userId) {
        String sql = "INSERT INTO exam_sessions (quiz_id, user_id, started_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, quizId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Server tự chấm điểm 1 câu hỏi và ghi nhận vào exam_answers
    public static boolean submitAnswer(int sessionId, int questionId, int selectedOptionId) throws SQLException {
        String sqlSession = "SELECT quiz_id FROM exam_sessions "
                + "WHERE id = ? AND submitted_at IS NULL FOR UPDATE";
        String sqlQuestion = "SELECT 1 FROM questions WHERE id = ? AND quiz_id = ?";
        String sqlExisting = "SELECT 1 FROM exam_answers WHERE session_id = ? AND question_id = ?";
        String sqlOption = "SELECT is_correct FROM options WHERE id = ? AND question_id = ?";
        String sqlSave = "INSERT INTO exam_answers "
                + "(session_id, question_id, selected_option_id, is_correct) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int quizId;
                try (PreparedStatement stmt = conn.prepareStatement(sqlSession)) {
                    stmt.setInt(1, sessionId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Phiên thi không tồn tại hoặc đã nộp.");
                        }
                        quizId = rs.getInt("quiz_id");
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(sqlQuestion)) {
                    stmt.setInt(1, questionId);
                    stmt.setInt(2, quizId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Câu hỏi không thuộc đề thi của phiên này.");
                        }
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(sqlExisting)) {
                    stmt.setInt(1, sessionId);
                    stmt.setInt(2, questionId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            throw new SQLException("Câu hỏi này đã được trả lời trong phiên thi.");
                        }
                    }
                }

                boolean isCorrect = false;
                if (selectedOptionId > 0) {
                    try (PreparedStatement stmt = conn.prepareStatement(sqlOption)) {
                        stmt.setInt(1, selectedOptionId);
                        stmt.setInt(2, questionId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Đáp án không thuộc câu hỏi đã chọn.");
                            }
                            isCorrect = rs.getBoolean("is_correct");
                        }
                    }
                }

                // Server đối chiếu đáp án đúng và ghi kết quả trong cùng transaction.
                try (PreparedStatement stmt = conn.prepareStatement(sqlSave)) {
                    stmt.setInt(1, sessionId);
                    stmt.setInt(2, questionId);
                    if (selectedOptionId > 0) stmt.setInt(3, selectedOptionId);
                    else stmt.setNull(3, Types.INTEGER);
                    stmt.setBoolean(4, isCorrect);
                    stmt.executeUpdate();
                }
                conn.commit();
                return isCorrect;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // Hoàn tất bài thi
    public static double finishExamSession(int sessionId) throws SQLException {
        String sqlCount = "SELECT COUNT(*) AS total_correct FROM exam_answers WHERE session_id = ? AND is_correct = TRUE";
        String sqlQuizInfo = "SELECT q.total_score, "
                           + "TIMESTAMPDIFF(SECOND, es.started_at, CURRENT_TIMESTAMP) AS elapsed_seconds, "
                           + "(SELECT COUNT(*) FROM questions WHERE quiz_id = q.id) AS total_questions "
                           + "FROM quizzes q JOIN exam_sessions es ON q.id = es.quiz_id "
                           + "WHERE es.id = ? AND es.submitted_at IS NULL FOR UPDATE";
        String sqlUpdate = "UPDATE exam_sessions SET submitted_at = CURRENT_TIMESTAMP, "
                + "score = ?, completion_time_seconds = ? WHERE id = ? AND submitted_at IS NULL";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                double totalScore;
                int totalQuestions;
                int elapsedSeconds;
                try (PreparedStatement stmt = conn.prepareStatement(sqlQuizInfo)) {
                    stmt.setInt(1, sessionId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Phiên thi không tồn tại hoặc đã được nộp.");
                        }
                        totalScore = rs.getDouble("total_score");
                        totalQuestions = rs.getInt("total_questions");
                        elapsedSeconds = rs.getInt("elapsed_seconds");
                    }
                }

                int totalCorrect;
                try (PreparedStatement stmt = conn.prepareStatement(sqlCount)) {
                    stmt.setInt(1, sessionId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Không thể tính số câu trả lời đúng.");
                        }
                        totalCorrect = rs.getInt("total_correct");
                    }
                }

                double finalScore = 0.0;
                if (totalQuestions > 0) {
                    finalScore = (double) totalCorrect / totalQuestions * totalScore;
                    finalScore = Math.round(finalScore * 100.0) / 100.0;
                }

                // Tính thời gian tại server/database để client không thể giả mạo thứ hạng.
                try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                    stmt.setDouble(1, finalScore);
                    stmt.setInt(2, elapsedSeconds);
                    stmt.setInt(3, sessionId);
                    if (stmt.executeUpdate() != 1) {
                        throw new SQLException("Không thể cập nhật kết quả phiên thi.");
                    }
                }
                conn.commit();
                return finalScore;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static int getQuizIdBySession(int sessionId) {
        String sql = "SELECT quiz_id FROM exam_sessions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quiz_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Thống kê bài thi của người dùng
    public static String getUserStats(String email) {
        String sql = "SELECT COUNT(es.id) AS completed_exams, COALESCE(AVG(es.score), 0.00) AS user_avg "
                + "FROM exam_sessions es JOIN users u ON es.user_id = u.id "
                + "WHERE u.email = ? AND es.submitted_at IS NOT NULL";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt("completed_exams");
                double avg = Math.round(rs.getDouble("user_avg") * 100.0) / 100.0;
                return count + "|" + avg;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "0|0.00";
    }

    // Lấy bảng xếp hạng của một bộ đề
    public static List<Model.ExamResult> getLeaderboardByQuiz(int quizId) {
        List<Model.ExamResult> list = new ArrayList<>();
        // SQL ưu tiên điểm DESC, thời gian làm bài ASC (dùng đúng Index idx_leaderboard)
        String sql = "SELECT u.full_name, es.score, es.completion_time_seconds, es.submitted_at "
                + "FROM exam_sessions es "
                + "JOIN users u ON es.user_id = u.id "
                + "WHERE es.quiz_id = ? AND es.submitted_at IS NOT NULL "
                + "ORDER BY es.score DESC, es.completion_time_seconds ASC "
                + "LIMIT 50";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, quizId);
            ResultSet rs = stmt.executeQuery();

            int rank = 1;
            while (rs.next()) {
                Model.ExamResult res = new Model.ExamResult();
                res.setRank(rank++);
                res.setUserName(rs.getString("full_name"));
                res.setScore(rs.getDouble("score"));
                res.setCompletionTimeSeconds(rs.getInt("completion_time_seconds"));
                res.setSubmittedAt(rs.getString("submitted_at"));
                list.add(res);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}