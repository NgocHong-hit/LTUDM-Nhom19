package Server;
import common.CryptoUtil;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server_socket {
    private static final int port = 12345;
    private ServerSocket serverSocket;
    private KeyPair rsaKeyPair;
    private ExecutorService threadPool;
    private final Set<ClientHandler> onlineClients = Collections.synchronizedSet(new HashSet<>());

    public Server_socket(int port) throws Exception{
        this.rsaKeyPair = CryptoUtil.generateRSAKeyPair();
        this.serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        this.threadPool = Executors.newFixedThreadPool(20);
        System.out.println("Quiz Server đang lắng nghe tại Cổng: " + port);
    }

    public void start() {
        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client kết nối từ: " + clientSocket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(clientSocket, rsaKeyPair, this);
                onlineClients.add(handler);
                threadPool.execute(handler);
            }
        } catch (Exception e) {
            if (!serverSocket.isClosed()) e.printStackTrace();
        }
    }

    public void removeClient(ClientHandler handler) {
        onlineClients.remove(handler);
    }

    public static void main(String[] args) {
        try {
            Server_socket server = new Server_socket(port);
            server.start();
        } catch (Exception e) {
            System.err.println("Lỗi khởi động Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}