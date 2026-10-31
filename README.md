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
