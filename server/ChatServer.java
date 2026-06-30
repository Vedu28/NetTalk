package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import utils.Constants;

public class ChatServer {
    private final int port;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;
    private volatile boolean isRunning = true;

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        ServerLogger.info("Starting NetTalk Unified Server...");
        
        // Ensure uploads directory exists
        File uploadsDir = new File(Constants.UPLOADS_DIR);
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }

        try {
            serverSocket = new ServerSocket(port);
            ServerLogger.info("Server started successfully. Listening on Port: " + port);
            ServerLogger.info("Web interface available at: http://localhost:" + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    // Start handler in a new thread
                    new Thread(handler).start();
                } catch (SocketException e) {
                    if (!isRunning) {
                        break;
                    }
                    ServerLogger.error("Socket error accepting client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            ServerLogger.error("Failed to start server on port " + port, e);
        } finally {
            shutdown();
        }
    }

    public synchronized boolean isNicknameTaken(String nickname) {
        for (ClientHandler client : clients) {
            if (client.getUser() != null && client.getUser().getUsername().equalsIgnoreCase(nickname)) {
                return true;
            }
        }
        return false;
    }

    public void registerClient(ClientHandler client) {
        clients.add(client);
    }

    public void unregisterClient(ClientHandler client) {
        clients.remove(client);
    }

    /**
     * Broadcasts a raw network protocol string to all authenticated clients.
     */
    public void broadcast(String rawMsg) {
        for (ClientHandler client : clients) {
            if (client.getUser() != null) {
                client.sendMsg(rawMsg);
            }
        }
    }

    /**
     * Broadcasts a raw network protocol string to all authenticated clients except one.
     */
    public void broadcastExcept(String rawMsg, ClientHandler except) {
        for (ClientHandler client : clients) {
            if (client.getUser() != null && client != except) {
                client.sendMsg(rawMsg);
            }
        }
    }

    /**
     * Routes a private network protocol message between two users.
     * Also sends it back to the sender so their GUI remains in sync.
     */
    public void sendPrivateMessage(String sender, String receiver, String rawMsg) {
        boolean delivered = false;
        for (ClientHandler client : clients) {
            if (client.getUser() != null) {
                String username = client.getUser().getUsername();
                if (username.equalsIgnoreCase(receiver)) {
                    client.sendMsg(rawMsg);
                    delivered = true;
                } else if (username.equalsIgnoreCase(sender)) {
                    client.sendMsg(rawMsg);
                }
            }
        }
        
        if (!delivered) {
            // Notify sender that target user is offline
            for (ClientHandler client : clients) {
                if (client.getUser() != null && client.getUser().getUsername().equalsIgnoreCase(sender)) {
                    String errorPayload = Constants.CMD_SYSTEM + Constants.DELIMITER + "User '" + receiver + "' is currently offline.";
                    client.sendMsg(errorPayload);
                    break;
                }
            }
        }
    }

    /**
     * Broadcasts the updated comma-separated list of all connected users to everyone.
     */
    public void broadcastUserList() {
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.CMD_USER_LIST).append(Constants.DELIMITER);
        
        List<String> usernames = new ArrayList<>();
        for (ClientHandler client : clients) {
            if (client.getUser() != null) {
                usernames.add(client.getUser().getUsername());
            }
        }
        
        // Join username elements by comma
        sb.append(String.join(",", usernames));
        broadcast(sb.toString());
    }

    public void shutdown() {
        ServerLogger.info("Shutting down Server...");
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        
        // Close all clients
        for (ClientHandler client : clients) {
            client.closeConnection();
        }
        clients.clear();
    }

    public static void main(String[] args) {
        int port = Constants.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number specified. Using default: " + port);
            }
        }
        
        ChatServer server = new ChatServer(port);
        // Hook shutdown listener for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.start();
    }
}
