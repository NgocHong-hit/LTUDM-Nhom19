package Server.db;

import Model.Option;
import Model.Question;
import Model.Quiz;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuizDAO {

    // Tạo Bộ đề mới (Transaction)
    public static boolean createQuiz(Quiz quiz) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu Transaction

            // Thêm Bộ đề vào bảng quizzes
            String sqlQuiz = "INSERT INTO quizzes (creator_id, title, description, duration_minutes, total_score) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stmtQuiz = conn.prepareStatement(sqlQuiz, Statement.RETURN_GENERATED_KEYS);
            stmtQuiz.setInt(1, quiz.getCreatorId());
            stmtQuiz.setString(2, quiz.getTitle());
            stmtQuiz.setString(3, quiz.getDescription());
            stmtQuiz.setInt(4, quiz.getDurationMinutes());
            stmtQuiz.setDouble(5, quiz.getTotalScore());
            
            if (stmtQuiz.executeUpdate() == 0) {
                conn.rollback();
                return false;
            }

            ResultSet rsQuizKey = stmtQuiz.getGeneratedKeys();
            int quizId = 0;
            if (rsQuizKey.next()) quizId = rsQuizKey.getInt(1);

            // Thêm từng câu hỏi vào bảng questions
            String sqlQ = "INSERT INTO questions (quiz_id, content, image_url) VALUES (?, ?, ?)";
            PreparedStatement stmtQ = conn.prepareStatement(sqlQ, Statement.RETURN_GENERATED_KEYS);

            String sqlOpt = "INSERT INTO options (question_id, content, is_correct) VALUES (?, ?, ?)";
            PreparedStatement stmtOpt = conn.prepareStatement(sqlOpt);

            for (Question q : quiz.getQuestions()) {
                stmtQ.setInt(1, quizId);
                stmtQ.setString(2, q.getContent());
                stmtQ.setString(3, q.getImageUrl());
                stmtQ.executeUpdate();

                ResultSet rsQKey = stmtQ.getGeneratedKeys();
                int questionId = 0;
                if (rsQKey.next()) questionId = rsQKey.getInt(1);

                // Thêm 4 đáp án vào bảng options
                for (Option opt : q.getOptions()) {
                    stmtOpt.setInt(1, questionId);
                    stmtOpt.setString(2, opt.getContent());
                    stmtOpt.setBoolean(3, opt.isCorrect());
                    stmtOpt.addBatch();
                }
                stmtOpt.executeBatch();
            }

            conn.commit(); 
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    // Lấy toàn bộ danh sách bộ đề thi từ CSDL 
    public static List<Quiz> getAllQuizzes() {
        List<Quiz> list = new ArrayList<>();
        String sql = "SELECT q.id, q.creator_id, u.email AS creator_email, q.title, q.description, q.duration_minutes, u.full_name AS creator_name, "
                + "(SELECT COUNT(*) FROM questions WHERE quiz_id = q.id) AS total_questions, "
                + "COUNT(es.id) AS attempt_count, "
                + "COALESCE(AVG(es.score), 0.00) AS avg_score, "
                + "COALESCE(MAX(es.score), 0.00) AS max_score "
                + "FROM quizzes q "
                + "JOIN users u ON q.creator_id = u.id "
                + "LEFT JOIN exam_sessions es ON q.id = es.quiz_id AND es.submitted_at IS NOT NULL "
                + "GROUP BY q.id, q.creator_id, u.email, q.title, q.description, q.duration_minutes, u.full_name, q.created_at "
                + "ORDER BY q.created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Quiz quiz = new Quiz();
                quiz.setId(rs.getInt("id"));
                quiz.setTitle(rs.getString("title"));
                quiz.setDescription(rs.getString("description"));
                quiz.setDurationMinutes(rs.getInt("duration_minutes"));
                quiz.setCreatorName(rs.getString("creator_name"));
                quiz.setTotalQuestions(rs.getInt("total_questions"));
                quiz.setCreatorId(rs.getInt("creator_id"));
                quiz.setCreatorEmail(rs.getString("creator_email"));
                
                // Đổ các chỉ số thống kê
                quiz.setAttemptCount(rs.getInt("attempt_count"));
                quiz.setAvgScore(Math.round(rs.getDouble("avg_score") * 100.0) / 100.0);
                quiz.setMaxScore(Math.round(rs.getDouble("max_score") * 100.0) / 100.0);

                list.add(quiz);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Kiểm tra xem bộ đề đã có ai thi chưa
    public static boolean hasQuizBeenTaken(int quizId) {
        String sql = "SELECT COUNT(*) FROM exam_sessions WHERE quiz_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, quizId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0; // Trả về true nếu đã có ít nhất 1 lượt thi
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    //  Lấy chi tiết bộ đề đáp án và câu hỏi
    public static Quiz getQuizByIdForEdit(int quizId) {
        Quiz quiz = null;
        String sqlQuiz = "SELECT * FROM quizzes WHERE id = ?";
        String sqlQ = "SELECT * FROM questions WHERE quiz_id = ?";
        String sqlOpt = "SELECT * FROM options WHERE question_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            PreparedStatement stmtQuiz = conn.prepareStatement(sqlQuiz);
            stmtQuiz.setInt(1, quizId);
            ResultSet rsQuiz = stmtQuiz.executeQuery();

            if (rsQuiz.next()) {
                quiz = new Quiz();
                quiz.setId(rsQuiz.getInt("id"));
                quiz.setCreatorId(rsQuiz.getInt("creator_id"));
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

                    // Lấy 4 đáp án và cờ is_correct
                    PreparedStatement stmtOpt = conn.prepareStatement(sqlOpt);
                    stmtOpt.setInt(1, q.getId());
                    ResultSet rsOpt = stmtOpt.executeQuery();
                    List<Option> options = new ArrayList<>();

                    while (rsOpt.next()) {
                        Option opt = new Option();
                        opt.setId(rsOpt.getInt("id"));
                        opt.setQuestionId(rsOpt.getInt("question_id"));
                        opt.setContent(rsOpt.getString("content"));
                        opt.setCorrect(rsOpt.getBoolean("is_correct"));
                        options.add(opt);
                    }
                    q.setOptions(options);
                    questions.add(q);
                }
                quiz.setQuestions(questions);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return quiz;
    }

    // Cập nhật Bộ đề 
    public static boolean updateQuiz(Quiz quiz) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); 

            // Cập nhật thông tin chung bảng quizzes
            String sqlQuiz = "UPDATE quizzes SET title = ?, description = ?, duration_minutes = ? WHERE id = ?";
            PreparedStatement stmtQuiz = conn.prepareStatement(sqlQuiz);
            stmtQuiz.setString(1, quiz.getTitle());
            stmtQuiz.setString(2, quiz.getDescription());
            stmtQuiz.setInt(3, quiz.getDurationMinutes());
            stmtQuiz.setInt(4, quiz.getId());
            stmtQuiz.executeUpdate();

            // Xóa câu hỏi cũ (bảng options tự động xóa theo nhờ ON DELETE CASCADE)
            String sqlDelQ = "DELETE FROM questions WHERE quiz_id = ?";
            PreparedStatement stmtDelQ = conn.prepareStatement(sqlDelQ);
            stmtDelQ.setInt(1, quiz.getId());
            stmtDelQ.executeUpdate();

            // Chèn lại danh sách câu hỏi và đáp án mới
            String sqlQ = "INSERT INTO questions (quiz_id, content, image_url) VALUES (?, ?, ?)";
            PreparedStatement stmtQ = conn.prepareStatement(sqlQ, Statement.RETURN_GENERATED_KEYS);

            String sqlOpt = "INSERT INTO options (question_id, content, is_correct) VALUES (?, ?, ?)";
            PreparedStatement stmtOpt = conn.prepareStatement(sqlOpt);

            for (Question q : quiz.getQuestions()) {
                stmtQ.setInt(1, quiz.getId());
                stmtQ.setString(2, q.getContent());
                stmtQ.setString(3, q.getImageUrl());
                stmtQ.executeUpdate();

                ResultSet rsQKey = stmtQ.getGeneratedKeys();
                int questionId = 0;
                if (rsQKey.next()) questionId = rsQKey.getInt(1);

                for (Option opt : q.getOptions()) {
                    stmtOpt.setInt(1, questionId);
                    stmtOpt.setString(2, opt.getContent());
                    stmtOpt.setBoolean(3, opt.isCorrect());
                    stmtOpt.addBatch();
                }
                stmtOpt.executeBatch();
            }

            conn.commit(); 
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    
}