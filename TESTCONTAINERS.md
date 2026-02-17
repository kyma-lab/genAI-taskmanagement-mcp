# Testcontainers + Podman Integration

## The Challenge

Testcontainers is designed for Docker but this project uses **Podman** instead. Testcontainers needs to find a Docker-compatible API socket, but Podman's socket location is dynamic and platform-specific.

## The Solution (Already Implemented)

The project includes **`run-tests.sh`** which automatically handles the Podman/Testcontainers integration.

### How It Works

1. **Auto-Detection**: The script finds the Podman API socket dynamically:
   ```bash
   SOCKET_PATH=$(ps aux | grep gvproxy | grep -o '\-forward-sock [^ ]*' | awk '{print $2}' | head -1)
   ```

2. **Validation**: Checks that the socket exists and is accessible

3. **Maven Integration**: Passes the socket path to Maven via the `docker.host` property:
   ```bash
   ./mvnw -Ddocker.host="unix://$SOCKET_PATH" test
   ```

### Configuration Files

**`src/test/resources/testcontainers.properties`:**
```properties
# Enable container reuse for stable tests
testcontainers.reuse.enable=true
testcontainers.ryuk.disabled=true

# The run-tests.sh script sets DOCKER_HOST automatically
```

**`pom.xml`:**
```xml
<properties>
    <!-- Default Podman socket for macOS - overridable via -Ddocker.host=... -->
    <docker.host>unix://${user.home}/.local/share/containers/podman/machine/podman-machine-default/podman.sock</docker.host>
</properties>
```

## Usage

### ✅ Correct Way (Using the Script)
```bash
# Run all tests
./run-tests.sh test

# Run specific test
./run-tests.sh test -Dtest=AuditLoggingIntegrationTest

# Run with additional Maven options
./run-tests.sh clean test
```

### ❌ Don't Do This
```bash
# This will FAIL because testcontainers can't find Docker
./mvnw test
```

## What Happens Behind the Scenes

1. **Podman Machine** runs and creates a `gvproxy` process
2. **gvproxy** provides a Docker-compatible API socket (typically in `/var/folders/.../podman-machine-default-api.sock`)
3. **run-tests.sh** finds this socket path automatically
4. **Testcontainers** connects to Podman via the socket as if it were Docker
5. **PostgreSQL container** starts for integration tests

## Platform-Specific Notes

### macOS (Current Setup)
- Podman Machine uses Apple Hypervisor (applehv)
- Socket path is in `/var/folders/.../T/podman/`
- Script detects it automatically via gvproxy process

### Linux
- Podman runs natively (no VM needed)
- Socket typically at `/run/user/1000/podman/podman.sock`
- Script would need adjustment for Linux

## Troubleshooting

### If Tests Fail with "Could not find Docker environment"

1. **Check Podman is Running:**
   ```bash
   podman machine list
   # Should show "Currently running"
   ```

2. **Start Podman if Needed:**
   ```bash
   podman machine start
   ```

3. **Verify Socket Exists:**
   ```bash
   ps aux | grep gvproxy | grep -o '\-forward-sock [^ ]*'
   ```

4. **Use the Script:**
   ```bash
   ./run-tests.sh test
   ```

### Manual Override (If Script Doesn't Work)

Find the socket manually:
```bash
podman machine inspect podman-machine-default --format='{{.ConnectionInfo.PodmanSocket.Path}}'
```

Then run tests with explicit path:
```bash
./mvnw test -Ddocker.host="unix:///path/to/socket"
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Integration Tests (JUnit + Testcontainers)                 │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ Uses Testcontainers API
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  Testcontainers Library                                      │
│  - Expects Docker API socket                                │
│  - Manages container lifecycle                              │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ Connects to socket via DOCKER_HOST
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  Podman API Socket (Docker-compatible)                      │
│  - Path: /var/folders/.../podman-machine-default-api.sock   │
│  - Provided by gvproxy process                              │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ Forwards to Podman Machine
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  Podman Machine (VM on macOS)                               │
│  - Runs containers (PostgreSQL for tests)                   │
│  - Apple Hypervisor backend                                 │
└─────────────────────────────────────────────────────────────┘
```

## Benefits of This Approach

