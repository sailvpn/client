# Proxy Client Architecture

## Overview

This is a **local SOCKS5/HTTP proxy client** that connects to a remote Illiad proxy server. The client does NOT implement authentication - it simply uses JWT tokens obtained from the remote server.

---

## Quick Start

### Prerequisites
1. Register an account on the remote Illiad proxy server
2. Know your server's hostname and port

### Simplest Setup (Automatic Token Management)

1. **Edit `src/main/resources/application.properties`:**
   ```properties
   params.crypto=JWT
   params.username=your_username
   params.password=your_password
   params.remoteHost=your-server.com
   params.remotePort=443
   ```

2. **Start the client:**
   ```bash
   ./gradlew bootRun
   ```

3. **Configure your applications to use the proxy:**
   - SOCKS5: `localhost:2080`
   - HTTP: `localhost:9999`

That's it! The client will automatically:
- Acquire a JWT token at startup
- Renew the token every 10 minutes
- Keep working indefinitely

---

## Client Responsibilities

### ✅ What the Client DOES:

1. **Local Proxy Services**
   - Provides SOCKS5 proxy on local port (default: 2080)
   - Provides HTTP proxy on local port (default: 9999)
   - Accepts connections from local applications (browsers, CLI tools, etc.)

2. **Protocol Handling**
   - Encodes outgoing requests with Illiad protocol headers
   - Includes JWT token in headers when configured
   - Forwards requests to remote server
   - Decodes responses from remote server

3. **Netty-based Networking**
   - All networking handled by Netty (not Spring Web)
   - HTTP proxy uses Netty HTTP codecs
   - SOCKS5 proxy uses custom Netty codecs
   - TLS/DTLS support via Netty handlers

---

## Client Responsibilities

### ❌ What the Client DOES NOT DO:

1. **NO Authentication Logic**
   - Does NOT validate JWT tokens
   - Does NOT decode JWT tokens
   - Does NOT implement login/register endpoints
   - Does NOT manage user sessions

2. **NO Token Management**
   - Does NOT generate tokens
   - Does NOT store tokens in database
   - Does NOT renew tokens automatically

3. **NO Web Server**
   - Does NOT provide REST API endpoints
   - Does NOT serve web pages
   - Does NOT use Spring Web framework

---

## How Users Get JWT Tokens

The proxy client now supports **three methods** for JWT token management:

### Method 1: Automatic Token Acquisition (RECOMMENDED)

The client automatically acquires and renews JWT tokens using your username and password.

**Configure `application.properties`:**
```properties
# Enable JWT authentication
params.crypto=JWT

# Provide your credentials
params.username=alice
params.password=SecurePass123!

# Remote server details
params.remoteHost=remote-server.com
params.remotePort=443

# Token management (optional - these are defaults)
params.tokenExpirationMinutes=43200        # 30 days
params.tokenRenewalEnabled=true            # Enable auto-renewal
params.tokenRenewalIntervalMinutes=10     # Renew every 10 minutes
```

**Start the client:**
```bash
./gradlew bootRun
```

The client will:
1. Automatically acquire a JWT token from the server at startup using your credentials
2. Periodically renew the token every 10 minutes (configurable)
3. Seamlessly replace the old token with the new one
4. Continue working without interruption

**Benefits:**
- ✅ No manual token management required
- ✅ Tokens are automatically renewed before expiration
- ✅ Seamless operation - no restarts needed
- ✅ Credentials can be provided via config file, system properties, or command line

### Method 2: Manual Token with Auto-Renewal

Use a pre-acquired token but still benefit from automatic renewal.

**Configure `application.properties`:**
```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
params.tokenRenewalEnabled=true
params.tokenRenewalIntervalMinutes=10
```

The client will use your token and automatically renew it periodically.

### Method 3: Manual Token Management (Legacy)

Manually acquire and manage tokens (the old way).

### Step 1: Register/Login on Remote Server

Users interact with the **remote server** (not this client) to register and login:

