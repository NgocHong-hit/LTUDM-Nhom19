Tên đề tài: CHIA SẼ ĐỀ VÀ THI TRẮC NGHIỆM
Danh sách thành viên
PHAN LÊ NGỌC HỒNG - 3122410134
CÁCH CHẠY SERVER-CLIENT
biên dịch
rmdir /s /q bin & mkdir bin\fxml & mkdir bin\css & dir /s /b src\*.java > sources.txt & javac -encoding UTF-8 -cp "lib/*" -d bin @sources.txt & del sources.txt & copy /Y src\Client_GUI\GUI\fxml\*.fxml bin\fxml\ & copy /Y src\Client_GUI\GUI\css\*.css bin\css\

chạy server
java -cp "bin;lib/*" Server.Server_socket

chạy GUI
java --module-path "lib" --add-modules javafx.controls,javafx.fxml -Djava.library.path="lib" -cp "bin;lib/*" Client_GUI.MainApp

Lệnh quản trị tại dấu nhắc server>
help
stats
online
block <email> [all|login|create|exam]
block <email>|<loại khóa>
unblock <email> [all|login|create|exam]
unblock <email>|<loại khóa>
quit

`stats` hiển thị tổng người dùng, số email đã đăng nhập đang online,
tổng bộ đề, điểm cao nhất và thấp nhất của các lượt thi đã nộp.
`block` / `unblock` khóa hoặc mở khóa đăng nhập, tạo/cập nhật đề,
tham gia thi hoặc tất cả (mặc định là `all`).
Tài khoản bị khóa đăng nhập sẽ bị đăng xuất khỏi các kết nối hiện tại.

Mã hóa kết nối dùng RSA-OAEP để trao đổi khóa phiên AES riêng cho từng client,
sau đó dùng AES-GCM và số thứ tự gói tin để bảo vệ dữ liệu/chống phát lại.
Lần đầu chạy server sẽ tạo `server_private.key` và `server_public.key` trong thư
mục chạy. Hãy chuyển `server_public.key` đến thư mục chạy của client bằng kênh
đáng tin cậy để client ghim khóa; không chuyển hoặc chia sẻ `server_private.key`.