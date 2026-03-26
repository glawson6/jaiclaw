# Telegram Bot Setup Guide

Connect JaiClaw to Telegram so users can chat with your agent directly in the Telegram app.

## Quick Start (2 minutes)

1. Open Telegram and message [@BotFather](https://t.me/BotFather)
2. Send `/newbot`, follow the prompts, and copy the bot token
3. Add the token to `docker-compose/.env`:
   ```
   TELEGRAM_BOT_TOKEN=123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
   ```
4. Start JaiClaw with Telegram (runs locally by default):
   ```bash
   ./start.sh telegram
   # Or via Docker:
   ./start.sh telegram docker
   ```
5. Open the bot link printed in the console and send a message

## Step-by-Step: Create a Telegram Bot

### 1. Open @BotFather

Open Telegram (desktop or mobile) and search for **@BotFather**, or click: https://t.me/BotFather

BotFather is Telegram's official bot for creating and managing bots.

### 2. Create the bot

Send `/newbot` to BotFather. It will ask two questions:

1. **Bot name** — a display name (e.g., "My JaiClaw Assistant"). This can contain spaces.
2. **Bot username** — a unique handle ending in `bot` (e.g., `my_jaiclaw_bot`). No spaces, must be globally unique.

BotFather responds with:

```
Done! Congratulations on your new bot. You will find it at t.me/my_jaiclaw_bot.
You can now add a description, about section and profile picture for your bot...

Use this token to access the HTTP API:
123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
```

### 3. Copy the bot token

The token is the string that looks like `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`. Copy it — you'll need it in the next step.

### 4. (Optional) Customize the bot

While in the BotFather chat, you can also:

| Command | Purpose |
|---------|---------|
| `/setdescription` | Short text shown when users open the bot for the first time |
| `/setabouttext` | Text shown in the bot's profile |
| `/setuserpic` | Profile picture for the bot |
| `/setcommands` | Custom command menu (e.g., `/help`, `/reset`) |

## Configure JaiClaw

Choose one of these methods to provide the bot token to JaiClaw:

### Option A: docker-compose/.env file (recommended)

Edit `docker-compose/.env` and set:

```bash
TELEGRAM_BOT_TOKEN=123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
```

This is picked up automatically by both `./start.sh telegram` and Docker Compose.

### Option B: Environment variable

```bash
export TELEGRAM_BOT_TOKEN=123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
./start.sh telegram
```

Environment variables take precedence over .env file values.

### Option C: Onboard wizard (shell)

If using the interactive shell:

```bash
./start.sh shell
# then type: onboard
```

The onboard wizard includes a Telegram setup step that prompts for the token.

## Polling vs. Webhook Mode

JaiClaw supports two ways of receiving messages from Telegram:

### Polling (default)

- JaiClaw periodically calls Telegram's `getUpdates` API to fetch new messages
- No public URL required — works behind NAT, firewalls, and on localhost
- Best for: **local development, testing, and simple deployments**
- Enabled when `TELEGRAM_WEBHOOK_URL` is blank or not set (the default)

### Webhook (production)

- Telegram pushes messages to your server via HTTP POST
- Requires a **public HTTPS URL** reachable by Telegram's servers
- Lower latency than polling — messages arrive instantly
- Best for: **production deployments with a public domain**

To enable webhook mode, set the webhook URL:

```bash
TELEGRAM_WEBHOOK_URL=https://your-domain.com/webhook/telegram
```

JaiClaw automatically registers the webhook with Telegram on startup and deletes it if you switch back to polling.

#### Webhook with ngrok (local development)

If you want to test webhook mode locally:

```bash
# Terminal 1: Start ngrok
ngrok http 8080

# Terminal 2: Set the ngrok URL and start JaiClaw
export TELEGRAM_WEBHOOK_URL=https://abc123.ngrok-free.app/webhook/telegram
./start.sh telegram
```

#### Webhook with a reverse proxy (production)

Configure your reverse proxy (nginx, Caddy, Traefik) to forward `/webhook/telegram` to JaiClaw's port:

```nginx
# nginx example
location /webhook/telegram {
    proxy_pass http://localhost:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Then set:
```bash
TELEGRAM_WEBHOOK_URL=https://your-domain.com/webhook/telegram
```

## Test the Connection

### Validate the token

```bash
curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe" | jq .
```

Expected response:
```json
{
  "ok": true,
  "result": {
    "id": 123456,
    "is_bot": true,
    "first_name": "My JaiClaw Assistant",
    "username": "my_jaiclaw_bot"
  }
}
```

### Send a test message

1. Start JaiClaw: `./start.sh telegram`
2. Open your bot in Telegram: `https://t.me/<bot_username>`
3. Send `/start` or any message
4. The bot should respond through JaiClaw's agent

## How It Works

```
User (Telegram App)
    │
    ▼
Telegram Bot API
    │
    ▼ (polling: getUpdates / webhook: HTTP POST)
    │
TelegramAdapter (jaiclaw-channel-telegram)
    │
    ▼ ChannelMessage
    │
WebhookDispatcher → AgentRuntime → LLM → response
    │
    ▼ sendMessage
    │
Telegram Bot API → User sees reply
```

The `TelegramAdapter` handles:
- Inbound message parsing (text, photos, documents, audio, video, voice)
- File attachment downloads from Telegram's servers
- Outbound message delivery via the `sendMessage` API
- Automatic polling/webhook lifecycle management

## Troubleshooting

### 401 Unauthorized

```
Telegram token validation failed
```

- The bot token is incorrect or revoked
- Verify the token: `curl -s "https://api.telegram.org/bot<TOKEN>/getMe"`
- If revoked, generate a new token via @BotFather → `/token`

### Conflict: terminated by other getUpdates

```
409 Conflict: terminated by other getUpdates request
```

- Another instance of JaiClaw (or another bot framework) is polling with the same token
- Stop all other instances before starting JaiClaw
- If using Docker, check for orphan containers: `docker ps | grep jaiclaw`

### Webhook not receiving messages

- Verify the URL is publicly accessible: `curl https://your-domain.com/webhook/telegram`
- Telegram requires HTTPS — self-signed certificates are not accepted unless explicitly configured
- Check webhook status: `curl -s "https://api.telegram.org/bot<TOKEN>/getWebhookInfo" | jq .`
- The `last_error_message` field shows what went wrong

### Bot not responding

- Check that the gateway is running: `curl http://localhost:8080/api/health`
- Check JaiClaw logs for errors: `./start.sh logs`
- Verify the bot token is being passed to the container: check `docker-compose/.env`
- Ensure an LLM provider is configured (Anthropic, OpenAI, Google Gemini, or Ollama)

### File/image handling

- JaiClaw downloads files from Telegram's servers using the bot token
- Large files (>20MB) may fail — Telegram's Bot API has a 20MB download limit
- For file uploads (bot → user), the limit is 50MB

## Restrict to Specific Users

By default, the bot responds to any Telegram user who messages it. To restrict access to specific users, set the `TELEGRAM_ALLOWED_USERS` environment variable to a comma-separated list of Telegram user IDs.

### Find your Telegram user ID

Message [@userinfobot](https://t.me/userinfobot) on Telegram — it replies with your numeric user ID.

### Configure the allowlist

```bash
# Single user
TELEGRAM_ALLOWED_USERS=123456789

# Multiple users
TELEGRAM_ALLOWED_USERS=123456789,987654321
```

Add this to `docker-compose/.env` alongside your bot token, or export it as an environment variable.

Messages from users not on the list are silently dropped. Leave the variable blank or unset to allow everyone.

## Security

- **Keep your token secret.** Anyone with the token can control the bot. Never commit it to version control.
- **Bot privacy mode** is enabled by default in Telegram. In groups, the bot only receives messages that start with `/` or explicitly mention the bot. Disable via @BotFather → `/setprivacy` if you want the bot to see all group messages.
- **Restrict group access.** If the bot should only work in DMs, you can use @BotFather → `/setjoingroups` to disable group invitations.
- **Rotate tokens** if you suspect a leak: @BotFather → `/revoke` → `/token` to generate a new one.