```bash
# Register on remote server
curl -k -X POST "https://remote-server.com/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePass123!",
    "email": "alice@example.com"
  }'

# Login to get userId
curl -k -X POST "https://remote-server.com/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePass123!"
  }'
# Response: {"success": true, "data": {"userId": "abc123...", ...}}
```

### Step 2: Generate Token on Remote Server

```bash
# Generate JWT token on remote server
curl -k -X POST "https://remote-server.com/api/auth/token/generate?userId=abc123..." \
  -H "Content-Type: application/json" \
  -d '{"expirationMinutes": 43200}'
# Response: {"success": true, "data": {"token": "eyJhbGci...", ...}}
```

### Step 3: Configure Client with Token

Copy the JWT token and configure the client:

**Edit `application.properties`:**
```properties
# Use JWT authentication
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0...

# Disable auto-renewal (manual mode)
params.tokenRenewalEnabled=false

# Remote server details
params.remoteHost=remote-server.com
params.remotePort=443
```

### Step 4: Run Client

```bash
./gradlew bootRun
```

The client now:
- Listens on local ports (SOCKS5: 2080, HTTP: 9999)
- Forwards requests to remote server with JWT token in Illiad headers
- Remote server validates the JWT token
- Remote server proxies the requests if token is valid

---

## Providing Credentials via Command Line or Environment

You can provide credentials without editing configuration files:

### Via System Properties (Command Line)
```bash
./gradlew bootRun -Dusername=alice -Dpassword=SecurePass123! -DremoteHost=your-server.com -Dcrypto=JWT
```

### Via Environment Variables (Bash/Shell)
```bash
export username=alice
export password=SecurePass123!
export remoteHost=your-server.com
export crypto=JWT
./gradlew bootRun
```

### Via application.properties
```properties
params.username=alice
params.password=SecurePass123!
params.remoteHost=your-server.com
params.crypto=JWT
```

All three methods work identically. System properties override environment variables, which override application.properties.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     LOCAL APPLICATIONS                          │
│  (Browser, curl, CLI tools, etc.)                              │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│                    PROXY CLIENT (This Project)                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌───────────────┐              ┌───────────────┐              │
│  │ SOCKS5 Proxy  │              │  HTTP Proxy   │              │
│  │ Port: 2080    │              │  Port: 9999   │              │
│  └───────┬───────┘              └───────┬───────┘              │
│          │                              │                       │
│          └──────────────┬───────────────┘                       │
│                         ↓                                       │
│              ┌─────────────────────┐                            │
│              │ Illiad Header        │                           │
│              │ + JWT Token          │                           │
│              └─────────┬───────────┘                            │
│                        │                                        │
│                        ↓                                        │
│              ┌─────────────────────┐                            │
│              │  Netty TLS/DTLS     │                           │
│              └─────────┬───────────┘                            │
│                        │                                        │
└────────────────────────┼────────────────────────────────────────┘
                         │
                         ↓ (TLS Connection)
┌─────────────────────────────────────────────────────────────────┐
│                     REMOTE ILLIAD SERVER                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Validates JWT token                                         │
│  2. Checks token not expired                                    │
│  3. Verifies token signature                                    │
│  4. If valid → proxies requests                                 │
│  5. If invalid → rejects connection                             │
│                                                                  │
│  Also provides:                                                 │
│  - User registration API                                        │
│  - Login API                                                    │
│  - Token generation API                                         │
│  - Token renewal API                                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Configuration

### application.properties

```properties
# Remote server connection
params.remoteHost=your-server.com
params.remotePort=443

# Authentication method
params.crypto=JWT

# JWT token (obtained from remote server)
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...

# Local proxy ports
params.local-port=2080    # SOCKS5
httpPort=9999             # HTTP
```

### Alternative: Hash-based Authentication

```properties
# Use hash-based authentication instead of JWT
params.crypto=SHA_256
params.secret=your-shared-secret
```

---

## Project Structure

