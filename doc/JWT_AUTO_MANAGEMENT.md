# Automatic JWT Token Management

## Overview

The proxy client now supports **automatic JWT token acquisition and renewal**, eliminating the need for manual token management. Simply provide your username and password, and the client handles everything else.

## Features

✅ **Automatic Token Acquisition** - Client fetches JWT token at startup using your credentials  
✅ **Automatic Token Renewal** - Client renews token periodically (default: every 10 minutes)  
✅ **Seamless Operation** - Token updates happen in the background without interrupting connections  
✅ **Flexible Configuration** - Configure via properties file, command line, or environment variables  
✅ **Fallback Support** - Falls back to manual token if auto-acquisition fails  

---

## Quick Start (Recommended Method)

### 1. Edit `src/main/resources/application.properties`

```properties
# Enable JWT authentication
params.crypto=JWT

# Your credentials
params.username=alice
params.password=SecurePassword123!

# Remote server
params.remoteHost=your-server.com
params.remotePort=443
```

### 2. Start the Client

```bash
./gradlew bootRun
```

### 3. Check the Logs

You should see:
```
Auto-acquiring JWT token for user: alice
Successfully acquired JWT token
Starting JWT token renewal every 10 minutes
SOCKS5 server started on port 2080
HTTP server started on port 9999
```

That's it! Your proxy is now running with automatic token management.

---

## Manual Token Mode (Token Sharing)

### When to Use Manual Mode

Use manual token mode when you need to:
- **Share one token across multiple devices** (PC, phone, tablet)
- **Share a token with family members**
- **Prevent auto-renewal disruption** on other devices
- **Manage tokens externally** (e.g., with a script or token manager)

### How Manual Mode Works

When you configure **only** `params.jwtToken` (without `username`/`password`):
- ✅ Client uses the provided token
- ✅ Auto-renewal is **DISABLED automatically**
- ✅ Token is used until it expires
- ✅ No disruption to other devices using the same token

### Setup Manual Token Mode

**Step 1: Generate a Token** (one time, from any device):

```bash
curl -k -X POST "https://your-server.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePassword123!",
    "expirationMinutes": 43200
  }'
```

Response:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0...",
    "expiresAt": 1735862400000
  }
}
```

**Step 2: Configure on ALL Devices** with the same token:

```properties
# application.properties (on each device)
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0...

# NO username/password = Manual mode (auto-renewal disabled)
params.remoteHost=your-server.com
params.remotePort=443
```

**Step 3: Start the Client** on each device:

```bash
./gradlew bootRun
```

**Expected logs:**
```
Using manual JWT token from configuration
Manual token mode: Auto-renewal is DISABLED to allow token sharing across devices
Token will be used until expiration. Renewal must be done manually.
SOCKS5 server started on port 2080
HTTP server started on port 9999
```

### Advantages

- ✅ **One token for all devices** - PC, phone, tablet all use the same token
- ✅ **No renewal disruption** - Auto-renewal disabled, no device invalidates others' tokens
- ✅ **Family sharing** - Multiple family members can share one token
- ✅ **Simple management** - Update token on all devices before expiration

### Token Expiration Management

Since auto-renewal is disabled in manual mode:

1. **Set a reminder** to update the token before it expires (e.g., every 25 days for 30-day token)
2. **Generate a new token** using the curl command above
3. **Update `params.jwtToken`** in `application.properties` on ALL devices
4. **Restart the client** on each device

**Tip:** Save the curl command in a script for easy token regeneration!

---

## Configuration Options

### Core Settings

| Property | Default | Description |
|----------|---------|-------------|
| `params.crypto` | `SHA_256` | Set to `JWT` to enable JWT authentication |
| `params.username` | `""` | Your username on the remote server |
| `params.password` | `""` | Your password on the remote server |
| `params.remoteHost` | `127.0.0.1` | Remote server hostname |
| `params.remotePort` | `2080` | Remote server port |

### Token Management Settings

| Property | Default | Description |
|----------|---------|-------------|
| `params.tokenExpirationMinutes` | `43200` | Token validity period (30 days) |
| `params.tokenRenewalEnabled` | `true` | Enable automatic token renewal |
| `params.tokenRenewalIntervalMinutes` | `10` | How often to renew (10 minutes) |

### Manual Token (Legacy)

| Property | Default | Description |
|----------|---------|-------------|
| `params.jwtToken` | `""` | Manually provide a JWT token |

---

## Configuration Methods

### Method 1: application.properties (Recommended)

Edit `src/main/resources/application.properties`:

```properties
params.crypto=JWT
params.username=alice
params.password=SecurePass123!
params.remoteHost=your-server.com
params.remotePort=443
```

### Method 2: System Properties (Command Line)

```bash
./gradlew bootRun \
  -Dcrypto=JWT \
  -Dusername=alice \
  -Dpassword=SecurePass123! \
  -DremoteHost=your-server.com \
  -DremotePort=443
