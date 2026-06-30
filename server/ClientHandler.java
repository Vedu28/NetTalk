package server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import model.User;
import utils.Constants;
import utils.FileUtils;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private User user;
    private boolean isWebSocket = false;
    private volatile boolean isRunning = true;
    private OutputStream out;
    private InputStream in;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public User getUser() {
        return user;
    }

    public boolean isWebSocket() {
        return isWebSocket;
    }

    @Override
    public void run() {
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();

            // Read the first line of the connection to detect protocol
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                closeConnection();
                return;
            }

            if (requestLine.startsWith("GET") || requestLine.startsWith("POST") || requestLine.startsWith("PUT")) {
                // HTTP / WebSocket client
                handleHttp(requestLine);
            } else if (requestLine.equals(Constants.SWING_CLIENT_SIGNATURE)) {
                // Swing client
                handleSwing();
            } else {
                ServerLogger.warning("Unknown client signature: " + requestLine);
                closeConnection();
            }
        } catch (IOException e) {
            ServerLogger.error("Error handling client connection: " + e.getMessage());
            closeConnection();
        }
    }

    /**
     * Reads a line of bytes until \n. This prevents buffer consumption issues
     * associated with wrapping the raw socket input stream in BufferedReader.
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

    /**
     * Processes standard HTTP requests (Web UI pages, file uploads, file downloads, WebSocket upgrades)
     */
    private void handleHttp(String requestLine) throws IOException {
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            sendHttpError(400, "Bad Request");
            return;
        }

        String method = parts[0];
        String fullPath = parts[1];

        // Parse query params
        String path = fullPath;
        String query = "";
        int qIdx = fullPath.indexOf('?');
        if (qIdx != -1) {
            path = fullPath.substring(0, qIdx);
            query = fullPath.substring(qIdx + 1);
        }

        // Read HTTP headers
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = readLine(in)) != null && !headerLine.isEmpty()) {
            int idx = headerLine.indexOf(':');
            if (idx != -1) {
                headers.put(headerLine.substring(0, idx).trim().toLowerCase(), headerLine.substring(idx + 1).trim());
            }
        }

        // 1. Check for WebSocket Upgrade
        if (path.equals("/ws") && "websocket".equalsIgnoreCase(headers.get("upgrade"))) {
            performWebSocketHandshake(headers);
            isWebSocket = true;
            runWebSocketLoop();
            return;
        }

        // 2. Handle File Uploads
        if (method.equalsIgnoreCase("POST") && path.equals("/upload")) {
            handleFileUpload(headers, query);
            return;
        }

        // 3. Handle File Downloads
        if (method.equalsIgnoreCase("GET") && path.equals("/download")) {
            handleFileDownload(query);
            return;
        }

        // 4. Serve Static Web UI Files
        if (method.equalsIgnoreCase("GET")) {
            handleStaticFile(path);
            return;
        }

        sendHttpError(405, "Method Not Allowed");
    }

    /**
     * Upgrades the HTTP socket to WebSocket using the handshake exchange
     */
    private void performWebSocketHandshake(Map<String, String> headers) throws IOException {
        String key = headers.get("sec-websocket-key");
        if (key == null) {
            sendHttpError(400, "Missing Sec-WebSocket-Key");
            throw new IOException("Missing WebSocket Key");
        }

        String acceptStr = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        try {
            byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(acceptStr.getBytes("UTF-8"));
            String acceptKey = Base64.getEncoder().encodeToString(sha1);

            String handshake = "HTTP/1.1 101 Switching Protocols\r\n" +
                               "Upgrade: websocket\r\n" +
                               "Connection: Upgrade\r\n" +
                               "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

            out.write(handshake.getBytes("UTF-8"));
            out.flush();
        } catch (Exception e) {
            sendHttpError(500, "Handshake hashing error");
            throw new IOException(e);
        }
    }

    /**
     * Handles binary payload upload via raw POST request body
     */
    private void handleFileUpload(Map<String, String> headers, String query) throws IOException {
        String lenStr = headers.get("content-length");
        int contentLength = lenStr != null ? Integer.parseInt(lenStr) : 0;

        if (contentLength <= 0) {
            sendHttpError(400, "Content-Length required");
            return;
        }

        // Read body
        byte[] body = new byte[contentLength];
        int bytesRead = 0;
        while (bytesRead < contentLength) {
            int read = in.read(body, bytesRead, contentLength - bytesRead);
            if (read == -1) break;
            bytesRead += read;
        }

        // Extract filename from query parameter "name=..."
        String filename = "file_" + System.currentTimeMillis();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                if (param.startsWith("name=")) {
                    filename = URLDecoder.decode(param.substring(5), "UTF-8");
                    break;
                }
            }
        }

        // Save file
        String fileId = System.currentTimeMillis() + "_" + filename;
        FileUtils.saveFile(fileId, body);

        // Respond with the file ID
        byte[] responseBytes = fileId.getBytes("UTF-8");
        out.write(("HTTP/1.1 200 OK\r\n" +
                   "Content-Type: text/plain\r\n" +
                   "Content-Length: " + responseBytes.length + "\r\n" +
                   "Access-Control-Allow-Origin: *\r\n" +
                   "Connection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(responseBytes);
        out.flush();
        socket.close();
    }

    /**
     * Serves file download request
     */
    private void handleFileDownload(String query) throws IOException {
        String id = null;
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                if (param.startsWith("id=")) {
                    id = URLDecoder.decode(param.substring(3), "UTF-8");
                    break;
                }
            }
        }

        if (id == null || id.isEmpty()) {
            sendHttpError(400, "Missing id parameter");
            return;
        }

        File file = new File(Constants.UPLOADS_DIR, id);
        if (!file.exists() || !file.isFile()) {
            sendHttpError(404, "File Not Found");
            return;
        }

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String originalName = id.contains("_") ? id.substring(id.indexOf("_") + 1) : id;

        out.write(("HTTP/1.1 200 OK\r\n" +
                   "Content-Type: " + FileUtils.getMimeType(id) + "\r\n" +
                   "Content-Length: " + fileBytes.length + "\r\n" +
                   "Content-Disposition: attachment; filename=\"" + originalName + "\"\r\n" +
                   "Access-Control-Allow-Origin: *\r\n" +
                   "Connection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(fileBytes);
        out.flush();
        socket.close();
    }

    /**
     * Serves local HTML/CSS/JS resources
     */
    private void handleStaticFile(String path) throws IOException {
        if (path.equals("/")) {
            path = "/index.html";
        }
        
        String resourcePath = "resources/web" + path;
        try {
            byte[] fileBytes = FileUtils.readWebResource(resourcePath);
            out.write(("HTTP/1.1 200 OK\r\n" +
                       "Content-Type: " + FileUtils.getMimeType(resourcePath) + "\r\n" +
                       "Content-Length: " + fileBytes.length + "\r\n" +
                       "Connection: close\r\n\r\n").getBytes("UTF-8"));
            out.write(fileBytes);
            out.flush();
        } catch (IOException e) {
            sendHttpError(404, "File Not Found");
        } finally {
            socket.close();
        }
    }

    private void sendHttpError(int code, String message) throws IOException {
        byte[] body = (code + " " + message).getBytes("UTF-8");
        out.write(("HTTP/1.1 " + code + " " + message + "\r\n" +
                   "Content-Type: text/plain\r\n" +
                   "Content-Length: " + body.length + "\r\n" +
                   "Connection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(body);
        out.flush();
        socket.close();
    }

    /**
     * The processing loop for a connected WebSocket client
     */
    private void runWebSocketLoop() {
        try {
            while (isRunning) {
                String rawMsg = readWebSocketMessage();
                if (rawMsg == null) {
                    break; // Client disconnected
                }
                processProtocolMessage(rawMsg);
            }
        } catch (IOException e) {
            ServerLogger.info("WebSocket client read error: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    /**
     * Reads a single incoming WebSocket text frame
     */
    private String readWebSocketMessage() throws IOException {
        int b1 = in.read();
        if (b1 == -1) return null; // EOF

        int opcode = b1 & 0x0F;
        if (opcode == 0x08) {
            return null; // Close frame
        }

        int b2 = in.read();
        if (b2 == -1) return null;

        boolean masked = (b2 & 0x80) != 0;
        long payloadLen = b2 & 0x7F;

        if (payloadLen == 126) {
            int r1 = in.read();
            int r2 = in.read();
            if (r1 == -1 || r2 == -1) return null;
            payloadLen = ((r1 & 0xFF) << 8) | (r2 & 0xFF);
        } else if (payloadLen == 127) {
            long len = 0;
            for (int i = 0; i < 8; i++) {
                int r = in.read();
                if (r == -1) return null;
                len = (len << 8) | (r & 0xFF);
            }
            payloadLen = len;
        }

        byte[] mask = new byte[4];
        if (masked) {
            for (int i = 0; i < 4; i++) {
                int r = in.read();
                if (r == -1) return null;
                mask[i] = (byte) r;
            }
        }

        byte[] payload = new byte[(int) payloadLen];
        int totalRead = 0;
        while (totalRead < payloadLen) {
            int r = in.read(payload, totalRead, (int) payloadLen - totalRead);
            if (r == -1) return null;
            totalRead += r;
        }

        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        return new String(payload, "UTF-8");
    }

    /**
     * Sends a WebSocket text frame (unmasked, server-to-client)
     */
    private synchronized void sendWebSocketMessage(String msg) throws IOException {
        byte[] payload = msg.getBytes("UTF-8");
        out.write(0x81); // FIN + Text Opcode

        if (payload.length <= 125) {
            out.write(payload.length);
        } else if (payload.length <= 65535) {
            out.write(126);
            out.write((payload.length >> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((payload.length >> (8 * i)) & 0xFF));
            }
        }
        out.write(payload);
        out.flush();
    }

    /**
     * The processing loop for a connected Swing desktop client
     */
    private void handleSwing() {
        try {
            while (isRunning) {
                String rawMsg = readLine(in);
                if (rawMsg == null) {
                    break; // Client disconnected
                }
                processProtocolMessage(rawMsg);
            }
        } catch (IOException e) {
            ServerLogger.info("Swing client read error: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private synchronized void sendSwingMessage(String msg) throws IOException {
        byte[] bytes = (msg + "\n").getBytes("UTF-8");
        out.write(bytes);
        out.flush();
    }

    /**
     * Dispatches custom actions depending on incoming network protocol frames
     */
    private void processProtocolMessage(String rawMsg) throws IOException {
        String[] tokens = rawMsg.split(Constants.DELIMITER, -1);
        if (tokens.length == 0) return;

        String cmd = tokens[0];

        switch (cmd) {
            case Constants.CMD_CONNECT:
                handleConnectCommand(tokens);
                break;

            case Constants.CMD_MSG:
                handleMsgCommand(tokens);
                break;

            case Constants.CMD_TYPING:
                handleTypingCommand(tokens);
                break;

            case Constants.CMD_FILE_ANNOUNCE:
                handleFileAnnounceCommand(tokens);
                break;

            case Constants.CMD_CLEAR_CHAT:
                handleClearChatCommand();
                break;

            default:
                ServerLogger.warning("Unknown command received: " + cmd);
        }
    }

    private void handleConnectCommand(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            sendMsg(Constants.CMD_CONNECT_ACK + Constants.DELIMITER + "ERROR" + Constants.DELIMITER + "Missing nickname");
            closeConnection();
            return;
        }

        String nickname = tokens[1].trim();
        if (nickname.isEmpty() || nickname.equalsIgnoreCase("ALL")) {
            sendMsg(Constants.CMD_CONNECT_ACK + Constants.DELIMITER + "ERROR" + Constants.DELIMITER + "Invalid nickname");
            closeConnection();
            return;
        }

        // Validate duplicates
        if (server.isNicknameTaken(nickname)) {
            sendMsg(Constants.CMD_CONNECT_ACK + Constants.DELIMITER + "ERROR" + Constants.DELIMITER + "Username is already taken");
            closeConnection();
            return;
        }

        this.user = new User(nickname, isWebSocket ? "WEB" : "SWING");
        server.registerClient(this);

        // Acknowledge connection
        sendMsg(Constants.CMD_CONNECT_ACK + Constants.DELIMITER + "OK" + Constants.DELIMITER + "Welcome to LAN Chat!");

        // Broadcast join event
        server.broadcast(Constants.CMD_JOINED + Constants.DELIMITER + nickname);
        ServerLogger.info(nickname + " (" + (isWebSocket ? "Web" : "Swing") + ") joined the chat room.");

        // Broadcast updated online list
        server.broadcastUserList();
    }

    private void handleMsgCommand(String[] tokens) {
        if (user == null || tokens.length < 3) return;

        String receiver = tokens[1];
        String content = tokens[2];
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));

        String responsePayload = Constants.CMD_MSG + Constants.DELIMITER + user.getUsername() + Constants.DELIMITER + receiver + Constants.DELIMITER + content + Constants.DELIMITER + timestamp;

        if (receiver.equalsIgnoreCase("ALL")) {
            // Public chat broadcast
            server.broadcast(responsePayload);
            // Save to chat_history.txt
            FileUtils.saveMessageToHistory(user.getUsername(), "ALL", content, timestamp);
        } else {
            // Private messaging
            server.sendPrivateMessage(user.getUsername(), receiver, responsePayload);
            FileUtils.saveMessageToHistory(user.getUsername(), receiver, "[Private] " + content, timestamp);
        }
    }

    private void handleTypingCommand(String[] tokens) {
        if (user == null || tokens.length < 2) return;
        String isTyping = tokens[1]; // "true" or "false"
        String payload = Constants.CMD_TYPING + Constants.DELIMITER + user.getUsername() + Constants.DELIMITER + isTyping;
        server.broadcastExcept(payload, this);
    }

    private void handleFileAnnounceCommand(String[] tokens) {
        if (user == null || tokens.length < 5) return;
        
        String receiver = tokens[1];
        String fileId = tokens[2];
        String fileName = tokens[3];
        String fileSize = tokens[4];
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));

        String payload = Constants.CMD_FILE_ANNOUNCE + Constants.DELIMITER + user.getUsername() + Constants.DELIMITER + receiver + Constants.DELIMITER + fileId + Constants.DELIMITER + fileName + Constants.DELIMITER + fileSize + Constants.DELIMITER + timestamp;

        if (receiver.equalsIgnoreCase("ALL")) {
            server.broadcast(payload);
            FileUtils.saveMessageToHistory(user.getUsername(), "ALL", "[Shared File: " + fileName + " (" + fileSize + ")]", timestamp);
        } else {
            server.sendPrivateMessage(user.getUsername(), receiver, payload);
            FileUtils.saveMessageToHistory(user.getUsername(), receiver, "[Private Shared File: " + fileName + " (" + fileSize + ")]", timestamp);
        }
    }

    private void handleClearChatCommand() {
        if (user == null) return;
        // User requesting to clear chat (can broadcast or handle locally, we broadcast a notify so others know or simply clear locally)
        String payload = Constants.CMD_CLEAR_CHAT + Constants.DELIMITER + user.getUsername();
        server.broadcastExcept(payload, this);
    }

    /**
     * Uniform message dispatch method (decides between WebSocket and standard socket packaging)
     */
    public void sendMsg(String rawMsg) {
        if (!isRunning) return;
        try {
            if (isWebSocket) {
                sendWebSocketMessage(rawMsg);
            } else {
                sendSwingMessage(rawMsg);
            }
        } catch (IOException e) {
            ServerLogger.warning("Error writing message to client " + (user != null ? user.getUsername() : "Unknown") + ": " + e.getMessage());
            closeConnection();
        }
    }

    /**
     * Terminate the client socket and clean up active server registration
     */
    public synchronized void closeConnection() {
        if (!isRunning) return;
        isRunning = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        server.unregisterClient(this);

        if (user != null) {
            String nickname = user.getUsername();
            server.broadcast(Constants.CMD_LEFT + Constants.DELIMITER + nickname);
            ServerLogger.info(nickname + " left the chat room.");
            server.broadcastUserList();
            user = null;
        }
    }
}