```
src/main/java/com/illiad/proxy/
├── ProxyApplication.java         # Spring Boot main class
├── Starter.java                  # Netty server setup
├── codec/
│   └── v5/                      # SOCKS5 codecs
│       ├── V5ServerEncoder.java
│       ├── V5CmdReqDecoder.java
│       └── udp/                 # UDP codec
├── handler/
│   ├── http/                    # HTTP proxy handlers
│   │   ├── FrontHandler.java
│   │   └── Socks5AckHandler.java
│   ├── v5/                      # SOCKS5 handlers
│   │   ├── V5CommandHandler.java
│   │   ├── associate/           # UDP ASSOCIATE
│   │   └── forward/             # Connection forwarding
│   ├── dtls/                    # DTLS for UDP
│   └── udp/                     # UDP handlers
├── config/
│   ├── Params.java              # Configuration
│   └── ParamBus.java            # Dependency injection
└── security/
    ├── Secret.java              # Header encoding
    └── Ssl.java                 # TLS/SSL support
```

---

## Dependencies

### Core Dependencies
- **Spring Boot Starter** - Application framework
- **Netty** - All networking (SOCKS5, HTTP, TLS, UDP)
- **Lombok** - Code generation

### NOT Included (Intentionally)
- ❌ Spring Web - Client doesn't provide REST API
- ❌ Jackson - Not needed (no JSON serialization)
- ❌ JWT libraries - Client doesn't validate tokens
- ❌ MongoDB - No local database
- ❌ BCrypt - No password hashing

---

## Security Notes

### Token Storage
- JWT token is stored in `application.properties`
- Token is transmitted in Illiad headers (encrypted via TLS)
- Client does NOT store tokens in database
- If token is compromised, generate new one on remote server

### TLS/DTLS
- All connections to remote server use TLS encryption
- UDP connections can use DTLS if configured
- Self-signed certificates supported for development

### Token Rotation
- When token expires, user must:
  1. Login to remote server
  2. Generate new token
  3. Update `application.properties`
  4. Restart client

---

## Usage Examples

### Example 1: Using with Browser

1. Configure browser to use SOCKS5 proxy: `localhost:2080`
2. Client forwards all requests to remote server with JWT token
3. Remote server validates token and proxies requests
4. Responses flow back through client to browser

### Example 2: Using with curl

```bash
# Via SOCKS5
curl -x socks5://localhost:2080 https://example.com

# Via HTTP proxy
curl -x http://localhost:9999 http://example.com
```

### Example 3: Using with Python requests

```python
import requests

proxies = {
    'http': 'socks5://localhost:2080',
    'https': 'socks5://localhost:2080'
}

response = requests.get('https://example.com', proxies=proxies)
```

---

## Troubleshooting

### "Connection refused"
- Check if client is running: `netstat -tuln | grep 2080`
- Check remote server is accessible
- Verify firewall rules

### "Authentication failed"
- Token may be expired - generate new token on remote server
- Token format incorrect - check for complete token with 3 parts
- Token revoked - generate new token on remote server

### "Token is not configured"
- Set `params.jwtToken` in `application.properties`
- Ensure `params.crypto=JWT` is set

---

## Interacting with Server JWT APIs

While the proxy client itself doesn't implement authentication, users need to interact with the remote server's REST API to obtain JWT tokens. Here's how:

### Server API Endpoints

The remote server provides these authentication endpoints:

```
Base URL: https://your-server.com/api/auth
```

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/auth/register` | POST | Register new user account |
| `/api/auth/login` | POST | Login to get sessionId |
| `/api/auth/logout` | POST | Invalidate session |
| `/api/auth/token/generate` | POST | Generate JWT token (3 methods) |

### Step-by-Step: Getting Your First Token

#### 1. Register a User Account

```bash
curl -k -X POST "https://your-server.com/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePassword123!",
    "email": "alice@example.com"
  }'
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "username": "alice",
    "email": "alice@example.com"
  }
}
```

#### 2. Login to Get Session ID

```bash
curl -k -X POST "https://your-server.com/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePassword123!"
  }'
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "username": "alice",
    "sessionId": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
  }
}
```

#### 3. Generate JWT Token (3 Alternative Methods)

**Method 1: Using SessionId (Recommended)**
```bash
curl -k -X POST "https://your-server.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
    "expirationMinutes": 43200
  }'