```

### Method 3: Environment Variables

```bash
export crypto=JWT
export username=alice
export password=SecurePass123!
export remoteHost=your-server.com
export remotePort=443
./gradlew bootRun
```

**Priority:** System Properties > Environment Variables > application.properties

---

## How It Works

### Startup Sequence

1. **Client starts** → Reads configuration
2. **Checks crypto type** → If `JWT`, proceeds with token management
3. **Checks credentials** → If username/password provided, auto-acquire token
4. **Fetches token** → Makes HTTP POST to `/api/auth/token/generate`
5. **Starts renewal** → Schedules periodic token renewal (if enabled)
6. **Starts servers** → SOCKS5 and HTTP proxy servers start
7. **Ready to proxy** → Client forwards requests with JWT token

### Token Renewal

Every `tokenRenewalIntervalMinutes` (default: 10 minutes):

1. **Renewal task triggers**
2. **Sends current token** → POST to `/api/auth/token/generate` with `currentToken`
3. **Receives new token** → Server generates new token
4. **Updates token** → Client seamlessly replaces old token with new one
5. **Continues operation** → No interruption to existing connections

### Token Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│  Day 0: Client Startup                                      │
│  ├─ Auto-acquire token (valid for 30 days)                  │
│  └─ Start renewal timer (every 10 minutes)                    │
└─────────────────────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────┐
│  Every 10 minutes: Auto-Renewal                               │
│  ├─ Send current token to server                            │
│  ├─ Receive new token (valid for 30 days)                   │
│  └─ Replace old token with new token                        │
└─────────────────────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────┐
│  Continuous Operation                                        │
│  └─ Client keeps running indefinitely                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Advanced Configurations

### Custom Token Expiration

Set token to expire in 7 days instead of 30:

```properties
params.tokenExpirationMinutes=10080  # 7 days = 7 * 24 * 60
```

### Renewal Interval Tuning

**Default (10 minutes) - Recommended for most users:**
```properties
params.tokenRenewalIntervalMinutes=10  # Renews every 10 minutes
```

**Why 10 minutes?**
- Most proxy sessions last 1-2 hours
- Ensures fresh tokens throughout the session
- Low overhead (minimal server load)
- High security (tokens are frequently rotated)
- If client crashes, token is at most 10 minutes old when restarted

**For less frequent renewal (1 hour):**
```properties
params.tokenRenewalIntervalMinutes=60  # Good for long-running sessions
```

**For more frequent renewal (5 minutes):**
```properties
params.tokenRenewalIntervalMinutes=5  # Maximum security, slightly more server load
```

### Manual Token with Auto-Renewal

Use a pre-acquired token but still enable auto-renewal:

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
params.tokenRenewalEnabled=true
params.tokenRenewalIntervalMinutes=10
```

### Disable Auto-Renewal (Manual Mode)

Use static token without renewal:

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
params.tokenRenewalEnabled=false
```

---

## Troubleshooting

### "Failed to acquire initial JWT token"

**Possible causes:**
- Wrong username or password
- Remote server unreachable
- Server doesn't support JWT authentication

**Solution:**
```bash
# Test server connectivity
curl -k -X POST "https://your-server.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"SecurePass123!","expirationMinutes":43200}'

