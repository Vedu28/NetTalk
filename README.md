# NetTalk - LAN Chat Application

NetTalk is a high-performance, production-quality LAN Chat Application that enables real-time collaboration across multiple users connected to the same local area network (LAN). It features a unified, multi-threaded Java server that handles **both** a custom Swing desktop client protocol and a modern Web client (via WebSockets) on a single network port.

---

## 🚀 Features

- **Unified Single-Port Server**: Listens on one port (default `5000`) and serves static Web assets, handles WebSocket frames for browser clients, processes HTTP uploads/downloads, and handles custom TCP streams for Swing clients.
- **Modern Swing Desktop GUI**:
  - Dark mode color theme with custom flat UI aesthetics.
  - Interactive online user panel and user avatars.
  - Real-time text messaging with enter-to-send support.
  - Private messaging (end-to-end local routing) with notifications and unread badges.
  - Drag-and-drop file upload directly onto the chat window.
  - System logs and connection failure error handling dialogs.
  - Synthetic chime sound effects for incoming events.
- **Responsive Web UI**:
  - Glassmorphic interface with full mobile layout capability.
  - Web Audio API synthetic notification chime.
  - File transfers with dynamic HTML5 drag-and-drop support.
  - Emoji drawer and typing indicator broadcasts.
- **Shared File Transmissions**: Full support for sharing large documents (`.pdf`, `.zip`, `.docx`, `.txt`, `.mp4`, images) between Web and Swing clients, handled using asynchronous background stream upload/download pipelines.
- **Chat Logging**: Appends all message structures to `chat_history.txt` on the server.
- **Zero External Dependencies**: Standard JDK 17+ libraries only, ensuring compilation ease out-of-the-box.

---

## 🛠️ Tech Stack

- **Backend**: Java (JDK 17+), ServerSockets, Multithreading, File I/O, WebSockets, SHA-1 Hashing
- **Desktop Client**: Java Swing (Custom UI design, Sound synthesizers)
- **Web Client**: HTML5, Vanilla CSS3, Javascript ES6
- **Architecture**: Clean Architecture, Object-Oriented Principles, Event-driven callbacks

---

## 📂 Project Structure

```
NetTalk
│
├── server
│   ├── ChatServer.java      # Server Socket dispatcher (HTTP/WS & Swing)
│   ├── ClientHandler.java   # Thread handler parsing incoming client streams
│   └── ServerLogger.java    # Standard Logger printing to console and server.log
│
├── client
│   ├── ChatClient.java      # Client networking socket & receiver thread
│   ├── ClientGUI.java       # Swing dark mode chat dashboard layout
│   └── FileTransfer.java    # Client-side HTTP upload & download tasks
│
├── model
│   ├── Message.java         # Message structure metadata model
│   └── User.java            # User metadata model
│
├── utils
│   ├── Constants.java       # Port settings, commands, and theme colors
│   └── FileUtils.java       # Web serving helpers, MIME mappings, chat archiver
│
├── resources
│   └── web
│       ├── index.html       # Web interface HTML structures
│       ├── style.css        # Responsive glassmorphic stylesheets
│       └── app.js           # Web client WebSocket event loops
│
├── .gitignore
│
├── LICENSE                  # MIT License
│
└── README.md
```

---

## 💻 Setup & Execution

### Prerequisites
- JDK 17 or higher installed. Verify using:
  ```bash
  java -version
  ```

### Step 1: Compilation
Open your terminal inside the project root directory and compile all packages:
```bash
javac server/*.java client/*.java model/*.java utils/*.java
```

### Step 2: Start the Server
Run the compiled server class. You can optionally specify a custom port number as an argument (defaults to `5000`):
```bash
java server.ChatServer 5000
```
*The terminal will indicate that the server is listening, and the web app is live.*

### Step 3: Run the Swing Clients
Open new terminals inside the project root and launch as many Swing client instances as desired:
```bash
java client.ClientGUI
```
- Enter a unique username, specify the server's IP address (e.g. `localhost` or its local LAN IP like `192.168.1.x`), enter the port number, and click **Connect**.

### Step 4: Run the Web Clients
Open your web browser and navigate to the server's LAN address:
```
http://localhost:5000
```
- Enter a nickname and click **Connect to Chatroom**.

---

## 📝 Future Improvements
- **End-to-End Encryption (E2EE)**: Implement Diffie-Hellman key exchanges and AES encryption for private messaging channels.
- **SQL Database Integration**: Port server chat logging from a simple flat-file `chat_history.txt` to a relational SQLite/H2 database.
- **Active Directory / LDAP**: Add standard network identity authentication layers.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.