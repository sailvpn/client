# Sail Application Management Scripts

This repository contains robust production-grade scripts designed to manage a Java JAR application as a background service. It supports starting, stopping, checking operational status, and configuring automatic system boot management.

## 📦 Repository Structure
* `sail.sh` - management script for **Linux** and **macOS** environments.
* `sail.bat` - management script for **Windows** environments.
* `sail.service` - Pre-configured Systemd service unit template file for **Linux**.
* `application.properties.txt` - External template configuration file.
* `app.jar` - Java application.
* `app.log` - Dynamically generated runtime application log file.
* `app.pid` - Dynamically generated Linux/macOS Process ID tracking file.

---

## ⚙️ External Configuration (Spring Properties)

The application automatically reads configuration parameters from an external file. To configure your settings without modifying or rebuilding the compiled JAR, duplicate or rename `application.properties.txt` to exactly `application.properties` in the same directory as `app.jar`.

### Configuration Template (`application.properties`)
```properties
# ===================================================================
# Remote Server Connection
# ===================================================================
params.remoteHost=your-server.com
params.remotePort=443
params.sni=example.test

# ===================================================================
# Local Proxy Ports
# ===================================================================
# local SOCKS5 port, no username/password needed
params.localPort=2080
# local HTTP/HTTPS port, blind forward
params.httpPort=9999
params.localHost=127.0.0.1

# ===================================================================
# Application Core Parameters & Constraints
# ===================================================================

# File paths to infrastructure assets
params.certPath=./ca.crt
# JWT token file path, this file contains the JWT token, the conent of the file will be updated automatically in case of automatic mode.
# ATTENTION, only one Token is valid at a time, if you create a token, all prervious ones will be invalidated.
params.jwtTokenFile=./token.jwt

# ===================================================================
# OPTION 1: Anonymity MODE
# ===================================================================
# in this mode, you will generate JWT RSA token from the Sail web portal, and copy the token to the file defined in 'params.jwtTokenFile'
# you can use the same token across multiple devices, and it will be valid until it expires.
# the token contains a temporary one-time id, and quota about the service(amount of bytes to be transported).
# it is 100% anonymous, the server and the client knows NOTHING about you

# Crypto engine name to use
# params.crypto=JWT2

# ===================================================================
# OPTION 2: AUTOMATIC MODE (Single User/Device)
# ===================================================================
# The client will automatically acquire and renew JWT tokens

# Crypto engine name to use
params.crypto=JWT

# IMPORTANT in the case of multiple devices, if you set "AUTO" mode on one device. all other devices will be invalidated once that token is updated.
params.tokenMode=AUTO

# you get your username and password from Sail's web portal, you can use them to acquire a new token ("AUTO" mode) for your device.
# if you do not want to expose your username and password. you can go to Sail's web portal, login,
# and generate a new token for all devices you want to use.
params.username=alice
params.password=SecurePassword123!

# Token settings (automatic mode)
params.expireMins=60                    # 60 minutes
params.renewInterval=10                    # Automatically renew in 10 minutes

# ===================================================================
# OPTION 3: MANUAL MODE (Multiple Devices / Family Sharing)
# ===================================================================
# Uncomment these lines to switch your configuration to manual token injection mode instead

# Crypto engine name to use
# params.crypto=JWT
# params.tokenMode=MANUAL
#
# Manual mode behavior:
# - Disables automatic acquisition and auto-renewal (prevents disrupting other devices)
# - The same static token payload can be manually shared across unlimited devices
# - Ideal choice for family sharing architectures
# - Ensure you update the target file defined in 'params.jwtTokenFile' before expiration
```

> ⚠️ **Important Constraint**: in the case of multiple devices, if you set "AUTO" mode on one device. all other devices will be invalidated once that token is updated.
---

## 🐧 Linux & macOS Deployment & Usage

### 1. Prerequisites
Before executing the script, you must grant executable permissions to it:
```bash
chmod +x sail.sh
```

### 2. Manual Commands
Run the script using one of the following arguments:
* **Start Application:** Launches the JAR in the background using `nohup` and saves the Process ID to `app.pid`.
  ```bash
  ./sail.sh start
  ```
* **Check Status:** Verifies if the saved Process ID is actively running in system memory.
  ```bash
  ./sail.sh status
  ```
* **Stop Application:** Sends a graceful termination signal (`SIGTERM`). If the app fails to exit within 5 seconds, it automatically triggers a forced shutdown (`SIGKILL`).
  ```bash
  ./sail.sh stop
  ```

### 3. Configure Auto-Start on Boot (Systemd)
To ensure the Java application starts automatically when the Linux server boots up (without requiring user login), you can register the bundled `sail.service` file:

1. Open the included `sail.service` file and verify that the `User` and `WorkingDirectory` paths match your target system environment:
   ```ini
   [Unit]
   Description=My Java Application Service
   After=network.target

   [Service]
   Type=forking
   User=myuser
   WorkingDirectory=/home/myuser/apps/myapp
   ExecStart=/home/myuser/apps/myapp/sail.sh start
   ExecStop=/home/myuser/apps/myapp/sail.sh stop
   Restart=on-failure

   [Install]
   WantedBy=multi-user.target
   ```
2. Copy the service unit file into your system configuration directory:
   ```bash
   sudo cp sail.service /etc/systemd/system/
   ```
3. Reload systemd, enable the service to hook into the boot routine, and start it immediately:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable sail.service
   sudo systemctl start sail.service
   ```
4. Verify system service logs:
   ```bash
   sudo systemctl status sail.service
   ```

---

## 🪟 Windows Deployment & Usage

### 1. Prerequisites
Ensure that your system environment path variable points to the Java runtime. The script leverages `javaw.exe` to suppress the visual command prompt window and process your application natively in the background.

### 2. Manual Commands
Open your Command Prompt (`cmd`) in the application directory and run:
* **Start Application:** Spawns a background task assigned with a unique window title wrapper for tracking.
  ```cmd
  sail.bat start
  ```
* **Check Status:** Inspects the active `tasklist` to verify if the custom window title identifier exists.
  ```cmd
  sail.bat status
  ```
* **Stop Application:** Commands `taskkill` to softly close the process tree. If stubborn, it issues a forced flag termination (`/F`).
  ```cmd
  sail.bat stop
  ```

### 3. Configure Auto-Start on Boot (Task Scheduler)
To ensure the script triggers silently in the background when Windows boots up:

1. Press `Win + R`, type `taskschd.msc`, and press **Enter**.
2. Click **Create Task...** on the right Action panel.
3. Under the **General** tab:
    * Assign a name (e.g., `My Java App Background Service`).
    * Select **Run whether user is logged on or not**.
    * Check **Run with highest privileges**.
4. Under the **Triggers** tab:
    * Click *New...* and set the "Begin the task" dropdown to **At startup**.
5. Under the **Actions** tab:
    * Click *New...* and set Action to **Start a program**.
    * Browse and select your `sail.bat` script.
    * Add the argument: `start`
    * **Critical:** In the *Start in (optional)* field, paste the absolute directory path of your application folder (e.g., `C:\apps\myapp\`).
6. Under the **Conditions** tab:
    * Uncheck *Start the task only if the computer is on AC power*.
7. Click **OK** and submit your Windows user credentials to securely authorize the task registration.

---

## 🪵 Log Management
Console output streams (`stdout` and `stderr`) are automatically redirected to `app.log` in real time.
* To monitor logs live in **Linux**: `tail -f app.log`
* To monitor logs live in **Windows PowerShell**: `Get-Content app.log -Wait`
