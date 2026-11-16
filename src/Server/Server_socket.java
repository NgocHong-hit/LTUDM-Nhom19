package Server;

import common.CryptoUtil;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import Server.db.QuizDAO;
import Server.db.UserDAO;

public class Server_socket {
    private static final int DEFAULT_PORT = 12345;
    private final ServerSocket serverSocket;
    private final KeyPair rsaKeyPair;
    private final ExecutorService threadPool;
    private final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public Server_socket(int port) throws Exception {
        // [TRAO ĐỔI KHÓA] Giữ khóa RSA ổn định để client ghim đúng khóa công khai của Server.
        rsaKeyPair = CryptoUtil.loadOrCreateRSAKeyPair("server_public.key", "server_private.key");
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        threadPool = Executors.newFixedThreadPool(20);
        System.out.println("Server đang lắng nghe tại cổng: " + port);
    }

    public void start() {
        startConsoleCommands();
        try {
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                System.out.println("Client kết nối từ: " + socket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(socket, rsaKeyPair, this);
                clients.add(handler);
                threadPool.execute(handler);
            }
        } catch (Exception e) {
            if (!serverSocket.isClosed()) {
                System.err.println("Lỗi nhận kết nối client: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    public int getOnlineUserCount() {
        return getOnlineUsers().size();
    }

    // Chỉ đếm email đã đăng nhập thành công, không tính socket chưa xác thực.
    public List<String> getOnlineUsers() {
        Map<String, String> onlineUsers = new LinkedHashMap<>();
        synchronized (clients) {
            for (ClientHandler handler : clients) {
                String email = handler.getAuthenticatedEmail();
                if (email != null) {
                    onlineUsers.putIfAbsent(email.toLowerCase(Locale.ROOT), email);
                }
            }
        }
        return new ArrayList<>(onlineUsers.values());
    }

    public void deauthenticateUser(String email) {
        synchronized (clients) {
            for (ClientHandler handler : clients) {
                if (handler.isAuthenticatedAs(email)) {
                    handler.clearAuthentication();
                }
            }
        }
    }

    private void startConsoleCommands() {
        // Dùng luồng riêng để vừa nhận lệnh quản trị vừa tiếp tục accept client.
        Thread consoleThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            printConsoleHelp();
            while (!serverSocket.isClosed()) {
                System.out.print("server> ");
                if (!scanner.hasNextLine()) {
                    return;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                executeConsoleCommand(line);
            }
        }, "server-terminal-console");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void executeConsoleCommand(String line) {
        String[] parts = line.split("\\s+");
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "help":
                printConsoleHelp();
                break;
            case "online":
                printOnlineUsers();
                break;
            case "stats":
                printStatistics();
                break;
            case "block":
                updateBlock(parts, true);
                break;
            case "unblock":
                updateBlock(parts, false);
                break;
            case "quit":
                stop();
                break;
            default:
                System.out.println("Lệnh không hợp lệ. Gõ help để xem cách dùng.");
        }
    }

    private void printOnlineUsers() {
        List<String> users = getOnlineUsers();
        System.out.println("Số người dùng đang online: " + users.size());
        for (String email : users) {
            System.out.println(" - " + email);
        }
    }

    private void printStatistics() {
        try {
            System.out.println("Tổng số người dùng: " + UserDAO.getTotalUserCount());
            System.out.println("Số người dùng đang online: " + getOnlineUserCount());
            System.out.println("Tổng số bộ đề: " + QuizDAO.getTotalQuizCount());
            System.out.printf("Điểm cao nhất: %.2f%n", QuizDAO.getHighestSubmittedScore());
            System.out.printf("Điểm thấp nhất: %.2f%n", QuizDAO.getLowestSubmittedScore());
        } catch (SQLException e) {
            System.err.println("Không thể đọc thống kê từ database: " + e.getMessage());
        }
    }

    private void updateBlock(String[] parts, boolean blocked) {
        if (parts.length < 2 || parts.length > 3) {
            printBlockUsage(blocked);
            return;
        }

        String email = parts[1];
        String type = parts.length == 3 ? parts[2].toLowerCase(Locale.ROOT) : "all";
        if (parts.length == 2 && email.contains("|")) {
            String[] target = email.split("\\|", -1);
            if (target.length != 2 || target[0].trim().isEmpty() || target[1].trim().isEmpty()) {
                printBlockUsage(blocked);
                return;
            }
            email = target[0].trim();
            type = target[1].trim().toLowerCase(Locale.ROOT);
        } else if (email.contains("|")) {
            printBlockUsage(blocked);
            return;
        }

        try {
            if (!UserDAO.setBlockOption(email, type, blocked)) {
                System.out.println("Không tìm thấy người dùng: " + email);
                return;
            }
            // Khóa đăng nhập phải vô hiệu phiên đang mở, tránh user tiếp tục dùng socket cũ.
            if (blocked && ("all".equals(type) || "login".equals(type))) {
                deauthenticateUser(email);
            }
            System.out.println("Đã " + (blocked ? "khóa " : "mở khóa ") + type + " cho " + email + ".");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.err.println("Không thể cập nhật trạng thái khóa: " + e.getMessage());
        }
    }

    private void printBlockUsage(boolean blocked) {
        String command = blocked ? "block" : "unblock";
        System.out.println("Cú pháp: " + command
                + " <email> [all|login|create|exam] hoặc " + command + " <email>|<loại khóa>");
    }

    private void printConsoleHelp() {
        System.out.println("Lệnh quản trị server:");
        System.out.println("  stats");
        System.out.println("  online");
        System.out.println("  block <email> [all|login|create|exam]");
        System.out.println("  block <email>|<loại khóa>");
        System.out.println("  unblock <email> [all|login|create|exam]");
        System.out.println("  unblock <email>|<loại khóa>");
        System.out.println("  help");
        System.out.println("  quit");
    }

    private void stop() {
        if (serverSocket.isClosed()) {
            return;
        }
        try {
            serverSocket.close();
        } catch (Exception e) {
            System.err.println("Không thể đóng server socket: " + e.getMessage());
        }
        synchronized (clients) {
            for (ClientHandler handler : clients) {
                handler.closeConnection();
            }
            clients.clear();
        }
        threadPool.shutdownNow();
        System.out.println("Server đã dừng.");
    }

    public static void main(String[] args) {
        try {
            int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
            new Server_socket(port).start();
        } catch (Exception e) {
            System.err.println("Lỗi khởi động server: " + e.getMessage());
        }
    }
}
