// NetTalk Web Chat Engine

let socket = null;
let nickname = "";
let isConnected = false;
let activeTarget = "ALL"; // "ALL" or username for private chat
let onlineUsers = [];
let typingTimeout = null;
let isTyping = false;

// Store private messages locally to render on filter switch
// Format: { "ALL": [msgNode...], "Ved": [msgNode...] }
const chatStorage = { "ALL": [] };
const unreadCounts = {};

// DOM Elements
const loginScreen = document.getElementById("login-screen");
const appContainer = document.getElementById("app-container");
const nicknameInput = document.getElementById("nickname");
const btnConnect = document.getElementById("btn-connect");
const loginError = document.getElementById("login-error");

const statusDot = document.getElementById("status-dot");
const statusLabel = document.getElementById("status-label");
const myUsernameDisplay = document.getElementById("my-username");
const myAvatarDisplay = document.getElementById("my-avatar");
const userCountDisplay = document.getElementById("user-count");
const usersList = document.getElementById("users-list");

const chatTargetTitle = document.getElementById("chat-target-title");
const chatTargetSub = document.getElementById("chat-target-sub");
const messagesContainer = document.getElementById("messages-container");
const typingIndicator = document.getElementById("typing-indicator");
const typingText = document.getElementById("typing-text");

const btnClear = document.getElementById("btn-clear");
const btnLogout = document.getElementById("btn-logout");
const btnEmoji = document.getElementById("btn-emoji");
const emojiDrawer = document.getElementById("emoji-drawer");
const btnAttach = document.getElementById("btn-attach");
const fileInput = document.getElementById("file-input");
const messageInput = document.getElementById("message-input");
const btnSend = document.getElementById("btn-send");
const dragDropOverlay = document.getElementById("drag-drop-overlay");

// Constants (Protocol matches backend)
const DELIMITER = "\u001F";
const CMD_CONNECT = "CONNECT";
const Constants = {
    CMD_CONNECT_ACK: "CONNECT_ACK",
    CMD_JOINED: "JOINED",
    CMD_LEFT: "LEFT",
    CMD_USER_LIST: "USER_LIST",
    CMD_MSG: "MSG",
    CMD_TYPING: "TYPING",
    CMD_FILE_ANNOUNCE: "FILE_ANNOUNCE",
    CMD_CLEAR_CHAT: "CLEAR_CHAT",
    CMD_SYSTEM: "SYSTEM"
};

// Event Listeners
btnConnect.addEventListener("click", connectToServer);
nicknameInput.addEventListener("keypress", (e) => {
    if (e.key === "Enter") connectToServer();
});

btnSend.addEventListener("click", sendMessage);
messageInput.addEventListener("keypress", (e) => {
    if (e.key === "Enter") {
        sendMessage();
    } else {
        broadcastTypingState();
    }
});

btnEmoji.addEventListener("click", (e) => {
    e.stopPropagation();
    emojiDrawer.classList.toggle("hidden");
});

document.addEventListener("click", () => {
    emojiDrawer.classList.add("hidden");
});

emojiDrawer.addEventListener("click", (e) => {
    e.stopPropagation();
});

// Click emoji insertion
document.querySelectorAll(".emoji-list span").forEach(emojiSpan => {
    emojiSpan.addEventListener("click", () => {
        messageInput.value += emojiSpan.textContent;
        messageInput.focus();
    });
});

// File upload triggers
btnAttach.addEventListener("click", () => fileInput.click());
fileInput.addEventListener("change", handleFileSelected);

// Header actions
btnClear.addEventListener("click", clearChatScreen);
btnLogout.addEventListener("click", logout);

// Drag and drop events
window.addEventListener("dragenter", (e) => {
    e.preventDefault();
    dragDropOverlay.classList.remove("hidden");
});

dragDropOverlay.addEventListener("dragover", (e) => {
    e.preventDefault();
});

dragDropOverlay.addEventListener("dragleave", (e) => {
    e.preventDefault();
    dragDropOverlay.classList.add("hidden");
});

dragDropOverlay.addEventListener("drop", (e) => {
    e.preventDefault();
    dragDropOverlay.classList.add("hidden");
    if (e.dataTransfer.files.length > 0) {
        uploadFile(e.dataTransfer.files[0]);
    }
});

