# JWT Auto-Management Quick Reference

## One-Page Setup Guide

### Step 1: Configure (30 seconds)

**File location:** `src/main/resources/application.properties`

Edit `src/main/resources/application.properties`:

```properties
params.crypto=JWT
params.username=YOUR_USERNAME
params.password=YOUR_PASSWORD
params.remoteHost=YOUR_SERVER
params.remotePort=443
```

### Step 2: Start (10 seconds)

```bash
./gradlew bootRun
```

### Step 3: Use (forever)

Configure your apps to use:
- **SOCKS5:** `localhost:2080`
- **HTTP:** `localhost:9999`

✅ **Done!** Token management is now automatic.

---

## Configuration Cheat Sheet

### Minimal Configuration

```properties
params.crypto=JWT
params.username=alice
params.password=pass123
params.remoteHost=server.com
```

### Production Configuration

```properties
# Server
params.remoteHost=proxy.example.com
params.remotePort=443

# Authentication
params.crypto=JWT
params.username=alice
params.password=SecurePassword123!

# Token management
params.tokenExpirationMinutes=43200     # 30 days
params.tokenRenewalIntervalMinutes=10  # 10 minutes
params.tokenRenewalEnabled=true

# Local ports
params.localPort=2080
params.httpPort=9999
```

### Command Line Override

```bash
./gradlew bootRun \
  -Dusername=alice \
  -Dpassword=pass123 \
  -DremoteHost=server.com \
  -Dcrypto=JWT
```

---

## Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `crypto` | `SHA_256` | Set to `JWT` |
| `username` | `""` | Your username |
| `password` | `""` | Your password |
| `remoteHost` | `127.0.0.1` | Server hostname |
| `remotePort` | `2080` | Server port (443 for HTTPS) |
| `tokenExpirationMinutes` | `43200` | 30 days |
| `tokenRenewalEnabled` | `true` | Enable auto-renewal |
| `tokenRenewalIntervalMinutes` | `10` | Renew every 10 minutes |
| `jwtToken` | `""` | Manual token (fallback) |
| `localPort` | `3080` | SOCKS5 port |
| `httpPort` | `9999` | HTTP proxy port |

---

## Common Use Cases

### Use Case 1: Development/Testing

```properties
params.crypto=JWT
params.username=testuser
params.password=test123
params.remoteHost=localhost
params.remotePort=2080
params.tokenRenewalIntervalMinutes=60  # Renew every hour
```

### Use Case 2: Production

```properties
params.crypto=JWT
params.username=alice
params.password=${PROXY_PASSWORD}  # Read from environment
params.remoteHost=proxy.company.com
params.remotePort=443
params.tokenExpirationMinutes=43200
params.tokenRenewalIntervalMinutes=10
```

### Use Case 3: Manual Token (Shared Across Devices)

**Best for:** Multiple devices, family sharing

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
# NO username/password = Manual mode (auto-renewal disabled)
```

**Benefits:**
- One token works on all devices
- No auto-renewal disruption
- Perfect for family sharing

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
params.tokenRenewalEnabled=true
```

### Use Case 4: Legacy (No Auto-Management)

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
params.tokenRenewalEnabled=false
```

---

## Troubleshooting Quick Fixes

### "Failed to acquire initial JWT token"

```bash
# Check server is reachable
curl -k https://your-server.com/api/auth/token/generate

