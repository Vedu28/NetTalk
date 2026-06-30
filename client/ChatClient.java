package client;

import java.io.*;
import java.net.*;
import java.util.*;
import utils.Constants;

public class ChatClient {
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private String serverIp;
    private int port;
    private String username;
    private ChatClientListener listener;
    private volatile boolean isRunning = false;
    private Thread readThread;

    public interface ChatClientListener {
        void onConnectAck(boolean success, String message);
        void onMessageReceived(String sender, String receiver, String content, String timestamp);
        void onFileAnnounced(String sender, String receiver, String fileId, String fileName, String fileSize, String timestamp);
        void onUserListUpdated(List<String> users);
        void onUserJoined(String username);
        void onUserLeft(String username);
        void onTypingState(String username, boolean isTyping);
        void onSystemMessage(String content);
        void onDisconnected();
    }

    public ChatClient(String serverIp, int port, String username, ChatClientListener listener) {
        this.serverIp = serverIp;
        this.port = port;
        this.username = username;
        this.listener = listener;
    }

    public String getUsername() {
        return username;
    }

    public String getServerIp() {
        return serverIp;
    }

    public int getPort() {
        return port;
    }

    public boolean isConnected() {
        return isRunning;
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, port), 5000);
                in = socket.getInputStream();
                out = socket.getOutputStream();
                isRunning = true;

                // 1. Send Swing Handshake Header
                sendRaw(Constants.SWING_CLIENT_SIGNATURE);

                // 2. Send Connect Command
                sendRaw(Constants.CMD_CONNECT + Constants.DELIMITER + username);

                // 3. Start Reading Thread
                readThread = new Thread(this::readLoop);
                readThread.start();
            } catch (Exception e) {
                isRunning = false;
                if (listener != null) {
                    listener.onConnectAck(false, "Connection failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void readLoop() {
        try {
            while (isRunning) {
                String line = readLine(in);
                if (line == null) {
                    break; // EOF
                }
                parseProtocolMessage(line);
            }
        } catch (IOException e) {
            // Socket error or closed
        } finally {
            disconnect();
        }
    }

    /**
     * Reads a line of bytes until \n. Matches server implementation.
     */
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                baos.write(b);
            }
        }
        if (baos.size() == 0 && b == -1) {
            return null;
        }
        return baos.toString("UTF-8");
    }

    private void parseProtocolMessage(String rawMsg) {
        String[] tokens = rawMsg.split(Constants.DELIMITER, -1);
        if (tokens.length == 0 || listener == null) return;

        String cmd = tokens[0];

        switch (cmd) {
            case Constants.CMD_CONNECT_ACK:
                boolean ok = "OK".equalsIgnoreCase(tokens[1]);
                String msg = tokens[2];
                listener.onConnectAck(ok, msg);
                break;

            case Constants.CMD_USER_LIST:
                List<String> list = new ArrayList<>();
                if (tokens.length > 1 && !tokens[1].isEmpty()) {
                    list = Arrays.asList(tokens[1].split(","));
                }
                listener.onUserListUpdated(list);
                break;

            case Constants.CMD_JOINED:
                listener.onUserJoined(tokens[1]);
                break;

            case Constants.CMD_LEFT:
                listener.onUserLeft(tokens[1]);
                break;

            case Constants.CMD_MSG:
                listener.onMessageReceived(tokens[1], tokens[2], tokens[3], tokens[4]);
                break;

            case Constants.CMD_FILE_ANNOUNCE:
                listener.onFileAnnounced(tokens[1], tokens[2], tokens[3], tokens[4], tokens[5], tokens[6]);
                break;

            case Constants.CMD_TYPING:
                listener.onTypingState(tokens[1], "true".equalsIgnoreCase(tokens[2]));
                break;

            case Constants.CMD_SYSTEM:
                listener.onSystemMessage(tokens[1]);
                break;
        }
    }

    public synchronized void sendRaw(String msg) {
        if (socket == null || socket.isClosed()) return;
        try {
            byte[] bytes = (msg + "\n").getBytes("UTF-8");
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            disconnect();
        }
    }

    public void sendMessage(String receiver, String content) {
        sendRaw(Constants.CMD_MSG + Constants.DELIMITER + receiver + Constants.DELIMITER + content);
    }

    public void sendTypingState(boolean isTyping) {
        sendRaw(Constants.CMD_TYPING + Constants.DELIMITER + (isTyping ? "true" : "false"));
    }

    public void sendFileAnnouncement(String receiver, String fileId, String fileName, String fileSize) {
        sendRaw(Constants.CMD_FILE_ANNOUNCE + Constants.DELIMITER + receiver + Constants.DELIMITER + fileId + Constants.DELIMITER + fileName + Constants.DELIMITER + fileSize);
    }

    public void sendClearChatNotification() {
        sendRaw(Constants.CMD_CLEAR_CHAT);
    }

    public synchronized void disconnect() {
        if (!isRunning) return;
        isRunning = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        if (listener != null) {
            listener.onDisconnected();
        }
    }
}
