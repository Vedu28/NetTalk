package utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileUtils {

    /**
     * Appends a message to the chat history file.
     */
    public static synchronized void saveMessageToHistory(String sender, String receiver, String content, String timestamp) {
        try (FileWriter fw = new FileWriter(Constants.CHAT_HISTORY_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            
            // Format: [10:32 PM] Ved -> ALL: Hello Everyone
            out.printf("[%s] %s -> %s: %s%n", timestamp, sender, receiver, content);
        } catch (IOException e) {
            System.err.println("Error writing to chat history: " + e.getMessage());
        }
    }

    /**
     * Reads a static web resource from the file system or falls back to reading as classpath resource.
     */
    public static byte[] readWebResource(String resourcePath) throws IOException {
        // Try file system first
        File file = new File(resourcePath);
        if (file.exists() && file.isFile()) {
            return Files.readAllBytes(file.toPath());
        }
        
        // Try classpath resource
        try (InputStream is = FileUtils.class.getResourceAsStream("/" + resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            return readAllBytes(is);
        }
    }

    /**
     * Helper to read all bytes from an InputStream (mimicking Java 9+ InputStream.readAllBytes for backward compatibility, though JDK 17 has it).
     */
    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Detects MIME type for standard web files.
     */
    public static String getMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    /**
     * Save uploaded file data.
     */
    public static void saveFile(String filename, byte[] fileData) throws IOException {
        File dir = new File(Constants.UPLOADS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dest = new File(dir, filename);
        Files.write(dest.toPath(), fileData);
    }

    /**
     * Formats file sizes in bytes to human-readable form (e.g. KB, MB).
     */
    public static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
