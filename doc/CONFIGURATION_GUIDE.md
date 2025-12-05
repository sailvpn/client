# Configuration File Setup Guide for End Users

This guide is for users who have **installed** the proxy client (from JAR, DEB, TAR, etc.).

---

## Quick Start (5 Minutes)

### Step 1: Find Where You Installed the Proxy

After unpacking/installing, you should have a folder like:

- **Linux:** `/opt/proxy/` or `/usr/local/proxy/` or `~/proxy/`
- **Windows:** `C:\Program Files\proxy\` or `C:\proxy\`
- **macOS:** `/Applications/proxy/` or `~/Applications/proxy/`

**Find your installation directory:**
```bash
# Linux/macOS
ls -la ~/proxy/        # If installed in home directory
ls -la /opt/proxy/     # If installed system-wide

# Windows (PowerShell)
dir C:\proxy\
dir "C:\Program Files\proxy\"
```

### Step 2: Create Your Configuration File

**Go to your installation directory:**
```bash
# Linux/macOS
cd ~/proxy/

# Windows (PowerShell)
cd C:\proxy\
```

**Create a file named `application.properties`:**
```bash
# Linux/macOS
nano application.properties

# Windows - use Notepad
notepad application.properties
```

### Step 3: Copy This Basic Configuration

**For automatic mode (single PC):**
```properties
# Server settings (REQUIRED - ask your admin for these)
params.remoteHost=your-proxy-server.com
params.remotePort=443

# Authentication (REQUIRED - your login credentials)
params.crypto=JWT
params.tokenMode=auto
params.username=your_username
params.password=your_password

# Local proxy ports (you can change these if needed)
params.localPort=2080
params.httpPort=9999
```

**Save the file!**

### Step 4: Run the Proxy

```bash
# Linux/macOS
java -jar proxy.jar

# Or if you have a startup script:
./start-proxy.sh

# Windows (double-click or PowerShell)
java -jar proxy.jar

# Or double-click:
start-proxy.bat
```

### Step 5: Configure Your Browser

Set your browser to use SOCKS5 proxy:
- **Host:** `localhost`
- **Port:** `2080`

**Done!** Your proxy is now running.

---

## Where to Put the Configuration File

### After Installing from JAR/DEB/TAR

**The configuration file should be in the SAME directory as the proxy application.**

#### Installation from TAR.GZ or ZIP

You unpacked the file and got something like:
```
proxy/
  ├── proxy.jar                    ← The main application
  ├── start-proxy.sh               ← Startup script (Linux/macOS)
  ├── start-proxy.bat              ← Startup script (Windows)
  ├── config-template.properties   ← Example configuration
  └── README.txt
```

**Create your `application.properties` HERE:**
```
proxy/
  ├── proxy.jar
  ├── application.properties       ← PUT YOUR CONFIG HERE
  ├── start-proxy.sh
  └── ...
```

**Commands:**
```bash
# Linux/macOS
cd ~/proxy/                        # Go to installation directory
cp config-template.properties application.properties  # Copy template
nano application.properties        # Edit configuration

# Windows
cd C:\proxy\
copy config-template.properties application.properties
notepad application.properties
```

---

#### Installation from DEB (Debian/Ubuntu)

After installing with `sudo dpkg -i proxy.deb`, files are in:

```
/opt/proxy/                        ← Installation directory
  ├── proxy.jar
  ├── start-proxy.sh
  └── config-template.properties
```

**Create your configuration:**
```bash
cd /opt/proxy/
sudo cp config-template.properties application.properties
sudo nano application.properties

# Make it readable only by you
sudo chmod 600 application.properties
sudo chown $USER application.properties
```

---

#### Installation from RPM (RedHat/CentOS/Fedora)

After installing with `sudo rpm -i proxy.rpm`, files are in:

```
/opt/proxy/                        ← Installation directory
  ├── proxy.jar
  ├── start-proxy.sh
  └── config-template.properties
```

**Create your configuration:**
```bash
cd /opt/proxy/
sudo cp config-template.properties application.properties
sudo nano application.properties
sudo chmod 600 application.properties
sudo chown $USER application.properties
```

---

#### Installation on Windows (Installer or ZIP)

After installation, files are typically in:
```
C:\Program Files\proxy\            ← or C:\proxy\
  ├── proxy.jar
  ├── start-proxy.bat
  └── config-template.properties
