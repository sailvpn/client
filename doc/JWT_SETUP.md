# JWT Authentication Setup for Proxy Client

## Overview

This proxy client now supports JWT (JSON Web Token) authentication when connecting to the remote Illiad proxy server. This allows for user-based authentication instead of shared secret hashes.

## How It Works

When you configure the proxy to use JWT authentication:
1. The proxy client encodes your JWT token in the custom Illiad protocol header
2. The header is sent to the remote server over TLS
3. The remote server validates the JWT token and grants access if valid

## Configuration

### Step 1: Obtain a JWT Token from the Server

You need to register an account and generate a JWT token from the remote server. See the server documentation for details on:
- User registration
- Login
- Token generation

Example token:
```
eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0LWU1ZjYtNzg5MC1hYmNkLWVmMTIzNDU2Nzg5MCIsImV4cGlyZXNBdCI6MTczNTg2MjQwMDAwMH0.Xh7j9K2mN4pQ6rS8tU0vW1xY2zA3bC4dE5fF6gG7hH8
```

### Step 2: Configure the Proxy

You have three options to configure JWT authentication:

#### Option 1: application.properties

Edit `src/main/resources/application.properties`:

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0LWU1ZjYtNzg5MC1hYmNkLWVmMTIzNDU2Nzg5MCIsImV4cGlyZXNBdCI6MTczNTg2MjQwMDAwMH0.Xh7j9K2mN4pQ6rS8tU0vW1xY2zA3bC4dE5fF6gG7hH8
```

#### Option 2: System Properties

Run the application with system properties:

```bash
./gradlew bootRun -Dcrypto=JWT -DjwtToken="eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0..."
```

#### Option 3: Environment Variables

Set environment variables:

```bash
export crypto=JWT
export jwtToken="eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0..."
./gradlew bootRun
```

### Step 3: Run the Proxy

Start the proxy as usual:

```bash
./gradlew bootRun
```

The proxy will now authenticate using your JWT token when connecting to the remote server.

## Important Notes

### Token Security

- **Keep your JWT token secret** - it's like a password
- Don't commit tokens to version control (use .gitignore for config files with tokens)
- Tokens are sent over TLS, so they're encrypted in transit
- Store tokens securely (e.g., in a password manager)

### Token Expiration

- JWT tokens have an expiration time set when generated
- When a token expires, you must generate a new one from the server
- The proxy will fail to connect if the token is expired
- Check token expiration using https://jwt.io (decode only - don't paste sensitive tokens on public sites)

### Token Rotation

- Generating a new token on the server **invalidates all previous tokens**
- Only one token is active per user at a time
- Update the proxy configuration when you generate a new token

### Switching Between Authentication Methods

You can switch between JWT and hash-based authentication by changing the `crypto` parameter:

**JWT Authentication:**
```properties
params.crypto=JWT
params.jwtToken=your-jwt-token-here
```

**Hash-based Authentication (old method):**
```properties
params.crypto=SHA_256
params.secret=your-shared-secret
```

## Troubleshooting

### "JWT token is not configured" Error

**Problem:** You set `crypto=JWT` but didn't provide a JWT token.

**Solution:** Set the `jwtToken` parameter in application.properties or as a system property.

### Connection Refused or Authentication Failed

**Possible causes:**
1. Token has expired - generate a new token
2. Token was revoked - generate a new token
3. Token is invalid - check for typos or incomplete token string
4. Remote server doesn't support JWT - verify server version

### Token Format Issues

**Valid JWT token format:**
- Three parts separated by dots: `header.payload.signature`
- Only contains: letters, numbers, `-`, `_`, and `.`
- Example length: 150-200 characters

**Invalid tokens:**
- Missing parts (should have 2 dots)
- Contains spaces or newlines
- Truncated or incomplete

## Example: Complete Setup

1. **Register on the server:**
```bash
curl -k -X POST "https://server.example.com/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "SecurePass123!", "email": "alice@example.com"}'
```

2. **Login to get user ID:**
```bash
curl -k -X POST "https://server.example.com/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "SecurePass123!"}'
```

Response: `{"success": true, "data": {"userId": "6748a1b2c3d4e5f678901234", ...}}`

3. **Generate JWT token (30 days expiration):**
```bash
curl -k -X POST "https://server.example.com/api/auth/token/generate?userId=6748a1b2c3d4e5f678901234" \
  -H "Content-Type: application/json" \
  -d '{"expirationMinutes": 43200}'
```

Response: `{"success": true, "data": {"token": "eyJhbGciOiJIUzI1NiJ9...", ...}}`

4. **Configure proxy client (`application.properties`):**
```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6ImExYjJjM2Q0LWU1ZjYtNzg5MC1hYmNkLWVmMTIzNDU2Nzg5MCIsImV4cGlyZXNBdCI6MTczNTg2MjQwMDAwMH0.Xh7j9K2mN4pQ6rS8tU0vW1xY2zA3bC4dE5fF6gG7hH8
params.remoteHost=server.example.com
params.remotePort=443
```

5. **Start proxy:**
```bash
./gradlew bootRun
```

6. **Use the proxy:**
Configure your applications to use SOCKS5 proxy at `localhost:3080` (default) or HTTP proxy at `localhost:9999` (default).

## Benefits of JWT Authentication

✅ **User-based authentication** - each user has unique credentials  
✅ **Token revocation** - generate new token to invalidate old ones  
✅ **Expiration control** - set token lifetime (1 day to 3 months)  
✅ **No shared secrets** - each user has independent tokens  
✅ **Auditability** - server can track usage by user  
✅ **Privacy** - token contains random UUID, not user information  

## Technical Details

### Illiad Protocol Header with JWT

The proxy encodes the JWT in the Illiad protocol header format:

```
+--------+--------+--------+--------+--------+--------+--------+
| Length (2 bytes)| Crypto | JWT Token (variable) | Offset | CRLF |
+--------+--------+--------+--------+--------+--------+--------+
```

- **Length (2 bytes)**: Length of (2 + 1 + JWT token length)
- **Crypto (1 byte)**: `0x07` for JWT
- **JWT Token (variable)**: The JWT token as UTF-8 bytes
- **Offset (variable)**: Random padding (1-128 bytes)
- **CRLF (2 bytes)**: `0x0D 0x0A`

### Code Implementation

The JWT support is implemented in:
- `Cryptos.java` - Added `JWT` enum value
- `CryptoByte.java` - Maps JWT to byte `0x07`, returns length `0` (variable)
- `SecretImp.java` - Returns JWT token bytes instead of hash when crypto=JWT
- `HeaderEncoder.java` - Encodes JWT in Illiad header format (already supports variable-length)
- `Params.java` - Added `jwtToken` configuration field

No changes needed to `HeaderEncoder` as it already supports variable-length signatures (when `getCryptoLength()` returns 0).

## Support

For server-side JWT setup and user management, see the server documentation at:
- `/doc/JWT_AUTHENTICATION.md`
- `/doc/JWT_TOKEN_USER_GUIDE.md`

For issues with the proxy client, check:
- Application logs for error messages
- Token expiration at https://jwt.io (decode only)
- Server connectivity and TLS configuration

