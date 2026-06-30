package utils;

public class Constants {
    // Default network settings
    public static final int DEFAULT_PORT = 5000;
    public static final String DEFAULT_HOST = "localhost";
    
    // Protocol handshake signatures
    public static final String SWING_CLIENT_SIGNATURE = "NETTALK_SWING";
    
    // Unit separator for message parsing (ASCII Unit Separator)
    public static final String DELIMITER = "\u001F";
    
    // Command prefixes
    public static final String CMD_CONNECT = "CONNECT";
    public static final String CMD_CONNECT_ACK = "CONNECT_ACK";
    public static final String CMD_JOINED = "JOINED";
    public static final String CMD_LEFT = "LEFT";
    public static final String CMD_USER_LIST = "USER_LIST";
    public static final String CMD_MSG = "MSG";
    public static final String CMD_TYPING = "TYPING";
    public static final String CMD_FILE_ANNOUNCE = "FILE_ANNOUNCE";
    public static final String CMD_CLEAR_CHAT = "CLEAR_CHAT";
    public static final String CMD_SYSTEM = "SYSTEM";
    
    // File upload settings
    public static final String UPLOADS_DIR = "uploads";
    public static final String CHAT_HISTORY_FILE = "chat_history.txt";
    
    // Application theme colors (Modern Dark Theme Palette)
    public static final java.awt.Color COLOR_BG = new java.awt.Color(30, 30, 30);
    public static final java.awt.Color COLOR_SIDEBAR = new java.awt.Color(45, 45, 45);
    public static final java.awt.Color COLOR_TEXT = new java.awt.Color(220, 220, 220);
    public static final java.awt.Color COLOR_TEXT_MUTED = new java.awt.Color(160, 160, 160);
    public static final java.awt.Color COLOR_ACCENT = new java.awt.Color(74, 144, 226); // Nice blue
    public static final java.awt.Color COLOR_ACCENT_HOVER = new java.awt.Color(53, 122, 202);
    public static final java.awt.Color COLOR_CARD = new java.awt.Color(60, 60, 60);
    public static final java.awt.Color COLOR_INPUT_BG = new java.awt.Color(50, 50, 50);
}