```

**Create your configuration:**
```cmd
cd "C:\Program Files\proxy"
copy config-template.properties application.properties
notepad application.properties
```

Or just:
1. Open File Explorer
2. Navigate to `C:\Program Files\proxy\`
3. Copy `config-template.properties`
4. Rename copy to `application.properties`
5. Right-click → Edit with Notepad

---

## Alternative: Using Custom Configuration Location

If you want to keep your configuration file somewhere else (not in the installation directory):

### Linux/macOS

**Put your config anywhere:**
```bash
mkdir -p ~/.config/proxy/
nano ~/.config/proxy/my-config.properties
```

**Run the proxy with custom config:**
```bash
cd ~/proxy/
java -jar proxy.jar --spring.config.location=~/.config/proxy/my-config.properties
```

### Windows

**Put your config anywhere:**
```
C:\Users\YourName\Documents\proxy-config.properties
```

**Run the proxy with custom config:**
```cmd
cd C:\proxy
java -jar proxy.jar --spring.config.location=C:\Users\YourName\Documents\proxy-config.properties
```

---

## Alternative: Using Environment Variables (Most Secure)

**No config file needed!** Set environment variables instead.

### Linux/macOS

**Create a startup script** (e.g., `my-proxy-start.sh`):
```bash
#!/bin/bash
export crypto=JWT
export tokenMode=auto
export username=alice
export password=SecurePass123
export remoteHost=proxy.example.com
export remotePort=443

cd ~/proxy/
java -jar proxy.jar
```

**Make it executable and run:**
```bash
chmod +x my-proxy-start.sh
./my-proxy-start.sh
```

### Windows

**Create a batch file** (e.g., `my-proxy-start.bat`):
```batch
@echo off
set crypto=JWT
set tokenMode=auto
set username=alice
set password=SecurePass123
set remoteHost=proxy.example.com
set remotePort=443

cd C:\proxy
java -jar proxy.jar
```

**Double-click to run.**

**Advantages:**
- ✅ Passwords not stored in files
- ✅ Easy to change without editing config files

---

## Protecting Your Configuration File (Security)

**Your configuration file contains your password!** Protect it.

### Linux/macOS

Make the file readable only by you:
```bash
cd ~/proxy/                        # Go to where your config is
chmod 600 application.properties   # Only you can read/write

# Verify:
ls -l application.properties
# Should show: -rw------- (only owner can read/write)
```

### Windows

**Option 1: GUI (Easiest)**
1. Right-click `application.properties`
2. Properties → Security → Advanced
3. Click "Disable inheritance"
4. Choose "Remove all inherited permissions"
5. Click "Add"
6. Type your username → Check Names → OK
7. Check "Full control" → OK

**Option 2: Command Line**
```cmd
icacls application.properties /inheritance:r /grant:r "%USERNAME%:F"
```

---

## Common Configurations

### Scenario 1: I Use the Proxy on ONE Computer

**Best option:** Automatic mode (easier!)

**File:** `application.properties` in installation directory

```properties
# Server (ask your admin)
params.remoteHost=proxy-server.com
params.remotePort=443

# Your login
params.crypto=JWT
params.tokenMode=auto
params.username=alice
params.password=MyPassword123

# Local ports
params.localPort=2080
params.httpPort=9999
```

**Start:**
```bash
java -jar proxy.jar
# Or: ./start-proxy.sh (Linux/macOS)
# Or: start-proxy.bat (Windows)
```

**Benefits:**
- ✅ Set it once, forget it
- ✅ Token renews automatically
- ✅ No maintenance needed

---

### Scenario 2: I Use the Proxy on MULTIPLE Computers

**Best option:** Manual mode (one token for all PCs)

**Step 1: Get a token** (do this once from any computer):

```bash
# Linux/macOS/Windows (PowerShell with curl installed)
curl -k -X POST "https://proxy-server.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"MyPassword123","expirationMinutes":43200}'
```

Response looks like:
```json
{"success":true,"data":{"token":"eyJhbGci..."}}
```

**Copy the token** (everything after `"token":"`, before the next `"`)

**Step 2: On EACH computer**, create `application.properties`:

```properties
# Server
params.remoteHost=proxy-server.com
params.remotePort=443

# Manual mode (same token on all PCs)
params.crypto=JWT
params.tokenMode=manual
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJj...

