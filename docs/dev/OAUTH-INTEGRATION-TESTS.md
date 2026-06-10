# OAuth Integration Tests Architecture

> End-to-end integration tests for the `jaiclaw-identity` OAuth flows, using a local mock HTTP server to simulate provider endpoints without external network calls.

## Overview

The `jaiclaw-identity` module has 90 unit tests (Spock specs) covering individual classes in isolation. The integration tests complement these by exercising full OAuth flows end-to-end: HTTP client calls, JSON parsing, PKCE validation, token exchange, credential storage, and callback server lifecycle.

### Test Inventory

| Spec | Tests | Flow | What It Covers |
|------|-------|------|---------------|
| `AuthorizationCodeFlowIT` | 6 | Auth Code + PKCE | URL construction, code exchange, userinfo fetch, userinfo failure, token error, credential storage |
| `DeviceCodeFlowIT` | 7 | Device Code (RFC 8628) | Device code request, polling with pending/success, slow_down backoff, access_denied, missing URL, server error, form param validation |
| `OAuthCallbackServerIT` | 5 | Loopback Callback | Successful callback, CSRF state mismatch, error callback, timeout, full callback + token exchange e2e |
| **Total** | **18** | | |

## Architecture

### Mock Server Pattern

All integration tests use `MockOAuthServer`, a reusable Groovy helper that wraps `com.sun.net.httpserver.HttpServer`:

```
┌─────────────────────────────────────────────────────────────┐
│                    Test Spec (Spock)                         │
│  1. Configure MockOAuthServer endpoints                     │
│  2. Build OAuthProviderConfig pointing at mock              │
│  3. Exercise flow (AuthorizationCodeFlow / DeviceCodeFlow)  │
│  4. Assert results + inspect recorded requests              │
└──────────────────────┬──────────────────────────────────────┘
                       │ real HTTP (java.net.http.HttpClient)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                MockOAuthServer (127.0.0.1:0)                │
│                                                             │
│  /token      → configurable JSON response                   │
│  /userinfo   → configurable JSON response                   │
│  /device/code → configurable JSON response                  │
│                                                             │
│  Features:                                                  │
│  • Binds to random port (port 0)                            │
│  • Records all requests per path for assertions             │
│  • pendingThenSuccess() for device code polling simulation  │
│  • Optional slow_down injection at specific poll index      │
│  • errorEndpoint() for error scenario testing               │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Mock server tech | `com.sun.net.httpserver.HttpServer` | JDK-bundled, zero dependencies, sufficient for mock endpoints |
| Port allocation | Random (port 0) | Avoids conflicts in CI, parallel-safe |
| HTTP client | Real `java.net.http.HttpClient` | Tests actual HTTP serialization, not just mocked interfaces |
| Test framework | Spock (Groovy) | Consistent with all other JaiClaw tests |
| Maven phase | `verify` via `maven-failsafe-plugin` | Standard Maven convention for ITs; unit tests remain in `test` phase |
| Profile gating | `-Pintegration-test` | ITs don't run by default; explicit opt-in prevents slow CI builds |

### Flow-Level vs End-to-End

Tests operate at two levels:

1. **Flow-level** (`AuthorizationCodeFlowIT`, `DeviceCodeFlowIT`): Directly instantiate `AuthorizationCodeFlow` / `DeviceCodeFlow` with a real `HttpClient`, point at mock server, exercise the flow methods, and assert on results + recorded HTTP requests.

2. **End-to-end callback** (`OAuthCallbackServerIT`): Start the real `OAuthCallbackServer` on a free port, simulate a browser redirect via HTTP GET, then chain into a real token exchange against the mock server.

### Testability Enhancement

`OAuthFlowManager` was given a second constructor that accepts `AuthorizationCodeFlow` and `DeviceCodeFlow` instances:

```java
// Default constructor (production) — creates its own flows
public OAuthFlowManager(Map<String, OAuthProviderConfig> providerConfigs,
                        AuthProfileStoreManager storeManager)

// Testable constructor — accepts injected flows
public OAuthFlowManager(Map<String, OAuthProviderConfig> providerConfigs,
                        AuthProfileStoreManager storeManager,
                        AuthorizationCodeFlow authCodeFlow,
                        DeviceCodeFlow deviceCodeFlow)