# Check server logs for authentication errors
```

### "JWT token is not configured"

**Possible causes:**
- Neither username/password nor jwtToken provided
- Auto-acquisition failed and no fallback token configured

**Solution:**
```properties
# Either provide credentials for auto-acquisition
params.username=alice
params.password=SecurePass123!

# Or provide a manual token
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
```

### "Error renewing JWT token"

**Possible causes:**
- Server connection lost
- Current token expired before renewal
- Server rejected token renewal

**Behavior:**
- Client will retry acquisition using username/password (if configured)
- If retry fails, client continues with last valid token until it expires

**Solution:**
```bash
# Check server connectivity
ping your-server.com

# Check token expiration at https://jwt.io

# Restart client to force fresh token acquisition
```

### Renewal Logs

Check logs to verify renewal is working:

```
JWT token renewed successfully at Fri Dec 05 10:30:00 UTC 2025
JWT token renewed successfully at Fri Dec 05 20:30:00 UTC 2025
JWT token renewed successfully at Sat Dec 06 06:30:00 UTC 2025
```

### "Too many redirects" Error

**Possible causes:**
- Server has a redirect loop (307 → 307 → ...)
- Incorrect server configuration
- Load balancer misconfiguration

**Behavior:**
- Client follows up to 5 redirects
- Logs each redirect URL
- Fails with "Too many redirects" error after 5 hops

**Solution:**
```bash
# Check server logs for redirect configuration
# Look for redirect URLs in client logs

# Example client log output:
# Following HTTP 307 redirect to: https://server.com/api/v2/auth/token/generate
# Following HTTP 307 redirect to: https://server.com/api/v3/auth/token/generate

# Fix server-side redirect configuration to point to final destination
```

---

## Security Considerations

### Password Storage

**⚠️ Important:** Storing passwords in `application.properties` is convenient but less secure than using environment variables or command-line arguments.

**Recommended for production:**

```bash
# Use environment variables (not visible in process list)
export username=alice
export password=SecurePass123!
./gradlew bootRun

# Or use a secrets manager and load via script
export username=$(cat /secure/username)
export password=$(cat /secure/password)
./gradlew bootRun
```

### Token Security

- Tokens are transmitted over TLS (encrypted)
- Tokens are stored in memory only (not on disk)
- Each renewal generates a new token and invalidates the old one
- Tokens contain random UUIDs, not user information

### Network Security

- Always use HTTPS (`params.remotePort=443`)
- Client supports self-signed certificates for development
- Token renewal uses the same TLS connection as proxy traffic

---

## Implementation Details

### JwtTokenManager Class

The `JwtTokenManager` component handles all token operations:

- **initialize()** - Called at startup, acquires initial token
- **acquireToken()** - Fetches new token using username/password
- **renewToken()** - Renews token using current token
- **getCurrentToken()** - Returns current valid token
- **shutdown()** - Stops renewal scheduler

### Integration with SecretImp

The `SecretImp` class now uses `JwtTokenManager` to get the current token:

```java
@Override
public byte[] getSecret() {
    if (getCryptoType() == Cryptos.JWT) {
        String currentToken = tokenManager.getCurrentToken();
        return currentToken.getBytes(StandardCharsets.UTF_8);
    }
    // ... hash-based authentication ...
}
```

This ensures every request uses the latest token, even if it was renewed in the background.

### Server Communication

Token acquisition and renewal use HTTP POST to the server's authentication API:

```http
POST /api/auth/token/generate HTTP/1.1
Host: your-server.com
Content-Type: application/json

{
  "username": "alice",
  "password": "SecurePass123!",
  "expirationMinutes": 43200
}
```

or for renewal:

```http
POST /api/auth/token/generate HTTP/1.1
Host: your-server.com
Content-Type: application/json

