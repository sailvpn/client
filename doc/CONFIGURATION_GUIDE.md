# Configuration File Setup Guide

## Quick Start

### Step 1: Locate the Configuration File

The configuration file is named **`application.properties`** and should be placed in:

```
src/main/resources/application.properties
```

**Full path example:**
```
/home/youruser/proxy/src/main/resources/application.properties
```

### Step 2: Copy the Template

We've provided a template at **`config-template.properties`** in the project root.

**Copy the template:**
```bash
cp config-template.properties src/main/resources/application.properties
```

### Step 3: Edit the Configuration

Open the file with your favorite text editor:

```bash
# Linux/macOS
nano src/main/resources/application.properties

# Or use a GUI editor
gedit src/main/resources/application.properties
code src/main/resources/application.properties
```

### Step 4: Set Your Values

Edit these required values:

```properties
# Replace with your actual server
params.remoteHost=proxy.example.com
params.remotePort=443

# For automatic mode:
params.tokenMode=auto
params.username=your_actual_username
params.password=your_actual_password
```

### Step 5: Start the Client

```bash
./gradlew bootRun
```

---

## Configuration File Locations (PC)

### Option 1: Default Location (Recommended)

**Location:**
```
<project-root>/src/main/resources/application.properties
```

**Example paths:**
- **Linux:** `/home/john/proxy/src/main/resources/application.properties`
- **macOS:** `/Users/john/proxy/src/main/resources/application.properties`
- **Windows:** `C:\Users\John\proxy\src\main\resources\application.properties`

**Advantages:**
- ✅ No command-line arguments needed
- ✅ Spring Boot finds it automatically
- ✅ Easiest to use

**How to use:**
```bash
./gradlew bootRun
```

---

### Option 2: Custom Location (Advanced)

**Location:** Anywhere on your PC

**Example paths:**
- **Linux:** `/etc/proxy/config.properties`
- **Linux (user):** `~/.config/proxy/application.properties`
- **macOS:** `~/Library/Application Support/proxy/config.properties`
- **Windows:** `C:\ProgramData\proxy\config.properties`
- **Windows (user):** `%APPDATA%\proxy\config.properties`

**Advantages:**
- ✅ Keep config separate from code
- ✅ Easy to backup
- ✅ Multiple configs for different scenarios

**How to use:**
```bash
# Linux/macOS
./gradlew bootRun --spring.config.location=/etc/proxy/config.properties

# Windows
gradlew.bat bootRun --spring.config.location=C:\ProgramData\proxy\config.properties
```

---

### Option 3: Environment Variables (Most Secure)

**No config file needed!**

**How to use:**

**Linux/macOS:**
```bash
export crypto=JWT
export tokenMode=auto
export username=alice
export password=SecurePass123
export remoteHost=proxy.example.com
export remotePort=443

./gradlew bootRun
```

**Windows (PowerShell):**
```powershell
$env:crypto="JWT"
$env:tokenMode="auto"
$env:username="alice"
$env:password="SecurePass123"
$env:remoteHost="proxy.example.com"
$env:remotePort="443"

.\gradlew.bat bootRun
```

**Windows (CMD):**
```cmd
set crypto=JWT
set tokenMode=auto
set username=alice
set password=SecurePass123
set remoteHost=proxy.example.com
set remotePort=443

gradlew.bat bootRun
```

**Advantages:**
- ✅ Most secure (passwords not in files)
- ✅ No config file to manage
- ✅ Easy to change without editing files

---

## File Permissions (Security)

### Linux/macOS

**Recommended permissions:**
```bash
chmod 600 src/main/resources/application.properties
```

This ensures only the owner can read/write the file (passwords are protected).

**Verify:**
```bash
ls -l src/main/resources/application.properties
# Should show: -rw-------
```

### Windows

**Set file permissions via GUI:**

1. Right-click on `application.properties`
2. Properties → Security tab
3. Advanced → Disable inheritance
4. Remove all users except your account
5. Give your account Full Control

**Or use command line:**
```cmd
icacls application.properties /inheritance:r /grant:r "%USERNAME%:F"
```

---

## Configuration for Different Scenarios

### Scenario 1: Personal PC (Work from Home)

**Location:** `src/main/resources/application.properties`

**Configuration:**
```properties
params.crypto=JWT
params.tokenMode=auto
params.username=alice
params.password=SecurePass123!
params.remoteHost=company-proxy.example.com
params.remotePort=443
params.localPort=2080
params.httpPort=9999
```

**Usage:**
```bash
./gradlew bootRun
# Proxy runs automatically with token renewal
```

---

### Scenario 2: Multiple PCs (Home + Work)

**Generate token once:**
```bash
curl -k -X POST "https://company-proxy.example.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"SecurePass123!","expirationMinutes":43200}'
# Copy the token from response
```

**On BOTH PCs:**

**Location:** `src/main/resources/application.properties`

**Configuration:**
```properties
params.crypto=JWT
params.tokenMode=manual
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJj...
params.remoteHost=company-proxy.example.com
params.remotePort=443
```

