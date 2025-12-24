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

# Set mode to automatic
params.tokenMode=auto

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

(Full content copied from `JWT_AUTO_MANAGEMENT.md`)