{
  "currentToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expirationMinutes": 43200
}
```

**HTTP Redirect Handling:**

The client automatically handles HTTP 307 (Temporary Redirect) and 308 (Permanent Redirect) responses:

- Follows redirects transparently (no user action needed)
- Preserves POST method and request body
- Handles both absolute and relative redirect URLs
- Prevents redirect loops (max 5 redirects)
- Logs redirect URLs for debugging

This is useful if your server uses load balancers or CDNs that redirect requests.

---

## Migration from Manual Token Management

If you're currently using manual JWT tokens, migration is easy:

### Before (Manual)

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
```

**Required actions:**
- Generate new token every 30 days
- Update application.properties
- Restart client

### After (Automatic)

```properties
params.crypto=JWT
params.username=alice
params.password=SecurePass123!
```

**Required actions:**
- None! Client handles everything automatically

---

## Example: Complete Configuration

```properties
# Proxy Client Configuration - Automatic JWT Token Management

# Remote server connection
params.remoteHost=proxy.example.com
params.remotePort=443

# Local proxy ports
params.localPort=2080    # SOCKS5
params.httpPort=9999     # HTTP

# Enable JWT authentication
params.crypto=JWT

# Automatic token acquisition (recommended)
params.username=alice
params.password=SecurePassword123!

# Token settings
params.tokenExpirationMinutes=43200        # 30 days
params.tokenRenewalEnabled=true            # Enable auto-renewal
params.tokenRenewalIntervalMinutes=10     # Renew every 10 minutes

# Optional: Manual token fallback (used if auto-acquisition fails)
# params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
```

Start the client:

```bash
./gradlew bootRun
```

Expected output:

```
Auto-acquiring JWT token for user: alice
Successfully acquired JWT token
Starting JWT token renewal every 10 minutes
SOCKS5 server started on port 2080
HTTP server started on port 9999
```

Configure your browser:
- SOCKS5 proxy: `localhost:2080`

✅ Done! Your proxy will now run indefinitely with automatic token management.

---

## FAQ

### Q: How often should I renew tokens?

**A:** The default is **10 minutes**, which is ideal for typical proxy usage:

- **Most proxy sessions last 1-2 hours** - Renewing every 10 minutes ensures fresh tokens throughout your session
- **Better security** - Frequent rotation means even if a token is compromised, it's only valid for ~10 minutes
- **Low overhead** - Only 6 renewals per hour is negligible server load
- **Resilient** - If the client crashes, you restart with a token that's at most 10 minutes old

**Adjust based on your needs:**
- **5 minutes** - Maximum security, slightly more server requests
- **30 minutes** - Good balance for longer sessions with lower renewal frequency
- **60 minutes** - Minimal overhead, suitable for all-day sessions

**Avoid:**
- **< 1 minute** - Creates unnecessary server load
- **> 6 hours** - Token becomes stale, defeats the purpose of auto-renewal

### Q: What happens if renewal fails?

**A:** The client will:
1. Try to acquire a new token using username/password (if configured)
2. If that fails, continue using the last valid token
3. Log an error message
4. You'll need to restart the client once the server is reachable

### Q: Can I use both username/password and manual token?

**A:** Yes! If both are provided:
1. Client tries to acquire token using username/password
2. If that fails, falls back to manual token
3. Renewal uses whichever token is currently active

### Q: Is my password encrypted?

**A:** Passwords are sent over HTTPS (TLS-encrypted) to the server. They're stored in plain text in `application.properties`, so protect that file with appropriate file permissions (`chmod 600 application.properties`).

### Q: Can I rotate passwords without restarting?

**A:** No, currently you need to restart the client after changing credentials. Future versions may support dynamic configuration reloading.

### Q: Does token renewal interrupt active connections?

**A:** No! Token renewal happens in the background. The client updates its token atomically, and new connections use the new token. Existing connections are unaffected.

---

## See Also

- [JWT Setup Guide](JWT_SETUP.md) - Manual token setup
- [Client Architecture](CLIENT_ARCHITECTURE.md) - How the client works
- [JWT Implementation](JWT_IMPLEMENTATION.md) - Technical details

---

**Last Updated:** December 5, 2025  
**Feature Status:** ✅ Implemented and Tested  
**Supported Platforms:** Linux, macOS, Windows

