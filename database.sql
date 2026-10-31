
-- 1. KHỞI TẠO CƠ SỞ DỮ LIỆU QUIZ_DB
CREATE DATABASE IF NOT EXISTS quiz_db 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE quiz_db;

-- 2. TÀI KHOẢN NGƯỜI DÙNG & XÁC THỰC
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,          -- username là địa chỉ email
    password_hash VARCHAR(255) NOT NULL,          -- mật khẩu băm BCrypt
    full_name VARCHAR(100) NOT NULL,              -- họ tên
    gender ENUM('Nam', 'Nữ', 'Khác') NOT NULL, -- giới tính
    dob DATE NOT NULL,                            -- ngày sinh
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,   -- xác thực OTP trước khi kích hoạt
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,    -- server khóa toàn diện: không đăng nhập
    block_create_quiz BOOLEAN NOT NULL DEFAULT FALSE, -- server khóa: cấm tạo bộ đề mới
    block_take_exam BOOLEAN NOT NULL DEFAULT FALSE,   -- server khóa: cấm tham gia thi
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS otp_verifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    expired_at TIMESTAMP NOT NULL,                -- hết hạn sau 10 phút
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otp_email (email)
);


-- 3. NGÂN HÀNG ĐỀ: BỘ ĐỀ, CÂU HỎI VÀ CÁC ĐÁP ÁN
CREATE TABLE IF NOT EXISTS quizzes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    creator_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    duration_minutes INT NOT NULL,                -- thời gian thi (phút)
    total_score DECIMAL(4, 2) NOT NULL DEFAULT 10.00, -- tổng điểm mặc định là 10
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS questions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    quiz_id INT NOT NULL,
    content TEXT NOT NULL,
    image_url VARCHAR(255) NULL,                  
    FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS options (
    id INT AUTO_INCREMENT PRIMARY KEY,
    question_id INT NOT NULL,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,    -- chỉ 1 đáp án đúng, chỉ lưu tại server
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);


-- 4. LƯỢT THI VÀ KẾT QUẢ; PHỤC VỤ THỐNG KÊ / XẾP HẠNG
CREATE TABLE IF NOT EXISTS exam_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    quiz_id INT NOT NULL,
    user_id INT NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP NULL,
    score DECIMAL(4, 2) NOT NULL DEFAULT 0.00,
    completion_time_seconds INT NOT NULL DEFAULT 0, -- tính bằng giây để xếp hạng
    FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    -- Index tối ưu xếp hạng: ưu tiên điểm DESC, sau đó ưu tiên thời gian ASC
    INDEX idx_leaderboard (quiz_id, score DESC, completion_time_seconds ASC)
);

CREATE TABLE IF NOT EXISTS exam_answers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id INT NOT NULL,
    question_id INT NOT NULL,
    selected_option_id INT NULL,
    is_correct BOOLEAN NOT NULL,                  -- server chấm đúng/sai từng câu
    FOREIGN KEY (session_id) REFERENCES exam_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    FOREIGN KEY (selected_option_id) REFERENCES options(id) ON DELETE SET NULL
);