# Verify credentials
# Fix: Update username/password in application.properties
```

### "JWT token is not configured"

```bash
# Fix: Add username/password or jwtToken
params.username=alice
params.password=pass123
```

### Client won't start

```bash
# Clean and rebuild
./gradlew clean
./gradlew bootRun
```

### Port already in use

```bash
# Change ports
params.localPort=3080
params.httpPort=8888
```

### Too many redirects

```bash
# Server has redirect loop
# Check client logs for redirect chain
# Fix server configuration
# Client automatically follows up to 5 redirects
```

---

## Startup Messages

### ✅ Success

```
Auto-acquiring JWT token for user: alice
Successfully acquired JWT token
Starting JWT token renewal every 10 minutes
SOCKS5 server started on port 2080
HTTP server started on port 9999
```

### ❌ Failure

```
Failed to acquire initial JWT token
JWT token is not configured. Please set jwtToken or username/password.
```

---

## Token Expiration Time Examples

| Duration | Minutes | Command |
|----------|---------|---------|
| 1 hour | 60 | `params.tokenExpirationMinutes=60` |
| 12 hours | 720 | `params.tokenExpirationMinutes=720` |
| 1 day | 1440 | `params.tokenExpirationMinutes=1440` |
| 1 week | 10080 | `params.tokenExpirationMinutes=10080` |
| 1 month | 43200 | `params.tokenExpirationMinutes=43200` ⭐ Default |
| 3 months | 129600 | `params.tokenExpirationMinutes=129600` |

---

## Renewal Interval Examples

| Frequency | Minutes | Command |
|-----------|---------|---------|
| Every 5 min | 5 | `params.tokenRenewalIntervalMinutes=5` |
| Every 10 min | 10 | `params.tokenRenewalIntervalMinutes=10` ⭐ Default |
| Every 30 min | 30 | `params.tokenRenewalIntervalMinutes=30` |
| Every 1 hour | 60 | `params.tokenRenewalIntervalMinutes=60` |
| Every 6 hours | 360 | `params.tokenRenewalIntervalMinutes=360` |

**Recommendation:** For typical usage (1-2 hour sessions), 10-30 minutes is ideal  
Example: 30-day token with 10-minute renewal = 4,320 renewals before expiration (very secure)

---

## Security Best Practices

### ✅ DO

- Use HTTPS (port 443) for remote server
- Use strong passwords
- Set token expiration to 30 days or less
- Enable auto-renewal
- Use environment variables for passwords in production
- Protect `application.properties` with file permissions

### ❌ DON'T

- Use HTTP (unencrypted) for remote server
- Commit passwords to version control
- Share JWT tokens
- Set token expiration >3 months
- Disable auto-renewal in production
- Store tokens in public repositories

---

## Environment Variable Examples

### Linux/macOS

```bash
export crypto=JWT
export username=alice
export password=SecurePass123!
export remoteHost=proxy.example.com
export remotePort=443
./gradlew bootRun
```

### Windows PowerShell

```powershell
$env:crypto="JWT"
$env:username="alice"
$env:password="SecurePass123!"
$env:remoteHost="proxy.example.com"
$env:remotePort="443"
.\gradlew.bat bootRun
```

### Windows CMD

```cmd
set crypto=JWT
set username=alice
set password=SecurePass123!
set remoteHost=proxy.example.com
set remotePort=443
gradlew.bat bootRun
```

---

## Browser Configuration

### Firefox

1. Settings → General → Network Settings → Settings
2. Manual proxy configuration
3. SOCKS Host: `localhost`, Port: `2080`
4. SOCKS v5: ✅
5. OK

### Chrome/Chromium

```bash
google-chrome --proxy-server="socks5://localhost:2080"
```

### System-wide (Linux)

```bash
# GNOME
gsettings set org.gnome.system.proxy mode 'manual'
gsettings set org.gnome.system.proxy.socks host 'localhost'
gsettings set org.gnome.system.proxy.socks port 2080

# Export for CLI tools
export ALL_PROXY=socks5://localhost:2080
```

---

## Testing

### Test SOCKS5 Proxy

```bash
curl --socks5 localhost:2080 https://api.ipify.org?format=json
```

### Test HTTP Proxy

```bash
curl --proxy http://localhost:9999 http://api.ipify.org?format=json
```

### Test Token Renewal

```bash
# Watch logs for renewal messages (every 10 minutes by default)
./gradlew bootRun | grep "token renewed"
```

---

## Migration Paths

### From Hash Authentication

**Before:**
```properties
params.crypto=SHA_256
params.secret=shared-secret
```

**After:**
```properties
params.crypto=JWT
params.username=alice
params.password=SecurePass123!
```

### From Manual JWT

**Before:**
```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
```

**After:**
```properties
params.crypto=JWT
params.username=alice
params.password=SecurePass123!
params.tokenRenewalEnabled=true
```

---

## Complete Example

**File:** `src/main/resources/application.properties`

```properties
# Company Proxy Client Configuration
# Last updated: 2025-12-05

# Remote proxy server
params.remoteHost=proxy.company.com
params.remotePort=443

# JWT authentication with auto-renewal
params.crypto=JWT
params.username=alice
params.password=SecurePassword123!
params.tokenExpirationMinutes=43200     # 30 days
params.tokenRenewalEnabled=true
params.tokenRenewalIntervalMinutes=10  # 10 minutes

# Local proxy services
params.localPort=2080    # SOCKS5
params.httpPort=9999     # HTTP

# Logging
logging.level.com.illiad.proxy=INFO
```

**Start:**
```bash
./gradlew bootRun
```

**Expected output:**
```
Auto-acquiring JWT token for user: alice
Successfully acquired JWT token
Starting JWT token renewal every 10 minutes
SOCKS5 server started on port 2080
HTTP server started on port 9999
```

**Use:**
- Configure browser: SOCKS5 `localhost:2080`
- Or use curl: `curl --socks5 localhost:2080 https://example.com`

✅ **Proxy is now running with automatic token management!**

---

## Quick Links

- **Full Guide:** [JWT_AUTO_MANAGEMENT.md](JWT_AUTO_MANAGEMENT.md)
- **Manual Setup:** [JWT_SETUP.md](JWT_SETUP.md)
- **Architecture:** [CLIENT_ARCHITECTURE.md](CLIENT_ARCHITECTURE.md)
- **Implementation:** [JWT_IMPLEMENTATION.md](JWT_IMPLEMENTATION.md)

---

**Last Updated:** December 5, 2025