```

Both `AuthorizationCodeFlow` and `DeviceCodeFlow` already accepted `HttpClient` via constructor, so integration tests pass a client pointing at the mock server.

## File Inventory

### Test Files

| File | Purpose |
|------|---------|
| `src/test/groovy/io/jaiclaw/identity/oauth/MockOAuthServer.groovy` | Reusable mock server with configurable endpoints and request tracking |
| `src/test/groovy/io/jaiclaw/identity/oauth/AuthorizationCodeFlowIT.groovy` | Auth code + PKCE integration tests |
| `src/test/groovy/io/jaiclaw/identity/oauth/DeviceCodeFlowIT.groovy` | Device code flow integration tests |
| `src/test/groovy/io/jaiclaw/identity/oauth/OAuthCallbackServerIT.groovy` | Callback server integration tests |

### Modified Source Files

| File | Change |
|------|--------|
| `src/main/java/.../OAuthFlowManager.java` | Added testable constructor |
| `pom.xml` | Added `integration-test` profile with `maven-failsafe-plugin` |

## MockOAuthServer API

```groovy
MockOAuthServer server = new MockOAuthServer()  // binds 127.0.0.1:0
server.port                                      // allocated port
server.baseUrl()                                 // "http://127.0.0.1:{port}"

// Configure endpoints
server.tokenEndpoint('/token', '{"access_token":"..."}')
server.userinfoEndpoint('/userinfo', '{"email":"..."}')
server.deviceCodeEndpoint('/device/code', '{"device_code":"..."}')
server.errorEndpoint('/token', 400, 'invalid_grant', 'Code expired')

// Device code polling simulation
server.pendingThenSuccess('/token', 2, successJson)          // 2 pending, then success
server.pendingThenSuccess('/token', 3, successJson, 1)       // slow_down at index 1

// Request inspection
List<RecordedRequest> reqs = server.getRequests('/token')
Map<String, String> params = reqs[0].formParams()            // parsed form body
String authHeader = reqs[0].headers['Authorization']          // request headers

server.close()                                                // stop server
```

## Maven Configuration

### Profile: `integration-test`

```xml
<profiles>
    <profile>
        <id>integration-test</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.5.2</version>
                    <configuration>
                        <includes>
                            <include>**/*IT.java</include>
                        </includes>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

The `**/*IT.java` include pattern picks up compiled Groovy `*IT.groovy` specs (Groovy compiles to `.class` files that surefire/failsafe treat as Java).

## Running the Tests

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# Unit tests only (default — no profile needed)
./mvnw test -pl :jaiclaw-identity -o
# → 90 tests

# Integration tests (under profile)
./mvnw verify -pl :jaiclaw-identity -Pintegration-test -o
# → 90 unit tests + 18 integration tests

# Both together
./mvnw verify -pl :jaiclaw-identity -Pintegration-test -o
```

## Test Coverage Map

```
AuthorizationCodeFlow.java
  ├── buildAuthorizeUrl()        ← AuthorizationCodeFlowIT: PKCE params, state, scopes
  ├── exchangeCode()             ← AuthorizationCodeFlowIT: form params, token parsing, error
  └── fetchUserinfo()            ← AuthorizationCodeFlowIT: Bearer token, email/accountId, failure

DeviceCodeFlow.java
  ├── requestDeviceCode()        ← DeviceCodeFlowIT: form params, response parsing, error
  └── pollForToken()             ← DeviceCodeFlowIT: pending→success, slow_down, access_denied

OAuthCallbackServer.java
  ├── constructor (start server) ← OAuthCallbackServerIT: port binding
  ├── awaitCallback()            ← OAuthCallbackServerIT: success, CSRF, error, timeout
  └── close()                    ← OAuthCallbackServerIT: cleanup

OAuthFlowManager.java
  └── (testable constructor)     ← AuthorizationCodeFlowIT: credential storage test

AuthProfileStoreManager.java
  └── upsertProfile()            ← AuthorizationCodeFlowIT: credential persistence verification
```
