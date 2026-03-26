# JaiClaw Signal Channel

Signal messaging channel adapter via [signal-cli](https://github.com/AsamK/signal-cli).

Two integration modes:
- **HTTP_CLIENT** — connects to a [signal-cli-rest-api](https://github.com/bbernhard/signal-cli-rest-api) Docker sidecar (recommended for most setups)
- **EMBEDDED** — gateway manages a local signal-cli daemon process via JSON-RPC over TCP

## Prerequisites

You need a phone number that can receive SMS or voice calls for Signal registration. This number will be dedicated to JaiClaw — it cannot be used with the Signal mobile app simultaneously (unless you use device linking).

---

## Option A: signal-cli-rest-api via Docker (HTTP_CLIENT mode)

This is the easiest setup. A Docker container runs signal-cli and exposes a REST API.

### 1. Start the container

```bash
mkdir -p $HOME/.local/share/signal-api

docker run -d --name signal-api \
  --restart=always \
  -p 8080:8080 \
  -v $HOME/.local/share/signal-api:/home/.local/share/signal-cli \
  -e 'MODE=native' \
  bbernhard/signal-cli-rest-api
```

Or with docker-compose:

```yaml
services:
  signal-cli-rest-api:
    image: bbernhard/signal-cli-rest-api:latest
    environment:
      - MODE=native
    ports:
      - "8080:8080"
    volumes:
      - "./signal-cli-config:/home/.local/share/signal-cli"
```

**MODE options:**

| Mode | Speed | Memory | Notes |
|------|-------|--------|-------|
| `normal` | OK | Normal | Standard Java execution |
| `native` | Fast | Normal | GraalVM native image |
| `json-rpc` | Fastest | Higher | Keeps signal-cli running in background |

### 2. Register or link your number

**Option 1 — Link to existing Signal account (recommended):**

1. Open `http://localhost:8080/v1/qrcodelink?device_name=jaiclaw` in a browser
2. On your phone: Signal → Settings → Linked Devices → tap "+"
3. Scan the QR code

**Option 2 — Register a new number:**

```bash
# Register (sends SMS verification)
curl -X POST "http://localhost:8080/v1/register/+14155551234"

# Verify with the code you received
curl -X POST "http://localhost:8080/v1/register/+14155551234/verify/123456"
```

### 3. Test it

```bash
curl -X POST -H "Content-Type: application/json" \
  'http://localhost:8080/v2/send' \
  -d '{"message": "Hello from JaiClaw!", "number": "+14155551234", "recipients": ["+14155559999"]}'
```

### 4. Configure JaiClaw

```yaml
jaiclaw:
  channels:
    signal:
      enabled: true
      mode: http-client
      api-url: http://localhost:8080
      phone-number: "+14155551234"
      poll-interval-seconds: 2
      # allowed-senders: "+14155550001,+14155550002"  # optional whitelist
```

---

## Option B: Install signal-cli locally (EMBEDDED mode)

### macOS

```bash
brew install signal-cli
```

This installs signal-cli and its Java dependency via Homebrew. Verify:

```bash
signal-cli --version
```

### Linux (JVM build)

Requires **JRE 21+**.

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jre

# Arch Linux
sudo pacman -S jre-openjdk
```

Download and install signal-cli:

```bash
VERSION=$(curl -Ls -o /dev/null -w %{url_effective} \
  https://github.com/AsamK/signal-cli/releases/latest | sed 's/^.*\/v//')

curl -L -O "https://github.com/AsamK/signal-cli/releases/download/v${VERSION}/signal-cli-${VERSION}.tar.gz"
sudo tar xf "signal-cli-${VERSION}.tar.gz" -C /opt
sudo ln -sf "/opt/signal-cli-${VERSION}/bin/signal-cli" /usr/local/bin/
```

Verify:

```bash
signal-cli --version
```

### Linux (Native build — faster startup, no JRE needed)

```bash
VERSION=$(curl -Ls -o /dev/null -w %{url_effective} \
  https://github.com/AsamK/signal-cli/releases/latest | sed 's/^.*\/v//')

curl -L -O "https://github.com/AsamK/signal-cli/releases/download/v${VERSION}/signal-cli-${VERSION}-Linux-native.tar.gz"
sudo tar xf "signal-cli-${VERSION}-Linux-native.tar.gz" -C /opt
sudo ln -sf /opt/signal-cli/bin/signal-cli /usr/local/bin/
```

### Register your number

```bash
# Register (receive verification via SMS)
signal-cli -a +14155551234 register

# Or request voice call instead
signal-cli -a +14155551234 register --voice

# Verify with the code you received
signal-cli -a +14155551234 verify 123456
```

### Test it

```bash
# Send a message
signal-cli -a +14155551234 send -m "Hello from JaiClaw!" +14155559999

# Receive messages
signal-cli -a +14155551234 receive
```

### Test daemon mode (what EMBEDDED mode uses)

```bash
# Start the JSON-RPC daemon on TCP port 7583
signal-cli -a +14155551234 daemon --tcp localhost:7583
```

In another terminal, send a JSON-RPC request:

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"send","params":{"recipient":["+14155559999"],"message":"test"}}' | nc localhost 7583
```

### Configure JaiClaw

```yaml
jaiclaw:
  channels:
    signal:
      enabled: true
      mode: embedded
      phone-number: "+14155551234"
      cli-command: signal-cli          # path to signal-cli binary
      tcp-port: 7583                   # JSON-RPC TCP port
      # allowed-senders: "+14155550001,+14155550002"
```

---

## Data storage

signal-cli stores credentials and keys at:
- **Linux:** `$HOME/.local/share/signal-cli/data/`
- **macOS:** `$HOME/.local/share/signal-cli/data/`
- **Docker:** mounted volume (e.g. `$HOME/.local/share/signal-api/`)

Back up this directory before upgrading signal-cli.

## Troubleshooting

**"Unregistered" errors:** You must receive messages regularly for Signal's protocol to work correctly. If using HTTP_CLIENT mode, the adapter polls automatically. For EMBEDDED mode, the daemon handles this.

**Rate limiting on registration:** Signal may rate-limit registration attempts. Wait and retry, or use the `--voice` flag for voice verification.

**Native library not found:** The bundled `libsignal-client` supports x86_64 Linux, Windows, and macOS. For ARM/aarch64 Linux, use the JVM build instead of the native build.
