package client;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import utils.Constants;
import utils.FileUtils;

public class ClientGUI extends JFrame implements ChatClient.ChatClientListener {

    private ChatClient client;
    private String currentTarget = "ALL"; // "ALL" or username

    // Cache to separate broadcast messages from private chat histories
    private final Map<String, List<MessageNode>> chatCache = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();

    // Login components
    private JDialog loginDialog;
    private JTextField txtUsername;
    private JTextField txtServerIp;
    private JTextField txtPort;
    private JLabel lblLoginError;
    private JButton btnConnect;

    // Main window components
    private JList<String> listUsers;
    private DefaultListModel<String> userListModel;
    private JPanel panelMessages;
    private JScrollPane scrollMessages;
    private JTextField txtMessage;
    private JButton btnSend;
    private JButton btnAttach;
    private JButton btnEmoji;
    private JLabel lblTargetTitle;
    private JLabel lblTargetSub;
    private JLabel lblConnectionStatus;
    private JPanel emojiPanel;
    private JLabel lblTypingStatus;

    // Typing states
    private javax.swing.Timer typingTimer;
    private boolean isCurrentlyTyping = false;
    private Set<String> typingUsers = new HashSet<>();

    public ClientGUI() {
        chatCache.put("ALL", new ArrayList<>());
        setupLoginDialog();
    }

