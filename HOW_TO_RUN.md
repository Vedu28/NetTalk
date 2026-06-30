# Quick Start Manual - NetTalk LAN Chat

Follow these quick commands to build and run the NetTalk server and clients.

---

## 🛠️ Step 1: Compilation
Compile the entire project from the root folder:
```bash
javac server/*.java client/*.java model/*.java utils/*.java
```

---

## 🖥️ Step 2: Running the Server
Run the unified server on port `5000` (defaults to 5000 if not specified):
```bash
java server.ChatServer 5000
```
- The Web interface will be hosted at: `http://localhost:5000`
- Keep this server terminal window open.

---

## 👥 Step 3: Running Clients

### Option A: Swing Desktop Client (GUI)
Launch the native desktop interface:
```bash
java client.ClientGUI
```
- **Login Details**:
  - **Username**: Enter a nickname (e.g. `Ved`, `Rahul`)
  - **Server IP**: Type `localhost` if running on the server's machine, or the server's LAN IP address (e.g. `192.168.1.15`) if connecting from another device on the network.
  - **Port**: `5000`

### Option B: Web Browser Client
Navigate to the server location in any web browser:
- **Same machine**: `http://localhost:5000`
- **Other machines on Wi-Fi/LAN**: `http://<server-lan-ip>:5000`

---

## 📡 Finding Your LAN IP Address
To host connections from other machines, find your local IP address:
- **Windows**: Open Command Prompt, run `ipconfig`, look for **IPv4 Address** (e.g., `192.168.1.15`).
- **macOS/Linux**: Open Terminal, run `ifconfig` or `ip a`, look for `inet` under `en0` or `eth0`.
- Ensure your network Firewall allows TCP port `5000` inbound traffic.