# Local ports
params.localPort=2080
params.httpPort=9999
```

**Benefits:**
- ✅ Same token on all computers
- ✅ No conflicts between devices
- ✅ Token valid for 30 days

**Important:** Remember to update the token on all computers before it expires (30 days)!

---

### Scenario 3: Family Sharing

Same as Scenario 2, but share the token with family members.

**Generate ONE token, give it to everyone:**

1. One person generates the token (see Scenario 2)
2. Share the token securely (Signal, password manager, etc.)
3. Everyone creates `application.properties` with the SAME token
4. When it expires (30 days), generate new token and share again

---


## Troubleshooting

### Problem: "Config file not found" or "Could not find configuration"

**Error messages:**
```
Could not resolve placeholder 'params.username'
Configuration file not found
```

**Solutions:**

1. **Make sure the file is named EXACTLY `application.properties`**
   - Not `config.properties`
   - Not `settings.properties`
   - Not `Application.properties` (capital A is wrong)

2. **Make sure it's in the SAME directory as proxy.jar**
   ```bash
   # Linux/macOS - check if both files are together
   ls -la ~/proxy/
   # Should see both:
   # proxy.jar
   # application.properties

   # Windows
   dir C:\proxy\
   # Should see both files
   ```

3. **If using custom location, specify it when running:**
   ```bash
   java -jar proxy.jar --spring.config.location=/path/to/your/config.properties
   ```

### Problem: "Permission denied"

**Error:**
```
Cannot read configuration file: Permission denied
```

**Solutions:**

**Linux/macOS:**
```bash
cd ~/proxy/
chmod 644 application.properties  # Make it readable
# Or if you want only you to read it:
chmod 600 application.properties
```

**Windows:**
- Right-click file → Properties → Security
- Make sure your user account has "Read" permission

### Problem: "Proxy not connecting" or "Authentication failed"

**Check your settings:**

1. **Is the server address correct?**
   ```properties
   params.remoteHost=proxy-server.com  # Check this with your admin
   params.remotePort=443               # Usually 443 for HTTPS
   ```

2. **Are your username/password correct?**
   ```properties
   params.username=alice               # Your actual username
   params.password=YourPassword123     # Your actual password
   ```

3. **Is the mode correct?**
   ```properties
   params.tokenMode=auto               # For automatic mode
   # OR
   params.tokenMode=manual             # For manual mode
   ```

### Problem: Special characters in password

If your password has special characters (`!`, `@`, `#`, `$`, etc.), put it in quotes:

**Option 1: Use environment variable (recommended)**
```bash
# Linux/macOS
export password='MyP@ssw0rd!'
java -jar proxy.jar

# Windows
set password=MyP@ssw0rd!
java -jar proxy.jar
```

**Option 2: Escape special characters**
```properties
# In application.properties, use backslash before special chars
params.password=MyP\\@ssw0rd\\!
```

### Problem: "Token expired" (Manual mode)

**Signs:**
- Proxy was working, now fails
- Error message mentions "token" or "expired"

**Solution:**
Generate a new token and update ALL your computers:

```bash
# 1. Generate new token
curl -k -X POST "https://proxy-server.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"pass","expirationMinutes":43200}'

# 2. Copy the new token

# 3. Update application.properties on ALL computers
params.jwtToken=NEW_TOKEN_HERE

# 4. Restart proxy on all computers
```

---

## Quick Reference

### Where is my config file?

**Same directory as proxy.jar:**
- Linux: `~/proxy/application.properties`
- Windows: `C:\proxy\application.properties`
- macOS: `~/proxy/application.properties`

### What should be in my config file?

**Automatic mode (single PC):**
```properties
params.crypto=JWT
params.tokenMode=auto
params.username=YOUR_USERNAME
params.password=YOUR_PASSWORD
params.remoteHost=SERVER_ADDRESS
params.remotePort=443
params.localPort=2080
params.httpPort=9999
```

**Manual mode (multiple PCs):**
```properties
params.crypto=JWT
params.tokenMode=manual
params.jwtToken=YOUR_TOKEN_HERE
params.remoteHost=SERVER_ADDRESS
params.remotePort=443
params.localPort=2080
params.httpPort=9999
```

### How do I run the proxy?

```bash
# Linux/macOS
cd ~/proxy/
java -jar proxy.jar

# Windows (double-click or PowerShell)
cd C:\proxy
java -jar proxy.jar
```

### How do I configure my browser?

**Firefox:**
1. Settings → General → Network Settings
2. Manual proxy configuration
3. SOCKS Host: `localhost`, Port: `2080`
4. SOCKS v5: ✅
5. OK

**Chrome/Edge:**
- Use system proxy settings or command line:
  ```bash
  chrome.exe --proxy-server="socks5://localhost:2080"
  ```

---

## See Also

- **[JWT_QUICK_REFERENCE.md](JWT_QUICK_REFERENCE.md)** - Quick setup guide
- **[JWT_AUTO_MANAGEMENT.md](JWT_AUTO_MANAGEMENT.md)** - Complete user guide
- **[config-template.properties](../config-template.properties)** - Configuration template

---

**Last Updated:** December 5, 2025  
**Applies to:** PC (Windows, Linux, macOS)  
**Note:** Mobile devices (phones, tablets) are not covered - they may use different apps