    private void setupLoginDialog() {
        loginDialog = new JDialog(this, "NetTalk Login", true);
        loginDialog.setResizable(false);
        loginDialog.setSize(380, 420);
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(Constants.COLOR_BG);
        loginDialog.setContentPane(panel);

        JLabel lblLogo = new JLabel("💬", SwingConstants.CENTER);
        lblLogo.setFont(new Font("Segoe UI", Font.PLAIN, 44));
        lblLogo.setBounds(0, 20, 380, 50);
        panel.add(lblLogo);

        JLabel lblTitle = new JLabel("NetTalk LAN Chat", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(Constants.COLOR_TEXT);
        lblTitle.setBounds(0, 75, 380, 30);
        panel.add(lblTitle);

        JLabel lblSub = new JLabel("Connect with coworkers on your local network", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(Constants.COLOR_TEXT_MUTED);
        lblSub.setBounds(0, 105, 380, 20);
        panel.add(lblSub);

        // Fields
        JLabel lblUser = new JLabel("USERNAME:");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblUser.setForeground(Constants.COLOR_TEXT_MUTED);
        lblUser.setBounds(40, 140, 300, 20);
        panel.add(lblUser);

        txtUsername = new JTextField("Ved");
        txtUsername.setBackground(Constants.COLOR_INPUT_BG);
        txtUsername.setForeground(Color.WHITE);
        txtUsername.setCaretColor(Color.WHITE);
        txtUsername.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Constants.COLOR_CARD, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        txtUsername.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtUsername.setBounds(40, 160, 300, 35);
        panel.add(txtUsername);

        JLabel lblIp = new JLabel("SERVER IP ADDRESS:");
        lblIp.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblIp.setForeground(Constants.COLOR_TEXT_MUTED);
        lblIp.setBounds(40, 205, 180, 20);
        panel.add(lblIp);

        txtServerIp = new JTextField(Constants.DEFAULT_HOST);
        txtServerIp.setBackground(Constants.COLOR_INPUT_BG);
        txtServerIp.setForeground(Color.WHITE);
        txtServerIp.setCaretColor(Color.WHITE);
        txtServerIp.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Constants.COLOR_CARD, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        txtServerIp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtServerIp.setBounds(40, 225, 180, 35);
        panel.add(txtServerIp);

        JLabel lblPort = new JLabel("PORT:");
        lblPort.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblPort.setForeground(Constants.COLOR_TEXT_MUTED);
        lblPort.setBounds(240, 205, 100, 20);
        panel.add(lblPort);

        txtPort = new JTextField(String.valueOf(Constants.DEFAULT_PORT));
        txtPort.setBackground(Constants.COLOR_INPUT_BG);
        txtPort.setForeground(Color.WHITE);
        txtPort.setCaretColor(Color.WHITE);
        txtPort.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Constants.COLOR_CARD, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        txtPort.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtPort.setBounds(240, 225, 100, 35);
        panel.add(txtPort);

        lblLoginError = new JLabel("", SwingConstants.CENTER);
        lblLoginError.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblLoginError.setForeground(new Color(231, 76, 60));
        lblLoginError.setBounds(40, 275, 300, 20);
        panel.add(lblLoginError);

        btnConnect = new JButton("Connect");
        btnConnect.setBackground(Constants.COLOR_ACCENT);
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnConnect.setBorder(null);
        btnConnect.setFocusPainted(false);
        btnConnect.setBounds(40, 305, 300, 45);
        btnConnect.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Add hover effects
        btnConnect.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btnConnect.isEnabled()) btnConnect.setBackground(Constants.COLOR_ACCENT_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btnConnect.isEnabled()) btnConnect.setBackground(Constants.COLOR_ACCENT);
            }
        });

        btnConnect.addActionListener(e -> attemptConnect());
        panel.add(btnConnect);

        // Allow connecting via hitting Enter in text fields
        ActionListener enterAction = e -> attemptConnect();
        txtUsername.addActionListener(enterAction);
        txtServerIp.addActionListener(enterAction);
        txtPort.addActionListener(enterAction);

        loginDialog.setVisible(true);
    }

    private void attemptConnect() {
        String username = txtUsername.getText().trim();
        String ip = txtServerIp.getText().trim();
        String portStr = txtPort.getText().trim();

        if (username.isEmpty()) {
            lblLoginError.setText("Username cannot be empty.");
            return;
        }

        if (ip.isEmpty()) {
            lblLoginError.setText("Server IP address cannot be empty.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            lblLoginError.setText("Port must be a number between 1024 and 65535.");
            return;
        }

        lblLoginError.setText("");
        btnConnect.setEnabled(false);
        btnConnect.setText("Connecting...");

        // Fire ChatClient background connection worker
        client = new ChatClient(ip, port, username, this);
        client.connect();
    }

    private void setupMainWindow() {
        setTitle("NetTalk - LAN Chatroom (" + client.getUsername() + ")");
        setSize(960, 680);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(Constants.COLOR_BG);

        // Main Layout Grid
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Constants.COLOR_BG);
        setContentPane(mainPanel);

        // 1. Sidebar (Left Panel)
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBackground(Constants.COLOR_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Constants.COLOR_CARD));

        // Profile bar
        JPanel profileBar = new JPanel(new BorderLayout(10, 0));
        profileBar.setBackground(Constants.COLOR_SIDEBAR);
        profileBar.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel lblAvatar = new JLabel(String.valueOf(client.getUsername().charAt(0)).toUpperCase(), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Constants.COLOR_ACCENT);
                g.fillOval(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        lblAvatar.setPreferredSize(new Dimension(38, 38));
        lblAvatar.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblAvatar.setForeground(Color.WHITE);
        
        JPanel profileInfo = new JPanel(new GridLayout(2, 1));
        profileInfo.setBackground(Constants.COLOR_SIDEBAR);
        
        JLabel lblMyName = new JLabel(client.getUsername());
        lblMyName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblMyName.setForeground(Color.WHITE);
        
        JLabel lblMyStatus = new JLabel("Swing Desktop Client");
        lblMyStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblMyStatus.setForeground(Constants.COLOR_TEXT_MUTED);
        
        profileInfo.add(lblMyName);
        profileInfo.add(lblMyStatus);
        
        profileBar.add(lblAvatar, BorderLayout.WEST);
        profileBar.add(profileInfo, BorderLayout.CENTER);
        sidebar.add(profileBar, BorderLayout.NORTH);

        // Online list Title
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setBackground(Constants.COLOR_SIDEBAR);
        listHeader.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        JLabel lblTitleList = new JLabel("ONLINE USERS");
        lblTitleList.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblTitleList.setForeground(Constants.COLOR_TEXT_MUTED);
        listHeader.add(lblTitleList, BorderLayout.WEST);
        sidebar.add(listHeader, BorderLayout.CENTER);

        // Online list
        userListModel = new DefaultListModel<>();
        userListModel.addElement("Broadcast Chat");
        listUsers = new JList<>(userListModel);
        listUsers.setBackground(Constants.COLOR_SIDEBAR);
        listUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listUsers.setCellRenderer(new UserListCellRenderer());
        listUsers.setSelectedIndex(0);

        listUsers.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = listUsers.getSelectedValue();
                if (selected != null) {
                    switchActiveTarget(selected);
                }
            }
        });

        JScrollPane scrollUsers = new JScrollPane(listUsers);
        scrollUsers.setBorder(null);
        scrollUsers.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebar.add(scrollUsers, BorderLayout.CENTER);
        
        mainPanel.add(sidebar, BorderLayout.WEST);

        // 2. Chat Area (Center Panel)
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(Constants.COLOR_BG);

        // Header Panel
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(Constants.COLOR_SIDEBAR);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Constants.COLOR_CARD),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        JPanel headerLeft = new JPanel(new GridLayout(2, 1));
        headerLeft.setBackground(Constants.COLOR_SIDEBAR);
        
        lblTargetTitle = new JLabel("Broadcast Chat");
        lblTargetTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTargetTitle.setForeground(Color.WHITE);
        
        lblTargetSub = new JLabel("Messages sent here are visible to everyone");
        lblTargetSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblTargetSub.setForeground(Constants.COLOR_TEXT_MUTED);
        
        headerLeft.add(lblTargetTitle);
        headerLeft.add(lblTargetSub);
        chatHeader.add(headerLeft, BorderLayout.WEST);

        lblConnectionStatus = new JLabel("Connected", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(46, 204, 113, 30));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        lblConnectionStatus.setPreferredSize(new Dimension(90, 24));
        lblConnectionStatus.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblConnectionStatus.setForeground(new Color(46, 204, 113));
        chatHeader.add(lblConnectionStatus, BorderLayout.EAST);
        chatPanel.add(chatHeader, BorderLayout.NORTH);

        // Messages scroll container
        panelMessages = new JPanel();
        panelMessages.setLayout(new BoxLayout(panelMessages, BoxLayout.Y_AXIS));
        panelMessages.setBackground(Constants.COLOR_BG);
        panelMessages.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        scrollMessages = new JScrollPane(panelMessages);
        scrollMessages.setBorder(null);
        scrollMessages.setBackground(Constants.COLOR_BG);
        scrollMessages.getVerticalScrollBar().setUnitIncrement(16);
        
        // Custom drag & drop support on the scroll view
        new FileDropTarget(scrollMessages);

        chatPanel.add(scrollMessages, BorderLayout.CENTER);

        // Bottom Controls (Input Field, Typing state)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(Constants.COLOR_BG);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 15, 20));

        lblTypingStatus = new JLabel(" ");
        lblTypingStatus.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblTypingStatus.setForeground(Constants.COLOR_TEXT_MUTED);
        lblTypingStatus.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 0));
        bottomPanel.add(lblTypingStatus, BorderLayout.NORTH);

        // Input control row
        JPanel controlRow = new JPanel(new BorderLayout(8, 0));
        controlRow.setBackground(Constants.COLOR_BG);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftActions.setBackground(Constants.COLOR_BG);

        btnEmoji = createIconButton("😊");
        btnAttach = createIconButton("📎");
        leftActions.add(btnEmoji);
        leftActions.add(Box.createHorizontalStrut(6));
        leftActions.add(btnAttach);
        controlRow.add(leftActions, BorderLayout.WEST);

        txtMessage = new JTextField();
        txtMessage.setBackground(Constants.COLOR_INPUT_BG);
        txtMessage.setForeground(Color.WHITE);
        txtMessage.setCaretColor(Color.WHITE);
        txtMessage.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtMessage.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Constants.COLOR_CARD, 1),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)));
        
        txtMessage.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                triggerTypingIndicator();
            }
        });
        
        txtMessage.addActionListener(e -> sendChatMessage());
        controlRow.add(txtMessage, BorderLayout.CENTER);

        btnSend = new JButton("Send");
        btnSend.setBackground(Constants.COLOR_ACCENT);
        btnSend.setForeground(Color.WHITE);
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSend.setFocusPainted(false);
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSend.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        btnSend.addActionListener(e -> sendChatMessage());
        controlRow.add(btnSend, BorderLayout.EAST);

        bottomPanel.add(controlRow, BorderLayout.CENTER);
        chatPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        // Create Emoji drawer hidden at bottom initially
        setupEmojiPanel(mainPanel);

        // Typing reset timer (resets isTyping state after 1.5s idle)
        typingTimer = new javax.swing.Timer(1500, e -> {
            isCurrentlyTyping = false;
            client.sendTypingState(false);
            typingTimer.stop();
        });

        // Add standard disconnect on window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (client != null) client.disconnect();
            }
        });

        setVisible(true);
        txtMessage.requestFocusInWindow();
    }

    private JButton createIconButton(String emojiText) {
        JButton btn = new JButton(emojiText);
        btn.setPreferredSize(new Dimension(38, 38));
        btn.setBackground(Constants.COLOR_INPUT_BG);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        btn.setBorder(BorderFactory.createLineBorder(Constants.COLOR_CARD, 1));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void setupEmojiPanel(JPanel container) {
        emojiPanel = new JPanel(new GridLayout(4, 8, 4, 4));
        emojiPanel.setBackground(Constants.COLOR_SIDEBAR);
        emojiPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        emojiPanel.setVisible(false);

        String[] emojis = {
            "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆",
            "😉", "😊", "😋", "😎", "😍", "😘", "🥰", "😗",
            "👍", "👎", "👏", "🙌", "👋", "🔥", "✨", "🎉",
            "❤️", "💔", "💬", "📎", "📁", "📦", "❌", "✔️"
        };

        for (String emoji : emojis) {
            JButton btn = new JButton(emoji);
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            btn.setBackground(Constants.COLOR_CARD);
            btn.setForeground(Color.WHITE);
            btn.setBorder(null);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                txtMessage.setText(txtMessage.getText() + emoji);
                txtMessage.requestFocusInWindow();
            });
            emojiPanel.add(btn);
        }

        btnEmoji.addActionListener(e -> {
            emojiPanel.setVisible(!emojiPanel.isVisible());
            container.revalidate();
            container.repaint();
        });

        btnAttach.addActionListener(e -> triggerFileUpload());
        
        container.add(emojiPanel, BorderLayout.SOUTH);
    }

    private void triggerTypingIndicator() {
        if (!isCurrentlyTyping) {
            isCurrentlyTyping = true;
            client.sendTypingState(true);
        }
        typingTimer.restart();
    }

    private void switchActiveTarget(String target) {
        if (target.contains(" (")) {
            // Strip client details if any
            target = target.substring(0, target.indexOf(" ("));
        }
        
        // Reset unread count
        String cacheKey = target.equals("Broadcast Chat") ? "ALL" : target;
        currentTarget = cacheKey;
        unreadCounts.put(cacheKey, 0);

        // Update titles
        if (currentTarget.equals("ALL")) {
            lblTargetTitle.setText("Broadcast Chat");
            lblTargetSub.setText("Messages sent here are visible to everyone");
        } else {
            lblTargetTitle.setText("Private Chat: " + currentTarget);
            lblTargetSub.setText("End-to-end local routing with " + currentTarget);
        }

        refreshUserList();
        renderMessagesFromCache();
    }

    private void renderMessagesFromCache() {
        panelMessages.removeAll();
        List<MessageNode> list = chatCache.getOrDefault(currentTarget, new ArrayList<>());
        for (MessageNode node : list) {
            addMessageToContainer(node);
        }
        panelMessages.revalidate();
        panelMessages.repaint();
        scrollToBottom();
    }

    private void addMessageToContainer(MessageNode node) {
        JPanel wrapper = new JPanel(new FlowLayout(node.isOutgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 4));
        wrapper.setBackground(Constants.COLOR_BG);
        wrapper.setMaximumSize(new Dimension(Short.MAX_VALUE, 80));

        JPanel inner = new JPanel(new BorderLayout(0, 2));
        inner.setBackground(Constants.COLOR_BG);

        // Bubble Header
        if (!node.isOutgoing && !node.isSystem) {
            JLabel lblSender = new JLabel(node.sender + "   " + node.timestamp);
            lblSender.setFont(new Font("Segoe UI", Font.BOLD, 10));
            lblSender.setForeground(Constants.COLOR_ACCENT);
            inner.add(lblSender, BorderLayout.NORTH);
        } else if (node.isOutgoing && !node.isSystem) {
            JLabel lblSender = new JLabel(node.timestamp, SwingConstants.RIGHT);
            lblSender.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            lblSender.setForeground(Constants.COLOR_TEXT_MUTED);
            inner.add(lblSender, BorderLayout.NORTH);
        }

        // Bubble Content
        if (node.isSystem) {
            JLabel lblSys = new JLabel(node.content);
            lblSys.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            lblSys.setForeground(Constants.COLOR_TEXT_MUTED);
            lblSys.setBorder(BorderFactory.createEmptyBorder(4, 15, 4, 15));
            wrapper.add(lblSys);
        } else if (node.isFile) {
            // Render file card
            JPanel fileCard = new JPanel(new BorderLayout(10, 0));
            fileCard.setBackground(Constants.COLOR_CARD);
            fileCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Constants.COLOR_CARD, 1),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));

            JLabel lblFileIcon = new JLabel(getFileIcon(node.fileName));
            lblFileIcon.setFont(new Font("Segoe UI", Font.PLAIN, 24));
            fileCard.add(lblFileIcon, BorderLayout.WEST);

            JPanel fileDetails = new JPanel(new GridLayout(2, 1));
            fileDetails.setBackground(Constants.COLOR_CARD);
            JLabel nameLabel = new JLabel(node.fileName);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            nameLabel.setForeground(Color.WHITE);
            JLabel sizeLabel = new JLabel(node.fileSize);
            sizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            sizeLabel.setForeground(Constants.COLOR_TEXT_MUTED);
            fileDetails.add(nameLabel);
            fileDetails.add(sizeLabel);
            fileCard.add(fileDetails, BorderLayout.CENTER);

            JButton btnDown = new JButton("Download");
            btnDown.setBackground(Constants.COLOR_ACCENT);
            btnDown.setForeground(Color.WHITE);
            btnDown.setFont(new Font("Segoe UI", Font.BOLD, 10));
            btnDown.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            btnDown.setFocusPainted(false);
            btnDown.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            String fileId = node.fileId;
            String fileName = node.fileName;
            btnDown.addActionListener(e -> downloadFile(fileId, fileName));
            fileCard.add(btnDown, BorderLayout.EAST);

            inner.add(fileCard, BorderLayout.CENTER);
            wrapper.add(inner);
        } else {
            // Text Bubble
            RoundedBubblePanel bubble = new RoundedBubblePanel(
                    node.isOutgoing ? Constants.COLOR_ACCENT : Constants.COLOR_INPUT_BG, 12);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            // To wrap text properly in labels, we format as HTML
            String wrappedText = "<html><body style='width: 320px; font-family: Segoe UI, sans-serif;'>" +
                    escapeHtml(node.content) + "</body></html>";
            JLabel lblText = new JLabel(wrappedText);
            lblText.setForeground(Color.WHITE);
            lblText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            bubble.add(lblText, BorderLayout.CENTER);

            inner.add(bubble, BorderLayout.CENTER);
            wrapper.add(inner);
        }

        panelMessages.add(wrapper);
        panelMessages.add(Box.createVerticalStrut(4));
    }

    private void sendChatMessage() {
        String msg = txtMessage.getText().trim();
        if (msg.isEmpty()) return;

        client.sendMessage(currentTarget, msg);
        txtMessage.setText("");
        txtMessage.requestFocusInWindow();

        // Cancel typing status immediately
        isCurrentlyTyping = false;
        client.sendTypingState(false);
        typingTimer.stop();
    }

    private void triggerFileUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select a file to send");
        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            uploadFileInBackground(file);
        }
    }

    private void uploadFileInBackground(File file) {
        appendSystemMessage("Uploading file: " + file.getName() + "...");
        
        // SwingWorker prevents UI locking during file network transmission
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return FileTransfer.uploadFile(client.getServerIp(), client.getPort(), file);
            }

            @Override
            protected void done() {
                try {
                    String fileId = get();
                    String fileSize = FileUtils.formatFileSize(file.length());
                    client.sendFileAnnouncement(currentTarget, fileId, file.getName(), fileSize);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ClientGUI.this, 
                            "Upload failed: " + e.getMessage(), 
                            "File Transfer Error", 
                            JOptionPane.ERROR_MESSAGE);
                    appendSystemMessage("File upload failed: " + file.getName());
                }
            }
        }.execute();
    }

    private void downloadFile(String fileId, String fileName) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save file...");
        chooser.setSelectedFile(new File(fileName));
        int option = chooser.showSaveDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File destination = chooser.getSelectedFile();
            
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    FileTransfer.downloadFile(client.getServerIp(), client.getPort(), fileId, destination);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(ClientGUI.this, 
                                "File saved successfully: " + destination.getName(), 
                                "Download Complete", 
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(ClientGUI.this, 
                                "Download failed: " + e.getMessage(), 
                                "File Transfer Error", 
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }

    private void appendSystemMessage(String text) {
        MessageNode node = new MessageNode();
        node.isSystem = true;
        node.content = text;
        
        chatCache.get(currentTarget).add(node);
        if (currentTarget.equals("ALL")) {
            addMessageToContainer(node);
            panelMessages.revalidate();
            panelMessages.repaint();
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollMessages.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    // Callbacks from ChatClient Listener
    @Override
    public void onConnectAck(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            btnConnect.setEnabled(true);
            btnConnect.setText("Connect");
            if (success) {
                loginDialog.dispose();
                setupMainWindow();
            } else {
                lblLoginError.setText(message);
            }
        });
    }

    @Override
    public void onMessageReceived(String sender, String receiver, String content, String timestamp) {
        SwingUtilities.invokeLater(() -> {
            boolean isOutgoing = sender.equalsIgnoreCase(client.getUsername());
            String cacheKey = "ALL";
            if (!receiver.equalsIgnoreCase("ALL")) {
                cacheKey = isOutgoing ? receiver : sender;
            }

            MessageNode node = new MessageNode();
            node.isOutgoing = isOutgoing;
            node.sender = sender;
            node.content = content;
            node.timestamp = timestamp;
            node.isSystem = false;
            node.isFile = false;

            if (!chatCache.containsKey(cacheKey)) {
                chatCache.put(cacheKey, new ArrayList<>());
            }
            chatCache.get(cacheKey).add(node);

            if (cacheKey.equals(currentTarget)) {
                addMessageToContainer(node);
                panelMessages.revalidate();
                panelMessages.repaint();
                scrollToBottom();
                playChime(true);
            } else {
                // Increment unread count
                unreadCounts.put(cacheKey, unreadCounts.getOrDefault(cacheKey, 0) + 1);
                refreshUserList();
                playChime(false);
            }
        });
    }

    @Override
    public void onFileAnnounced(String sender, String receiver, String fileId, String fileName, String fileSize, String timestamp) {
        SwingUtilities.invokeLater(() -> {
            boolean isOutgoing = sender.equalsIgnoreCase(client.getUsername());
            String cacheKey = "ALL";
            if (!receiver.equalsIgnoreCase("ALL")) {
                cacheKey = isOutgoing ? receiver : sender;
            }

            MessageNode node = new MessageNode();
            node.isOutgoing = isOutgoing;
            node.sender = sender;
            node.fileId = fileId;
            node.fileName = fileName;
            node.fileSize = fileSize;
            node.timestamp = timestamp;
            node.isSystem = false;
            node.isFile = true;

            if (!chatCache.containsKey(cacheKey)) {
                chatCache.put(cacheKey, new ArrayList<>());
            }
            chatCache.get(cacheKey).add(node);

            if (cacheKey.equals(currentTarget)) {
                addMessageToContainer(node);
                panelMessages.revalidate();
                panelMessages.repaint();
                scrollToBottom();
                playChime(true);
            } else {
                unreadCounts.put(cacheKey, unreadCounts.getOrDefault(cacheKey, 0) + 1);
                refreshUserList();
                playChime(false);
            }
        });
    }

    @Override
    public void onUserListUpdated(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            userListModel.addElement("Broadcast Chat");
            for (String user : users) {
                if (!user.equalsIgnoreCase(client.getUsername())) {
                    userListModel.addElement(user);
                    if (!chatCache.containsKey(user)) {
                        chatCache.put(user, new ArrayList<>());
                    }
                }
            }
            refreshUserList();
        });
    }

    @Override
    public void onUserJoined(String username) {
        SwingUtilities.invokeLater(() -> {
            appendSystemMessage(username + " joined the chat");
            playChime(true);
        });
    }

    @Override
    public void onUserLeft(String username) {
        SwingUtilities.invokeLater(() -> {
            appendSystemMessage(username + " left the chat");
            playChime(false);
        });
    }

    @Override
    public void onTypingState(String username, boolean isTyping) {
        SwingUtilities.invokeLater(() -> {
            if (isTyping) {
                typingUsers.add(username);
            } else {
                typingUsers.remove(username);
            }
            updateTypingLabel();
        });
    }

    private void updateTypingLabel() {
        if (typingUsers.isEmpty()) {
            lblTypingStatus.setText(" ");
        } else {
            // Display who is typing
            String labelText = String.join(", ", typingUsers) + (typingUsers.size() == 1 ? " is typing..." : " are typing...");
            lblTypingStatus.setText(labelText);
        }
    }

    @Override
    public void onSystemMessage(String content) {
        SwingUtilities.invokeLater(() -> appendSystemMessage(content));
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            lblConnectionStatus.setText("Offline");
            lblConnectionStatus.setForeground(new Color(231, 76, 60));
            lblConnectionStatus.repaint();
            JOptionPane.showMessageDialog(this, 
                    "Connection lost with Server.", 
                    "Disconnected", 
                    JOptionPane.WARNING_MESSAGE);
        });
    }

    private void refreshUserList() {
        listUsers.repaint();
    }

    // Audio synthesizer chime
    private void playChime(boolean primaryTone) {
        try {
            float sampleRate = 44100f;
            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            line.start();
            
            byte[] buf = new byte[1];
            double frequency = primaryTone ? 880.0 : 523.25; // A5 vs C5
            double duration = 0.12;

            for (int i = 0; i < sampleRate * duration; i++) {
                double angle = i / (sampleRate / frequency) * 2.0 * Math.PI;
                // Soft volume attenuation
                double volume = Math.exp(-i / (sampleRate * 0.05));
                buf[0] = (byte) (Math.sin(angle) * 35 * volume);
                line.write(buf, 0, 1);
            }
            line.drain();
            line.stop();
            line.close();
        } catch (Exception e) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // Cell Renderer for List
    class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                                                      boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout(10, 0));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
            panel.setOpaque(true);

            String user = (String) value;
            
            JLabel avatar = new JLabel(user.equals("Broadcast Chat") ? "📢" : String.valueOf(user.charAt(0)).toUpperCase(), SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(user.equals("Broadcast Chat") ? Constants.COLOR_ACCENT : new Color(70, 70, 85));
                    g.fillOval(0, 0, getWidth(), getHeight());
                    super.paintComponent(g);
                }
            };
            avatar.setPreferredSize(new Dimension(30, 30));
            avatar.setFont(new Font("Segoe UI", Font.BOLD, 12));
            avatar.setForeground(Color.WHITE);
            panel.add(avatar, BorderLayout.WEST);

            JLabel name = new JLabel(user);
            name.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            name.setForeground(Color.WHITE);
            panel.add(name, BorderLayout.CENTER);

            // Add unread badges if present
            String cacheKey = user.equals("Broadcast Chat") ? "ALL" : user;
            int count = unreadCounts.getOrDefault(cacheKey, 0);
            if (count > 0) {
                JLabel badge = new JLabel(String.valueOf(count), SwingConstants.CENTER) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        g.setColor(new Color(46, 204, 113));
                        g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                        super.paintComponent(g);
                    }
                };
                badge.setPreferredSize(new Dimension(18, 18));
                badge.setFont(new Font("Segoe UI", Font.BOLD, 9));
                badge.setForeground(Color.WHITE);
                panel.add(badge, BorderLayout.EAST);
            }

            if (isSelected) {
                panel.setBackground(new Color(74, 144, 226, 40));
            } else {
                panel.setBackground(Constants.COLOR_SIDEBAR);
            }

            return panel;
        }
    }

    // Helper classes
    static class MessageNode {
        boolean isOutgoing;
        boolean isSystem;
        boolean isFile;
        String sender;
        String content;
        String timestamp;
        
        // File details
        String fileId;
        String fileName;
        String fileSize;
    }

    private static String getFileIcon(String name) {
        String ext = name.contains(".") ? name.substring(name.lastIndexOf(".") + 1).toLowerCase() : "";
        switch (ext) {
            case "png": case "jpg": case "jpeg": case "gif": case "svg": return "🖼️";
            case "pdf": return "📕";
            case "zip": case "rar": case "7z": return "📦";
            case "doc": case "docx": return "📝";
            case "txt": return "📄";
            case "mp4": case "mkv": return "🎥";
            default: return "📁";
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    // Custom Rounded Bubble Panel
    static class RoundedBubblePanel extends JPanel {
        private final Color bgColor;
        private final int radius;

        public RoundedBubblePanel(Color bgColor, int radius) {
            this.bgColor = bgColor;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        }
    }

    // File Drag and Drop Support
    class FileDropTarget extends java.awt.dnd.DropTarget {
        public FileDropTarget(Component c) {
            c.setDropTarget(this);
        }

        @Override
        public synchronized void drop(java.awt.dnd.DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                @SuppressWarnings("unchecked")
                List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(
                        java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                if (droppedFiles != null && !droppedFiles.isEmpty()) {
                    File file = droppedFiles.get(0);
                    uploadFileInBackground(file);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public static void main(String[] args) {
        // Set cross-platform look and feel to prevent OS defaults breaking styles
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fallback
        }
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}