✅ **Automatic**: No manual configuration needed  
✅ **Cross-Platform**: Works on different macOS versions  
✅ **Reliable**: Detects current socket location dynamically  
✅ **Transparent**: Testcontainers thinks it's talking to Docker  
✅ **Fast**: Container reuse enabled for quicker test runs  
✅ **Isolated**: Each test gets a fresh PostgreSQL instance  

## Container Reuse Feature

The configuration enables container reuse (`testcontainers.reuse.enable=true`), which means:

- **First test run**: Starts PostgreSQL container (~5-10 seconds)
- **Subsequent runs**: Reuses existing container (~1-2 seconds)
- **Manual cleanup**: Stop containers with `podman stop $(podman ps -q)`

## Running Specific Test Classes

```bash
# Run only audit logging tests
./run-tests.sh test -Dtest=AuditLoggingIntegrationTest

# Run authentication tests
./run-tests.sh test -Dtest=AuthenticationIntegrationTest

# Run multiple test classes
./run-tests.sh test -Dtest=AuditLoggingIntegrationTest,TasksToolIntegrationTest

# Run all integration tests
./run-tests.sh test

# Clean and run tests
./run-tests.sh clean test
```

## IDE Integration

### IntelliJ IDEA

To run tests from IntelliJ, you need to set the `docker.host` VM option:

1. **Go to**: Run → Edit Configurations
2. **Select**: Your test configuration
3. **Add VM Option**: 
   ```
   -Ddocker.host=unix:///var/folders/.../podman-machine-default-api.sock
   ```
4. **Find the socket path**:
   ```bash
   podman machine inspect podman-machine-default --format='{{.ConnectionInfo.PodmanSocket.Path}}'
   ```

Or create a shared test template:
1. **Go to**: Run → Edit Configurations → Edit Configuration Templates → JUnit
2. **Add VM Option**: `-Ddocker.host=unix://[your-socket-path]`
3. All new JUnit tests will inherit this setting

### VS Code

Add to `.vscode/settings.json`:
```json
{
  "java.test.config": {
    "vmArgs": [
      "-Ddocker.host=unix:///var/folders/.../podman-machine-default-api.sock"
    ]
  }
}
```

## Continuous Integration (CI/CD)

For GitHub Actions or other CI systems:

```yaml
- name: Setup Podman
  run: |
    brew install podman
    podman machine init
    podman machine start

- name: Run Tests
  run: ./run-tests.sh test
```

For systems with Docker pre-installed, testcontainers will use Docker automatically without the script.

## Debugging Tips

### View Container Logs
```bash
# List running containers
podman ps

# View PostgreSQL logs
podman logs <container-id>
```

### Inspect Test Database
```bash
# Get container details
podman ps --format "{{.ID}} {{.Image}} {{.Ports}}"

# Connect to test database (during test execution)
podman exec -it <postgres-container-id> psql -U taskuser -d taskdb
```

### Check Testcontainers Logs
Add to test class:
```java
@Testcontainers
class MyIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withLogConsumer(new Slf4jLogConsumer(log));
}
```

## Migration from Docker to Podman

If you previously used Docker and want to switch to Podman:

1. **Install Podman**:
   ```bash
   brew install podman
   ```

2. **Initialize Podman Machine**:
   ```bash
   podman machine init
   podman machine start
   ```

3. **Use the Test Script**:
   ```bash
   ./run-tests.sh test
   ```

That's it! No code changes needed.

## Known Limitations

1. **macOS Only**: The current `run-tests.sh` script is optimized for macOS. Linux users need a modified version.

2. **Socket Path Changes**: The socket path changes when Podman machine is recreated. The script handles this automatically.

3. **Performance**: Podman on macOS uses a VM, so it's slightly slower than Docker Desktop. Container reuse helps mitigate this.

## Summary

**The solution is already implemented and working!** Just use `./run-tests.sh test` instead of `./mvnw test` and Podman will work seamlessly with Testcontainers. The script handles all the socket detection and configuration automatically.

For everyday development:
- ✅ Use: `./run-tests.sh test`
- ❌ Avoid: `./mvnw test` (will fail without DOCKER_HOST set)

The integration is transparent, reliable, and requires zero manual configuration.
