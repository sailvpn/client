# Configuration Guide for Developers

This guide is for **developers** working with the proxy client source code.

> **👤 Are you an end user?** See [CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md) for end-user configuration (installed applications, JAR/DEB/TAR packages, etc.)

---

## Quick Start (Developers)

### Step 1: Clone/Download the Source Code

```bash
git clone <repository-url>
cd proxy
```

### Step 2: Create Configuration File

**Default location:**
```
src/main/resources/application.properties
```

**Copy the template:**
```bash
cp config-template.properties src/main/resources/application.properties
```

### Step 3: Edit Configuration

```bash
# Use your preferred editor
nano src/main/resources/application.properties
code src/main/resources/application.properties
vim src/main/resources/application.properties
```

### Step 4: Run with Gradle

```bash
./gradlew bootRun
```

---

## Configuration File Locations

### Option 1: Default Location (Recommended)

**Location:**
```
<project-root>/src/main/resources/application.properties
```

**Full path examples:**
- **Linux:** `/home/john/proxy/src/main/resources/application.properties`
- **macOS:** `/Users/john/proxy/src/main/resources/application.properties`
- **Windows:** `C:\Users\John\proxy\src\main\resources\application.properties`

**Advantages:**
- ✅ No command-line arguments needed
- ✅ Spring Boot finds it automatically
- ✅ Standard Spring Boot convention

**Usage:**
```bash
./gradlew bootRun
```

---

### Option 2: Custom Location

**Location:** Anywhere in your filesystem

**Common development locations:**
- **Linux:** `~/.config/proxy/dev-config.properties`
- **macOS:** `~/Library/Application Support/proxy/config.properties`
- **Windows:** `%APPDATA%\proxy\config.properties`

**Usage:**
```bash
./gradlew bootRun --spring.config.location=/path/to/config.properties

# Or with Spring Boot's argument:
./gradlew bootRun -Dspring.config.location=/path/to/config.properties
```

---

### Option 3: Environment Variables

**No config file needed for development.**

```bash
export crypto=JWT
export tokenMode=auto
export username=alice
export password=SecurePass123
export remoteHost=proxy.example.com
export remotePort=443

./gradlew bootRun
```

**Advantages:**
- ✅ Keeps sensitive data out of version control
- ✅ Easy to switch configurations
- ✅ CI/CD friendly

---

### Option 4: External Configuration Directory

**Create a config directory outside the project:**

```bash
mkdir -p ~/proxy-configs/
```

**Create different configs:**
```
~/proxy-configs/
  ├── dev.properties          # Local development
  ├── staging.properties      # Staging environment
  └── production.properties   # Production settings
```

**Switch between them:**
```bash
# Development
./gradlew bootRun --spring.config.location=~/proxy-configs/dev.properties

# Staging
./gradlew bootRun --spring.config.location=~/proxy-configs/staging.properties
```

---

## Configuration Priority

Spring Boot loads configuration in this order (later sources override earlier ones):

1. **Default properties** (hardcoded in code)
2. **`application.properties`** in `src/main/resources/`
3. **External config file** (via `--spring.config.location`)
4. **Environment variables**
5. **System properties** (via `-D` flags)
6. **Command-line arguments**

**Example of override:**
```bash
# application.properties has:
params.localPort=2080

# Override with environment variable:
export localPort=3080
./gradlew bootRun
# localPort will be 3080

# Override with system property:
./gradlew bootRun -DlocalPort=4080
# localPort will be 4080
```

---

## Development Configurations

### Local Development (Automatic Mode)

**File:** `src/main/resources/application.properties`

```properties
# Local dev server
params.remoteHost=localhost
params.remotePort=2080

# Test credentials
params.crypto=JWT
params.tokenMode=auto
params.username=dev-user
params.password=dev-password

# Dev ports (avoid conflicts)
params.localPort=3080
params.httpPort=8888

# Verbose logging
logging.level.com.illiad.proxy=DEBUG
logging.level.io.netty=DEBUG
```

---

### Integration Testing (Manual Mode)

**File:** `src/main/resources/application-test.properties`

```properties
# Test server
params.remoteHost=test-server.local
params.remotePort=443

# Fixed token for testing
params.crypto=JWT
params.tokenMode=manual
params.jwtToken=eyJhbGciOiJIUzI1NiJ9.test-token-for-integration-tests

# Test ports
params.localPort=9080
params.httpPort=9999

# Test logging
logging.level.com.illiad.proxy=TRACE
```

**Run with test profile:**
```bash
./gradlew bootRun -Dspring.profiles.active=test
```

---

### Production-like Configuration

**File:** `~/proxy-configs/staging.properties`

```properties
# Staging server
params.remoteHost=staging.example.com
params.remotePort=443

# Use environment variables for credentials
params.crypto=JWT
params.tokenMode=auto
params.username=${PROXY_USERNAME}
params.password=${PROXY_PASSWORD}

# Standard ports
params.localPort=2080
params.httpPort=9999

# Production logging
logging.level.com.illiad.proxy=INFO
```

