Tên đề tài: CHIA SẼ ĐỀ VÀ THI TRẮC NGHIỆM
Danh sách thành viên
PHAN LÊ NGỌC HỒNG - 3122410134


Lệnh quản trị tại dấu nhắc server>
help
stats
online
block <email> [all|login|create|exam]
block <email>|<loại khóa>
unblock <email> [all|login|create|exam]
unblock <email>|<loại khóa>
quit

Biên dịch lại code Server (khi sửa file trong src/Server/):
javac -encoding UTF-8 -d bin -cp "bin;lib/*" src/Server/*.java

Biên dịch lại code Client (khi sửa file giao diện/controller trong src/Client_GUI/):
javac -encoding UTF-8 --module-path "lib" --add-modules javafx.controls,javafx.fxml -d bin -cp "bin;lib/*" src/Client_GUI/Controller/*.java

Đẩy thư mục bin/ mới biên dịch lên VPS:
scp -r bin root@160.250.134.162:/opt/quiz-server/

Chạy thử ứng dụng Client trên Windows:
java --module-path "lib" --add-modules javafx.controls,javafx.fxml -Djava.library.path="lib" -cp "bin;lib/*" Client_GUI.MainApp

Tắt Server cũ (dùng trước khi chạy lại code mới):
pkill -f Server.Server_socket && screen -wipe

Bật Server ở chế độ chạy ngầm 24/7:
screen -dmS quiz bash -c "cd /opt/quiz-server && java -cp 'bin:lib/*' Server.Server_socket"

Kiểm tra trạng thái Server và cổng 12345:
screen -ls && ss -tulnp | grep 12345

Mở màn hình console của Server đang chạy ngầm (để xem log hoặc gõ lệnh stats, block, unblock):
screen -r quiz

ssh root@160.250.134.162