// Core Network Connection
function connectToServer() {
    const nick = nicknameInput.value.trim();
    if (!nick) {
        showError("Please enter a username.");
        return;
    }
    
    nickname = nick;
    loginError.textContent = "";
    btnConnect.disabled = true;
    btnConnect.textContent = "Connecting...";

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/ws`;

    socket = new WebSocket(wsUrl);

    socket.onopen = () => {
        // Send connect command
        sendRaw(CMD_CONNECT + DELIMITER + nickname);
    };

    socket.onmessage = (event) => {
        handleIncomingMessage(event.data);
    };

    socket.onerror = (err) => {
        console.error("WebSocket connection error:", err);
        showError("Unable to connect to the server.");
        resetLoginButton();
    };

    socket.onclose = () => {
        handleDisconnect();
    };
}

function sendRaw(msg) {
    if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(msg);
    }
}

function handleIncomingMessage(raw) {
    const tokens = raw.split(DELIMITER);
    if (tokens.length === 0) return;

    const cmd = tokens[0];

    switch (cmd) {
        case Constants.CMD_CONNECT_ACK:
            const status = tokens[1];
            const message = tokens[2];
            if (status === "OK") {
                loginSuccess();
            } else {
                showError(message);
                resetLoginButton();
                socket.close();
            }
            break;

        case Constants.CMD_USER_LIST:
            updateUserList(tokens[1]);
            break;

        case Constants.CMD_JOINED:
            const joinedUser = tokens[1];
            appendSystemMessage(`${joinedUser} joined the chat`);
            playChime(true);
            break;

        case Constants.CMD_LEFT:
            const leftUser = tokens[1];
            appendSystemMessage(`${leftUser} left the chat`);
            playChime(false);
            break;

        case Constants.CMD_MSG:
            processIncomingMsg(tokens[1], tokens[2], tokens[3], tokens[4]);
            break;

        case Constants.CMD_FILE_ANNOUNCE:
            processIncomingFile(tokens[1], tokens[2], tokens[3], tokens[4], tokens[5], tokens[6]);
            break;

        case Constants.CMD_TYPING:
            processTypingIndicator(tokens[1], tokens[2]);
            break;

        case Constants.CMD_CLEAR_CHAT:
            const clearer = tokens[1];
            appendSystemMessage(`${clearer} cleared their screen`);
            break;

        case Constants.CMD_SYSTEM:
            appendSystemMessage(tokens[1]);
            break;
    }
}

// Login flows
function loginSuccess() {
    isConnected = true;
    loginScreen.classList.add("hidden");
    appContainer.classList.remove("hidden");
    
    myUsernameDisplay.textContent = nickname;
    myAvatarDisplay.textContent = nickname.charAt(0);
    
    statusDot.className = "status-dot online";
    statusLabel.textContent = "Connected";

    chatStorage["ALL"] = [];
    unreadCounts["ALL"] = 0;
    renderMessages();
}

function logout() {
    if (socket) {
        socket.close();
    }
}

function handleDisconnect() {
    isConnected = false;
    statusDot.className = "status-dot offline";
    statusLabel.textContent = "Offline (Reconnecting)";
    
    // Switch to login UI if closed unexpectedly
    if (loginScreen.classList.contains("hidden")) {
        // Reconnect loop
        setTimeout(() => {
            if (!isConnected) {
                console.log("Attempting auto-reconnection...");
                connectToServer();
            }
        }, 3000);
    } else {
        resetLoginButton();
    }
}

function showError(msg) {
    loginError.textContent = msg;
}

function resetLoginButton() {
    btnConnect.disabled = false;
    btnConnect.textContent = "Connect to Chatroom";
}

// User List UI updates
function updateUserList(rawUsers) {
    if (!rawUsers) {
        onlineUsers = [];
    } else {
        onlineUsers = rawUsers.split(",").filter(u => u.toLowerCase() !== nickname.toLowerCase());
    }

    userCountDisplay.textContent = onlineUsers.length + 1; // plus self

    // Clear old list but preserve "Broadcast Chat" at the top
    usersList.innerHTML = "";

    // Broadcast Chat row
    const broadcastLi = document.createElement("li");
    broadcastLi.className = `user-item ${activeTarget === "ALL" ? "active" : ""}`;
    broadcastLi.innerHTML = `
        <div class="user-item-left">
            <div class="user-item-avatar" style="background-color: var(--accent-color); color: white;">📢</div>
            <span class="user-item-name">Broadcast Chat</span>
        </div>
    `;
    broadcastLi.onclick = () => switchChatTarget("ALL");
    usersList.appendChild(broadcastLi);

    // Dynamic users
    onlineUsers.forEach(user => {
        if (!chatStorage[user]) {
            chatStorage[user] = [];
            unreadCounts[user] = 0;
        }

        const userLi = document.createElement("li");
        userLi.className = `user-item ${activeTarget === user ? "active" : ""}`;
        
        let badgeHtml = "";
        if (unreadCounts[user] > 0) {
            badgeHtml = `<span class="user-item-badge">${unreadCounts[user]}</span>`;
        }

        userLi.innerHTML = `
            <div class="user-item-left">
                <div class="user-item-avatar">${user.charAt(0)}</div>
                <span class="user-item-name">${user}</span>
            </div>
            ${badgeHtml}
        `;
        userLi.onclick = () => switchChatTarget(user);
        usersList.appendChild(userLi);
    });
}

function switchChatTarget(target) {
    activeTarget = target;
    unreadCounts[target] = 0;
    
    // Update headers
    if (target === "ALL") {
        chatTargetTitle.textContent = "Broadcast Chat";
        chatTargetSub.textContent = "Messages sent here are visible to everyone";
    } else {
        chatTargetTitle.textContent = `Private Chat: ${target}`;
        chatTargetSub.textContent = `End-to-end local routing with ${target}`;
    }

    // Refresh active state in list
    updateUserList(onlineUsers.join(","));
    renderMessages();
}

// Message Send & Append Actions
function sendMessage() {
    const text = messageInput.value.trim();
    if (!text) return;

    // Send payload: MSG | receiver | content
    sendRaw(Constants.CMD_MSG + DELIMITER + activeTarget + DELIMITER + text);
    messageInput.value = "";
    messageInput.focus();

    // Reset typing
    isTyping = false;
    sendRaw(Constants.CMD_TYPING + DELIMITER + "false");
}

function processIncomingMsg(sender, receiver, content, timestamp) {
    const isOutgoing = sender.toLowerCase() === nickname.toLowerCase();
    
    // Determine which queue to store this message in
    // If it's a broadcast, save to ALL.
    // If it's private, save to the peer's username
    let queue = "ALL";
    if (receiver !== "ALL") {
        queue = isOutgoing ? receiver : sender;
    }

    const msgNode = {
        type: 'text',
        sender: sender,
        content: content,
        timestamp: timestamp,
        isOutgoing: isOutgoing
    };

    if (!chatStorage[queue]) {
        chatStorage[queue] = [];
    }
    chatStorage[queue].push(msgNode);

    // Notification updates
    if (queue !== activeTarget) {
        unreadCounts[queue] = (unreadCounts[queue] || 0) + 1;
        updateUserList(onlineUsers.join(","));
        playChime(false);
    } else {
        // Append dynamically if currently viewing
        appendMessageUI(msgNode);
        playChime(true);
    }
}

function appendMessageUI(msg) {
    const isSystem = msg.type === 'system';
    
    const node = document.createElement("div");
    node.className = `message-node ${isSystem ? 'system' : (msg.isOutgoing ? 'outgoing' : 'incoming')}`;

    if (isSystem) {
        node.innerHTML = `<div class="message-bubble">${msg.content}</div>`;
    } else {
        node.innerHTML = `
            <div class="message-meta">
                <span class="message-sender">${msg.sender}</span>
                <span class="message-time">${msg.timestamp}</span>
            </div>
            <div class="message-bubble">${msg.content}</div>
        `;
    }

    messagesContainer.appendChild(node);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function appendSystemMessage(content) {
    const msgNode = {
        type: 'system',
        content: content
    };
    chatStorage[activeTarget].push(msgNode);
    appendMessageUI(msgNode);
}

function renderMessages() {
    messagesContainer.innerHTML = "";
    const list = chatStorage[activeTarget] || [];
    list.forEach(msg => appendMessageUI(msg));
}

function clearChatScreen() {
    chatStorage[activeTarget] = [];
    renderMessages();
    sendRaw(Constants.CMD_CLEAR_CHAT);
}

// File uploads and handling
function handleFileSelected(event) {
    const files = event.target.files;
    if (files.length > 0) {
        uploadFile(files[0]);
    }
}

function uploadFile(file) {
    if (!isConnected) return;
    
    appendSystemMessage(`Uploading: ${file.name}...`);
    
    // Perform standard HTTP POST file stream upload
    fetch(`/upload?name=${encodeURIComponent(file.name)}`, {
        method: 'POST',
        body: file
    })
    .then(resp => {
        if (!resp.ok) throw new Error("Server upload rejected file");
        return resp.text();
    })
    .then(fileId => {
        // Send file announcement command over WebSocket
        const formattedSize = formatBytes(file.size);
        sendRaw(Constants.CMD_FILE_ANNOUNCE + DELIMITER + activeTarget + DELIMITER + fileId + DELIMITER + file.name + DELIMITER + formattedSize);
    })
    .catch(err => {
        console.error(err);
        appendSystemMessage(`Failed to upload file: ${file.name}`);
    });
}

function processIncomingFile(sender, receiver, fileId, fileName, fileSize, timestamp) {
    const isOutgoing = sender.toLowerCase() === nickname.toLowerCase();
    let queue = "ALL";
    if (receiver !== "ALL") {
        queue = isOutgoing ? receiver : sender;
    }

    const downloadUrl = `/download?id=${encodeURIComponent(fileId)}`;
    
    const cardHtml = `
        <div class="file-card">
            <span class="file-icon">${getFileIcon(fileName)}</span>
            <div class="file-info">
                <span class="file-name">${fileName}</span>
                <span class="file-size">${fileSize}</span>
            </div>
            <a href="${downloadUrl}" class="btn-download" target="_blank" download="${fileName}">Download</a>
        </div>
    `;

    const msgNode = {
        type: 'text',
        sender: sender,
        content: cardHtml,
        timestamp: timestamp,
        isOutgoing: isOutgoing
    };

    if (!chatStorage[queue]) chatStorage[queue] = [];
    chatStorage[queue].push(msgNode);

    if (queue !== activeTarget) {
        unreadCounts[queue] = (unreadCounts[queue] || 0) + 1;
        updateUserList(onlineUsers.join(","));
        playChime(false);
    } else {
        appendMessageUI(msgNode);
        playChime(true);
    }
}

// Helpers
function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

function getFileIcon(name) {
    const ext = name.split('.').pop().toLowerCase();
    switch (ext) {
        case 'png': case 'jpg': case 'jpeg': case 'gif': case 'svg': return '🖼️';
        case 'pdf': return '📕';
        case 'zip': case 'rar': case '7z': case 'tar': return '📦';
        case 'doc': case 'docx': return '📝';
        case 'txt': return '📄';
        case 'mp4': case 'mkv': case 'avi': return '🎥';
        default: return '📁';
    }
}

// Typing indicators
function broadcastTypingState() {
    if (!isTyping) {
        isTyping = true;
        sendRaw(Constants.CMD_TYPING + DELIMITER + "true");
    }
    
    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => {
        isTyping = false;
        sendRaw(Constants.CMD_TYPING + DELIMITER + "false");
    }, 1500);
}

function processTypingIndicator(typer, isTypingStr) {
    // Only show if typer matches the target we are currently viewing
    const isTarget = activeTarget === "ALL" || activeTarget.toLowerCase() === typer.toLowerCase();
    
    if (isTypingStr === "true" && isTarget) {
        typingText.textContent = `${typer} is typing...`;
        typingIndicator.classList.remove("hidden");
    } else {
        typingIndicator.classList.add("hidden");
    }
}

// Synthetic audio chime (Zero network dependencies)
function playChime(isPrimary) {
    try {
        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        
        const playNote = (freq, startTime, duration) => {
            const osc = audioCtx.createOscillator();
            const gain = audioCtx.createGain();
            
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            
            osc.type = 'sine';
            osc.frequency.setValueAtTime(freq, startTime);
            
            gain.gain.setValueAtTime(0.08, startTime);
            gain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration);
            
            osc.start(startTime);
            osc.stop(startTime + duration);
        };
        
        const now = audioCtx.currentTime;
        if (isPrimary) {
            // Light incoming msg ping (D5 -> A5)
            playNote(587.33, now, 0.12);
            playNote(880.00, now + 0.06, 0.2);
        } else {
            // Low background alert ping (C5 -> E5)
            playNote(523.25, now, 0.12);
            playNote(659.25, now + 0.06, 0.2);
        }
    } catch (e) {
        // Fallback for browser blocking policies
    }
}
