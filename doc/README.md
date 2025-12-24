# Proxy Client Documentation

This directory contains documentation for the Illiad proxy client.

## JWT Authentication Documentation

### Quick Start
- **[TOKEN_QUICK_REFERENCE.md](TOKEN_QUICK_REFERENCE.md)** - One-page quick reference with essential commands and configuration
- **[TOKEN_AUTO_MANAGEMENT.md](TOKEN_AUTO_MANAGEMENT.md)** - ⭐ **NEW!** Automatic token acquisition and renewal guide

### User Guides
- **[TOKEN_SETUP.md](TOKEN_SETUP.md)** - Manual token setup guide (legacy method)
  - Configuration options
  - Step-by-step instructions
  - Troubleshooting guide
  - Security best practices
- **[CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md)** - ⭐ Configuration guide for **END USERS**
  - For users who install packaged apps (JAR, DEB, TAR, ZIP)
  - Where to place config files on PC
  - File permissions and security
  - Common scenarios (single PC, multiple PCs, family sharing)
  - Simple troubleshooting

### Developer Guides
- **[CONFIGURATION_GUIDE_DEVELOPERS.md](CONFIGURATION_GUIDE_DEVELOPERS.md)** - ⭐ Configuration guide for **DEVELOPERS**
  - For developers working with source code
  - src/main/resources/application.properties
  - Gradle/IDE integration
  - Environment variables and Spring profiles
  - Docker/Kubernetes configurations
  - Testing configurations

### Technical Documentation
- **[TOKEN_IMPLEMENTATION.md](TOKEN_IMPLEMENTATION.md)** - Implementation details for developers
  - Files modified
  - Architecture overview
  - Protocol flow
  - Testing instructions
- **[CLIENT_ARCHITECTURE.md](CLIENT_ARCHITECTURE.md)** - Complete client architecture overview
- **[HTTP_307_REDIRECT_HANDLING.md](HTTP_307_REDIRECT_HANDLING.md)** - HTTP redirect handling implementation

## Quick Links

### For First-Time Users (Recommended Path)
1. Read **[TOKEN_QUICK_REFERENCE.md](TOKEN_QUICK_REFERENCE.md)** for a 30-second setup
2. Or read **[TOKEN_AUTO_MANAGEMENT.md](TOKEN_AUTO_MANAGEMENT.md)** for detailed automatic setup
3. Alternative: [TOKEN_SETUP.md](TOKEN_SETUP.md) for manual token management (legacy)

### For Developers
1. Check [TOKEN_IMPLEMENTATION.md](TOKEN_IMPLEMENTATION.md) for implementation details
2. Review [CLIENT_ARCHITECTURE.md](CLIENT_ARCHITECTURE.md) for overall architecture
3. Review code changes in the modified files listed in the implementation doc

### Example Configuration
- See `../application.properties.jwt.example` for configuration examples

## Authentication Methods

This proxy client supports two authentication methods:

### 1. JWT Authentication (User-based) ⭐ Recommended

**NEW: Automatic Token Management**

Simply provide your username and password - the client handles everything else:

```properties
params.crypto=JWT
params.username=alice
params.password=SecurePassword123!
params.remoteHost=your-server.com
params.remotePort=443
```

Features:
- ✅ Automatic token acquisition at startup
- ✅ Automatic token renewal (every 10 minutes by default)
- ✅ No manual token management required
- ✅ Seamless background updates

**Legacy: Manual Token Management**

Manually acquire and configure JWT tokens:

```properties
params.crypto=JWT
params.jwtToken=eyJhbGciOiJIUzI1NiJ9...
```

Features:
- Each user has unique credentials
- Token-based with expiration
- Can be revoked/rotated
- Requires manual token updates

### 2. Hash-based Authentication (Shared secret)
- Simple shared secret
- No user management
- No expiration
- Suitable for personal/testing use

**Configuration:**
```properties
params.crypto=SHA_256
params.secret=your-shared-secret
```

## Server Documentation

For server-side documentation, see the server project's `doc` directory:
- `/server/doc/JWT_AUTHENTICATION.md` - Server JWT implementation
- `/server/doc/JWT_TOKEN_USER_GUIDE.md` - Token management guide
- `/server/doc/` - Full server documentation index

## Support

- **Questions?** Check the troubleshooting sections in the guides
- **Bug reports?** Include error messages and configuration details
- **Feature requests?** Describe your use case and requirements

---

Last updated: December 5, 2025