**Run:**
```bash
export PROXY_USERNAME=staging-user
export PROXY_PASSWORD=staging-pass
./gradlew bootRun --spring.config.location=~/proxy-configs/staging.properties
```

---

## Version Control Best Practices

### .gitignore Configuration

**Add to `.gitignore`:**
```gitignore
# Ignore actual configuration files with secrets
src/main/resources/application.properties
src/main/resources/application-*.properties

# Keep template
!config-template.properties

# Ignore external configs
**/proxy-configs/
```

### Committing Configuration

**DO:**
- ✅ Commit `config-template.properties` with placeholder values
- ✅ Commit `application.properties.example` files
- ✅ Document required configuration in README

**DON'T:**
- ❌ Commit `application.properties` with real credentials
- ❌ Commit tokens or passwords
- ❌ Commit server addresses for production

### Template File

**Commit this as `config-template.properties`:**
```properties
# Proxy Client Configuration Template
# Copy to src/main/resources/application.properties and fill in values

# Server settings
params.remoteHost=REPLACE_WITH_SERVER_ADDRESS
params.remotePort=443

# Authentication
params.crypto=JWT
params.tokenMode=auto
params.username=REPLACE_WITH_USERNAME
params.password=REPLACE_WITH_PASSWORD

# Local ports
params.localPort=2080
params.httpPort=9999
```

---

## IDE-Specific Configuration

### IntelliJ IDEA

**Create a Run Configuration:**

1. Run → Edit Configurations
2. Add New → Spring Boot
3. Main class: `com.illiad.proxy.ProxyApplication`
4. **Environment variables:**
   ```
   crypto=JWT;
   tokenMode=auto;
   username=dev-user;
   password=dev-pass;
   remoteHost=localhost
   ```
5. **VM options:**
   ```
   -Dspring.config.location=/path/to/dev-config.properties
   ```
6. Apply → OK

**Share run configurations:**
Add to `.idea/runConfigurations/` and commit (if no secrets).

---

### VS Code

**Create `.vscode/launch.json`:**
```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot-ProxyApplication",
      "request": "launch",
      "cwd": "${workspaceFolder}",
      "mainClass": "com.illiad.proxy.ProxyApplication",
      "projectName": "proxy",
      "args": "--spring.config.location=${workspaceFolder}/dev-config.properties",
      "env": {
        "crypto": "JWT",
        "tokenMode": "auto",
        "username": "dev-user",
        "password": "dev-pass",
        "remoteHost": "localhost"
      }
    }
  ]
}
```

**Don't commit if it contains secrets!**

---

### Eclipse

**Create a Run Configuration:**

1. Run → Run Configurations
2. Java Application → New
3. Main class: `com.illiad.proxy.ProxyApplication`
4. Arguments tab:
   - Program arguments: `--spring.config.location=/path/to/config.properties`
   - VM arguments: `-Dusername=dev-user -Dpassword=dev-pass`
5. Environment tab: Add variables
6. Apply → Run

---

## Advanced Development Configurations

### Multiple Profiles

**application.properties (base):**
```properties
# Common settings for all profiles
params.crypto=JWT
params.localPort=2080
params.httpPort=9999
logging.level.com.illiad.proxy=INFO
```

**application-dev.properties:**
```properties
# Development overrides
params.remoteHost=localhost
params.remotePort=2080
params.tokenMode=auto
params.username=dev
params.password=dev
logging.level.com.illiad.proxy=DEBUG
```

**application-prod.properties:**
```properties
# Production overrides
params.remoteHost=${PROXY_HOST}
params.remotePort=443
params.tokenMode=auto
params.username=${PROXY_USER}
params.password=${PROXY_PASS}
logging.level.com.illiad.proxy=WARN
```

**Run with profile:**
```bash
./gradlew bootRun -Dspring.profiles.active=dev
./gradlew bootRun -Dspring.profiles.active=prod
```

---

### Configuration with Docker

**Dockerfile:**
```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY build/libs/proxy.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml:**
```yaml
version: '3.8'
services:
  proxy:
    build: .
    environment:
      - crypto=JWT
      - tokenMode=auto
      - username=${PROXY_USERNAME}
      - password=${PROXY_PASSWORD}
      - remoteHost=${PROXY_HOST}
      - remotePort=443
    ports:
      - "2080:2080"
      - "9999:9999"
```

**.env file (not committed):**
```env
PROXY_USERNAME=alice
PROXY_PASSWORD=SecurePass123
PROXY_HOST=proxy.example.com
```

**Run:**
```bash
docker-compose up
```

---

### Kubernetes Configuration

**ConfigMap:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: proxy-config
data:
  crypto: "JWT"
  tokenMode: "auto"
  remoteHost: "proxy.example.com"
  remotePort: "443"
  localPort: "2080"
  httpPort: "9999"
```

