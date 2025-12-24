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

(Full content copied from `JWT_SETUP.md`)