```

**Method 2: Using Username + Password (Direct)**
```bash
curl -k -X POST "https://your-server.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePassword123!",
    "expirationMinutes": 43200
  }'
```

**Method 3: Using Current Token (Renewal)**
```bash
curl -k -X POST "https://your-server.com/api/auth/token/generate" \
  -H "Content-Type: application/json" \
  -d '{
    "currentToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expirationMinutes": 43200
  }'
```

**Response (200 OK) - All methods:**
```json
{
  "success": true,
  "message": "Token generated successfully",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0LWU1ZjYtNzg5MC1hYmNkLWVmMTIzNDU2Nzg5MCIsImV4cGlyZXNBdCI6MTczNTg2MjQwMDAwMH0.Xh7j9K2mN4pQ6rS8tU0vW1xY2zA3bC4dE5fF6gG7hH8",
    "expirationMinutes": 43200,
    "expiresAt": "2025-12-08T10:30:00Z"
  }
}
```

#### 4. Configure Proxy Client with Token

Copy the `token` value and add it to your `application.properties`:

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0LWU1ZjYtNzg5MC1hYmNkLWVmMTIzNDU2Nzg5MCIsImV4cGlyZXNBdCI6MTczNTg2MjQwMDAwMH0.Xh7j9K2mN4pQ6rS8tU0vW1xY2zA3bC4dE5fF6gG7hH8
params.remoteHost=your-server.com
params.remotePort=443
```

#### 5. Start Proxy Client

```bash
./gradlew bootRun
```

### Token Expiration Times

| Duration | Minutes | Use Case |
|----------|---------|----------|
| 1 day | 1440 | Testing |
| 1 week | 10080 | Weekly rotation |
| 1 month | 43200 | ⭐ Recommended |
| 3 months | 129600 | Maximum allowed |

### Important Notes About Tokens

1. **Only ONE active token per user**
   - Generating a new token invalidates ALL previous tokens
   - If you lose your token, generate a new one

2. **Token Security**
   - Treat JWT token like a password
   - Anyone with your token can use your proxy access
   - Never share or commit tokens to version control

3. **Token Privacy**
   - Token contains random UUID, not your username
   - Only server can correlate UUID to your account
   - Safe to use on untrusted networks (via TLS)

4. **Token Renewal**
   - Apps can implement periodic renewal using Method 3
   - Recommended: renew every 10-30 minutes
   - Prevents token expiration during long sessions

### Advanced: Periodic Token Renewal

For applications that run continuously, implement periodic token renewal to prevent expiration:

```python
import requests
import time
import threading

class TokenManager:
    def __init__(self, server_url, initial_token):
        self.server_url = server_url
        self.token = initial_token
        self.renewal_interval = 600  # 10 minutes
        
    def start_renewal(self):
        """Start background token renewal thread"""
        def renew_loop():
            while True:
                time.sleep(self.renewal_interval)
                self.renew_token()
        
        thread = threading.Thread(target=renew_loop, daemon=True)
        thread.start()
    
    def renew_token(self):
        """Renew token using current token"""
        try:
            response = requests.post(
                f"{self.server_url}/api/auth/token/generate",
                json={
                    "currentToken": self.token,
                    "expirationMinutes": 43200
                },
                verify=False  # For self-signed certs
            )
            
            if response.status_code == 200:
                data = response.json()
                if data['success']:
                    self.token = data['data']['token']
                    print(f"Token renewed successfully at {time.ctime()}")
                    # Update application.properties or restart client
                else:
                    print(f"Token renewal failed: {data['message']}")
            else:
                print(f"Token renewal failed with status {response.status_code}")
                
        except Exception as e:
            print(f"Token renewal error: {e}")

# Usage
manager = TokenManager(
    "https://your-server.com",
    "eyJhbGciOiJIUzI1NiJ9..."
)
manager.start_renewal()
```

### Error Handling

Common errors when interacting with server APIs:

| HTTP Status | Error Message | Solution |
|-------------|---------------|----------|
| 307 | Temporary Redirect | Don't route API calls through proxy |
| 400 | "Username already exists" | Choose different username |
| 400 | "Must provide one of: sessionId, currentToken, or username+password" | Include valid authentication method |
| 401 | "Invalid username or password" | Check credentials |
| 401 | "Invalid or expired session" | Login again |
| 401 | "Token is expired" | Generate new token with username+password |

**Note on 307 Redirect:** If you try to access the server's API through the proxy itself (self-connection loop), the server will return 307. Always call authentication APIs directly to the HTTPS server, not through the proxy.

### Web-Based Token Generation

For non-technical users, you can create a simple HTML page:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Get Your Proxy Token</title>
</head>
<body>
    <h1>Generate Proxy Token</h1>
    
    <form id="loginForm">
        <h2>Step 1: Login</h2>
        <label>Username:</label>
        <input type="text" id="username" required><br>
        <label>Password:</label>
        <input type="password" id="password" required><br>
        <button type="submit">Login</button>
    </form>
    
    <form id="tokenForm" style="display:none;">
        <h2>Step 2: Generate Token</h2>
        <label>Token Duration:</label>
        <select id="duration">
            <option value="1440">1 day</option>
            <option value="10080">1 week</option>
            <option value="43200" selected>30 days</option>
        </select><br>
        <button type="submit">Generate Token</button>
    </form>
    
    <div id="result" style="display:none;">
        <h2>Your Token</h2>
        <textarea id="token" rows="5" cols="80" readonly></textarea><br>
        <p>Copy this token and paste it into your proxy client configuration.</p>
        <button onclick="copyToken()">Copy to Clipboard</button>
    </div>
    
    <script>
        let sessionId = null;
        
        document.getElementById('loginForm').onsubmit = async (e) => {
            e.preventDefault();
            const response = await fetch('https://your-server.com/api/auth/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    username: document.getElementById('username').value,
                    password: document.getElementById('password').value
                })
            });
            const data = await response.json();
            if (data.success) {
                sessionId = data.data.sessionId;
                document.getElementById('loginForm').style.display = 'none';
                document.getElementById('tokenForm').style.display = 'block';
            } else {
                alert('Login failed: ' + data.message);
            }
        };
        
        document.getElementById('tokenForm').onsubmit = async (e) => {
            e.preventDefault();
            const response = await fetch('https://your-server.com/api/auth/token/generate', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    sessionId: sessionId,
                    expirationMinutes: parseInt(document.getElementById('duration').value)
                })
            });
            const data = await response.json();
            if (data.success) {
                document.getElementById('token').value = data.data.token;
                document.getElementById('tokenForm').style.display = 'none';
                document.getElementById('result').style.display = 'block';
            } else {
                alert('Token generation failed: ' + data.message);
            }
        };
        
        function copyToken() {
            document.getElementById('token').select();
            document.execCommand('copy');
            alert('Token copied to clipboard!');
        }
    </script>
</body>
</html>
```

---

## Comparison with Server

| Feature | Client (This Project) | Remote Server |
|---------|----------------------|---------------|
| **Purpose** | Local proxy | Remote proxy + auth |
| **Listens on** | localhost:2080, localhost:9999 | your-server.com:443 |
| **Provides** | SOCKS5, HTTP proxy | SOCKS5, HTTPS disguise, REST API |
| **Authentication** | Uses JWT token | Validates JWT token |
| **User management** | None | Full (register, login, token gen) |
| **Database** | None | MongoDB (users, tokens) |
| **Web framework** | None | Spring WebFlux |
| **Networking** | Netty | Netty |

---

## Summary

The proxy **client** is a simple local proxy that:
- ✅ Provides SOCKS5 and HTTP proxy on localhost
- ✅ Forwards requests to remote server with JWT token
- ✅ Uses Netty for all networking
- ❌ Does NOT implement authentication
- ❌ Does NOT manage tokens
- ❌ Does NOT provide REST API

Token management happens on the **remote server** - users register, login, and generate tokens there, then configure this client with the token.