**Secret:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: proxy-credentials
type: Opaque
stringData:
  username: alice
  password: SecurePass123
```

**Deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: proxy
spec:
  replicas: 1
  selector:
    matchLabels:
      app: proxy
  template:
    metadata:
      labels:
        app: proxy
    spec:
      containers:
      - name: proxy
        image: proxy:latest
        envFrom:
        - configMapRef:
            name: proxy-config
        - secretRef:
            name: proxy-credentials
        ports:
        - containerPort: 2080
        - containerPort: 9999
```

---

## Testing Configurations

### Unit Tests

**src/test/resources/application-test.properties:**
```properties
# Test configuration (no real connections)
params.crypto=JWT
params.tokenMode=manual
params.jwtToken=test-token-12345
params.remoteHost=localhost
params.remotePort=9999
params.localPort=0  # Random available port
params.httpPort=0   # Random available port
```

**In test code:**
```java
@SpringBootTest
@ActiveProfiles("test")
class ProxyApplicationTests {
    // Tests use test configuration
}
```

---

### Integration Tests

**Use Testcontainers for full integration:**

```java
@Testcontainers
@SpringBootTest
class IntegrationTest {
    @Container
    static GenericContainer<?> proxyServer = 
        new GenericContainer<>("proxy-server:latest")
            .withExposedPorts(2080);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("params.remoteHost", 
            proxyServer::getHost);
        registry.add("params.remotePort", 
            () -> proxyServer.getMappedPort(2080));
    }
}
```

---

## Troubleshooting (Developers)

### Configuration Not Loading

**Check classpath:**
```bash
./gradlew bootRun --debug 2>&1 | grep "application.properties"
```

**Verify file location:**
```bash
ls -la src/main/resources/application.properties
```

**Check for typos:**
- File must be named exactly `application.properties`
- Parameters must use correct prefix: `params.`

---

### Overriding Not Working

**Check priority order:**
```bash
# This will show which configuration sources are loaded
./gradlew bootRun --debug 2>&1 | grep -A 10 "property sources"
```

**Verify parameter names:**
```bash
# System property (no params. prefix):
-DremoteHost=localhost

# Environment variable (no params. prefix):
export remoteHost=localhost

# Config file (with params. prefix):
params.remoteHost=localhost
```

---

### Lombok Not Generating Getters

If `params.getUsername()` doesn't exist:

1. **Verify Lombok is in build.gradle.kts:**
   ```kotlin
   compileOnly("org.projectlombok:lombok")
   annotationProcessor("org.projectlombok:lombok")
   ```

2. **Rebuild:**
   ```bash
   ./gradlew clean build
   ```

3. **IDE plugin:**
   - IntelliJ: Install Lombok plugin
   - Eclipse: Install Lombok jar
   - VS Code: Install Java extension with Lombok support

---

## File Permissions (Linux/macOS)

**Protect configuration files:**
```bash
chmod 600 src/main/resources/application.properties
chmod 600 ~/proxy-configs/*.properties
```

**Verify:**
```bash
ls -l src/main/resources/application.properties
# Should show: -rw------- (owner read/write only)
```

---

## Configuration Validation

### At Build Time

**Create a test:**
```java
@Test
void validateConfiguration() {
    assertNotNull(params.getRemoteHost(), "remoteHost must be configured");
    assertNotNull(params.getCrypto(), "crypto must be configured");
    if (params.getTokenMode().equals("auto")) {
        assertNotNull(params.getUsername(), "username required in auto mode");
        assertNotNull(params.getPassword(), "password required in auto mode");
    }
}
```

---

### At Runtime

**Add validation in TokenManager:**
```java
@PostConstruct
public void validate() {
    if (params.getCrypto().equals("JWT")) {
        if (params.getTokenMode().equals("auto")) {
            Assert.hasText(params.getUsername(), 
                "username is required when tokenMode=auto");
            Assert.hasText(params.getPassword(), 
                "password is required when tokenMode=auto");
        } else if (params.getTokenMode().equals("manual")) {
            Assert.hasText(params.getJwtToken(), 
                "jwtToken is required when tokenMode=manual");
        }
    }
}
```

---

## See Also

- **[CONFIGURATION_GUIDE.md](CONFIGURATION_GUIDE.md)** - End-user configuration guide
- **[JWT_AUTO_MANAGEMENT.md](JWT_AUTO_MANAGEMENT.md)** - JWT feature documentation
- **[TOKEN_IMPLEMENTATION.md](TOKEN_IMPLEMENTATION.md)** - Technical implementation details

---

**Last Updated:** December 5, 2025  
**Audience:** Developers  
**Related:** End-user guide available in CONFIGURATION_GUIDE.md

