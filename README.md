# Java Application Management Scripts

This repository contains robust production-grade scripts designed to manage a Java JAR application as a background service. It supports starting, stopping, checking operational status, and configuring automatic system boot management.

## 📁 Repository Structure
* `manage_app.sh` - Unified management script for **Linux** environments.
* `manage_app.bat` - Unified management script for **Windows** environments.
* `my_app.jar` - Your compiled Java application.
* `app.log` - Dynamically generated runtime application log file.
* `app.pid` - Dynamically generated Linux Process ID tracking file.

---

## 🐧 Linux Deployment & Usage

### 1. Prerequisites
Before executing the script, you must grant executable permissions to it:
```bash
chmod +x manage_app.sh
```

### 2. Manual Commands
Run the script using one of the following arguments:
* **Start Application:** Launches the JAR in the background using `nohup` and saves the Process ID to `app.pid`.
  ```bash
  ./manage_app.sh start
  ```
* **Check Status:** Verifies if the saved Process ID is actively running in system memory.
  ```bash
  ./manage_app.sh status
  ```
* **Stop Application:** Sends a graceful termination signal (`SIGTERM`). If the app fails to exit within 5 seconds, it automatically triggers a forced shutdown (`SIGKILL`).
  ```bash
  ./manage_app.sh stop
  ```

### 3. Configure Auto-Start on Boot (Systemd)
To ensure the Java application starts automatically when the Linux server boots up (without requiring user login):

1. Create a systemd service file:
   ```bash
   sudo nano /etc/systemd/system/myapp.service
   ```
2. Paste the following configuration (adjust `User` and directory paths to match your system environment):
   ```ini
   [Unit]
   Description=My Java Application Service
   After=network.target

   [Service]
   Type=forking
   User=myuser
   WorkingDirectory=/home/myuser/apps/myapp
   ExecStart=/home/myuser/apps/myapp/manage_app.sh start
   ExecStop=/home/myuser/apps/myapp/manage_app.sh stop
   Restart=on-failure

   [Install]
   WantedBy=multi-user.target
   ```
3. Reload systemd, enable the service, and start it immediately:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable myapp.service
   sudo systemctl start myapp.service
   ```
4. Verify system service logs:
   ```bash
   sudo systemctl status myapp.service
   ```

---

## 🪟 Windows Deployment & Usage

### 1. Prerequisites
Ensure that your system environment path variable points to the Java runtime. The script leverages `javaw.exe` to suppress the visual command prompt window and process your application natively in the background.

### 2. Manual Commands
Open your Command Prompt (`cmd`) in the application directory and run:
* **Start Application:** Spawns a background task assigned with a unique window title wrapper for tracking.
  ```cmd
  manage_app.bat start
  ```
* **Check Status:** Inspects the active `tasklist` to verify if the custom window title identifier exists.
  ```cmd
  manage_app.bat status
  ```
* **Stop Application:** Commands `taskkill` to softly close the process tree. If stubborn, it issues a forced flag termination (`/F`).
  ```cmd
  manage_app.bat stop
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
    * Browse and select your `manage_app.bat` script.
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

