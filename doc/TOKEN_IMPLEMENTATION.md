# JWT Authentication Implementation Summary

## What Was Added

JWT (JSON Web Token) authentication support has been added to the proxy client, allowing it to authenticate with the remote Illiad proxy server using user-specific tokens instead of shared secret hashes.

## Files Modified

### 1. `src/main/java/com/illiad/proxy/security/Cryptos.java`
- **Added:** `JWT("JWT")` enum value at the beginning of the enum
- **Purpose:** Defines JWT as a supported crypto type

### 2. `src/main/java/com/illiad/proxy/security/CryptoByte.java`
- **Added:** JWT case in `toByte()` method - returns `0x07`
- **Added:** JWT case in `toCrypto()` method - maps `0x07` to `Cryptos.JWT`
- **Added:** JWT case in `byteLength()` method - returns `0` (variable length)
- **Purpose:** Handles byte encoding/decoding for JWT crypto type

### 3. `src/main/java/com/illiad/proxy/security/SecretImp.java`
- **Modified:** `getSecret()` method to detect JWT crypto type
- **Added:** Logic to return JWT token bytes instead of hash when `crypto=JWT`
- **Purpose:** Provides JWT token as the "secret" for the Illiad header

### 4. `src/main/java/com/illiad/proxy/config/Params.java`
- **Added:** `jwtToken` field with system property support
- **Purpose:** Stores the JWT token from configuration

### 5. `src/main/resources/application.properties`
- **Added:** Comments explaining JWT configuration
- **Purpose:** Documentation for users

## Files Created

### 1. `JWT_SETUP.md`
- Comprehensive user guide for JWT authentication setup
- Step-by-step instructions
- Troubleshooting guide
- Technical implementation details

### 2. `application.properties.jwt.example`
- Example configuration file
- Shows JWT and hash-based authentication options
- Includes helpful comments and token generation instructions

## How It Works

### Protocol Flow

1. **User Configuration:**
   ```properties
   params.crypto=JWT
   params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
   ```

2. **Header Encoding:**
   - `SecretImp.getSecret()` detects `crypto=JWT`
   - Returns JWT token as UTF-8 bytes
   - `HeaderEncoder` encodes in Illiad protocol format:
     ```
     [2-byte length][0x07][JWT token bytes][random offset][CRLF]
     ```

3. **Server Validation:**
   - Remote server receives header
   - Extracts JWT token
   - Validates signature and expiration
   - Looks up user in database
   - Grants/denies access

## Security Considerations

✅ **Token Encryption:** JWT tokens are sent over TLS, so they're encrypted in transit  
✅ **No Token Storage:** Proxy doesn't store tokens - only reads from configuration  
✅ **Token Privacy:** JWT contains random UUID, not user information  
✅ **Expiration Enforcement:** Server validates token expiration  
✅ **Revocation Support:** Generating new token invalidates all previous tokens  

⚠️ **User Responsibilities:**
- Keep JWT tokens secret (like passwords)
- Don't commit tokens to version control
- Rotate tokens regularly (e.g., every 30 days)
- Generate new token if compromised

## Server Compatibility

This implementation follows the Illiad protocol JWT specification:
- **Crypto Type:** `0x07` (matching server's `HeaderDecoder.java`)
- **Header Format:** Variable-length header with token as UTF-8 bytes
- **Token Structure:** Standard JWT with `id` and `expiresAt` claims
- **Validation:** Server-side using `JwtService` and `SecretImp`

## Future Enhancements

Potential improvements for future versions:
- [ ] Token expiration warning (notify user before token expires)
- [ ] Automatic token refresh (if server supports refresh tokens)
- [ ] Token validation on client side (early detection of expired tokens)
- [ ] Multiple token support (fallback tokens)
- [ ] Encrypted token storage (secure local storage)

## Documentation

For users:
- Read `TOKEN_SETUP.md` for setup instructions
- See `application.properties.jwt.example` for configuration examples
- Refer to server documentation for token generation

For developers:
- JWT protocol: `/doc/JWT_AUTHENTICATION.md` (server project)
- Illiad header format: `HeaderEncoder.java` comments
- Server validation: `HeaderDecoder.java` (server project)

## Support

If you encounter issues:
1. Check `TOKEN_SETUP.md` troubleshooting section
2. Verify token format and expiration at https://jwt.io
3. Check server logs for authentication errors
4. Ensure server supports JWT authentication (crypto type 0x07)

---

**Implementation completed:** December 5, 2025  
**Status:** ✅ Compiled and ready for testing  
**Backward compatibility:** ✅ Hash-based authentication still works