**Benefits:**
- Same token on both PCs
- No auto-renewal = no disruption
- Update token manually every 30 days

---

### Scenario 3: Family Sharing (Multiple Users)

**Generate token once:**
```bash
curl -k -X POST "https://family-proxy.example.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{"username":"family","password":"FamilyPass123!","expirationMinutes":43200}'
```

**On EACH family member's PC:**

**Location:** `src/main/resources/application.properties`

**Configuration:**
```properties
params.crypto=JWT
params.tokenMode=manual
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJj...
params.remoteHost=family-proxy.example.com
params.remotePort=443
```

**Share the token with family via:**
- Secure messaging app
- Password manager
- Email (encrypted)

---

## Configuration Management Tips

### Tip 1: Use Symbolic Links

**Create a symlink to config in a standard location:**

```bash
# Linux/macOS
ln -s /etc/proxy/config.properties src/main/resources/application.properties

# Now edit /etc/proxy/config.properties
# The client will use it automatically
```

### Tip 2: Multiple Configurations

**Create different configs for different scenarios:**

```
configs/
  ├── work.properties      # Work proxy
  ├── home.properties      # Home proxy
  └── family.properties    # Family proxy
```

**Switch between them:**
```bash
# Use work config
./gradlew bootRun --spring.config.location=configs/work.properties

# Use home config
./gradlew bootRun --spring.config.location=configs/home.properties
```

### Tip 3: Version Control

**DO:**
- ✅ Commit `config-template.properties`
- ✅ Create `.gitignore` entry for actual config

**DON'T:**
- ❌ Commit `application.properties` with passwords
- ❌ Commit tokens to git

**.gitignore example:**
```
src/main/resources/application.properties
configs/*.properties
!config-template.properties
```

### Tip 4: Backup Your Configuration

**Create a backup:**
```bash
# Linux/macOS
cp src/main/resources/application.properties ~/backup/proxy-config-backup.properties

# Windows
copy src\main\resources\application.properties %USERPROFILE%\Documents\proxy-config-backup.properties
```

**Backup encryption (recommended):**
```bash
# Encrypt with GPG
gpg -c src/main/resources/application.properties
# Creates: application.properties.gpg

# Decrypt when needed
gpg -d application.properties.gpg > application.properties
```

---

## Troubleshooting

### Problem: Config file not found

**Error:**
```
Could not resolve placeholder 'params.username'
```

**Solutions:**

1. **Check file location:**
   ```bash
   ls -la src/main/resources/application.properties
   ```

2. **Verify file name:**
   - Must be exactly `application.properties`
   - Not `config.properties` or `settings.properties`

3. **Use explicit path:**
   ```bash
   ./gradlew bootRun --spring.config.location=/full/path/to/application.properties
   ```

### Problem: Permission denied

**Error:**
```
Cannot read configuration file: Permission denied
```

**Solutions:**

**Linux/macOS:**
```bash
chmod 600 src/main/resources/application.properties
chown $USER src/main/resources/application.properties
```

**Windows:**
```cmd
icacls application.properties /grant "%USERNAME%:F"
```

### Problem: Special characters in password

**If your password contains special characters like `!`, `$`, `@`, etc.**

**Option 1: Use environment variables**
```bash
export password='MyP@ssw0rd!'  # Single quotes preserve special chars
./gradlew bootRun
```

**Option 2: Escape in config file**
```properties
# Use backslash to escape
params.password=MyP\\@ssw0rd\\!
```

**Option 3: Use unicode escapes**
```properties
params.password=MyP\u0040ssw0rd\u0021
```

---

## Quick Reference

### File Locations Summary

| OS | Default Location |
|----|------------------|
| **Linux** | `~/proxy/src/main/resources/application.properties` |
| **macOS** | `~/proxy/src/main/resources/application.properties` |
| **Windows** | `C:\Users\YourName\proxy\src\main\resources\application.properties` |

### Required Permissions

| OS | Command |
|----|---------|
| **Linux/macOS** | `chmod 600 application.properties` |
| **Windows** | `icacls application.properties /inheritance:r /grant:r "%USERNAME%:F"` |

### Configuration Priority

1. **Command line** (highest): `-Dusername=alice`
2. **Environment variables**: `export username=alice`
3. **External config**: `--spring.config.location=/path/to/config`
4. **Default location** (lowest): `src/main/resources/application.properties`

---

## See Also

- **[JWT_QUICK_REFERENCE.md](JWT_QUICK_REFERENCE.md)** - Quick setup guide
- **[JWT_AUTO_MANAGEMENT.md](JWT_AUTO_MANAGEMENT.md)** - Complete user guide
- **[config-template.properties](../config-template.properties)** - Configuration template

---

**Last Updated:** December 5, 2025  
**Applies to:** PC (Windows, Linux, macOS)  
**Note:** Mobile devices (phones, tablets) are not covered - they may use different apps

