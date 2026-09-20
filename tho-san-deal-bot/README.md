# 🎯 Thợ Săn Deal Bot

A production-ready Telegram Bot for price watching on Lazada. Built with Java 17 (Java 21 in production Docker), Spring Boot 3.x, PostgreSQL.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Requirements](#requirements)
4. [Environment Variables](#environment-variables)
5. [Run Local](#run-local)
6. [PostgreSQL Setup](#postgresql-setup)
7. [Run Docker](#run-docker)
8. [BotFather Setup](#botfather-setup)
9. [Telegram Privacy Mode](#telegram-privacy-mode)
10. [Add Bot to Channel](#add-bot-to-channel)
11. [Give Post Messages Permission](#give-post-messages-permission)
12. [Find Channel Chat ID](#find-channel-chat-id)
13. [Configure Webhook](#configure-webhook)
14. [Webhook Secret](#webhook-secret)
15. [Test Bot](#test-bot)
16. [Commands](#commands)
17. [MockPriceProvider](#mockpriceprovider)
18. [Scheduler](#scheduler)
19. [Single-Instance Limitation](#single-instance-limitation)
20. [Future Lazada Integration](#future-lazada-integration)
21. [Troubleshooting](#troubleshooting)

---

## Overview

**Thợ Săn Deal Bot** watches Lazada product prices and notifies users in a Telegram channel when prices drop to their target.

**Phase 1 features:**
- Telegram webhook handler with idempotent inbox
- `/watch`, `/list`, `/remove`, `/start`, `/help` commands
- PostgreSQL persistence with Flyway migrations
- MockPriceProvider (deterministic, configurable)
- Notification outbox pattern (DB-decoupled Telegram delivery)
- Anti-spam notification logic (rules A, B, C)
- Docker Compose deployment

---

## Architecture

```
Telegram API
    │
    ▼ POST /api/telegram/webhook
TelegramWebhookController
    │ 1. Validate secret header
    │ 2. Persist to telegram_update_inbox (atomic, idempotent)
    │ 3. Return 200 OK immediately
    │ 4. Publish TelegramUpdateEvent (async)
    ▼
TelegramUpdateService (async thread pool)
    │
    ▼
MessageCommandParser → ParsedCommand
    │
    ▼
CommandDispatcher → CommandHandler (Start/Help/Watch/List/Remove)
    │
    ▼
WatchService → PostgreSQL

────────────────────────────
Scheduler (fixedDelay=60s):
PriceWatchScheduler
    ├── PriceProvider.checkPrice() [MockPriceProvider → LazadaPriceProvider in Phase 2]
    ├── PriceCheckLog.save()
    ├── NotificationDecisionService.shouldNotify()
    └── DealNotificationService.queueDealNotification() → notification_outbox

NotificationOutboxWorker (fixedDelay=15s):
    ├── Load PENDING outbox entries
    ├── TelegramClient.sendMessage() → Telegram Channel
    └── Mark SENT/FAILED
```

**Key design decisions:**
- **Inbox pattern**: Durable deduplication for Telegram retries
- **Outbox pattern**: DB transaction decoupled from HTTP calls
- **fixedDelay** scheduler: No concurrent runs in single-instance mode
- **Optimistic locking** (`@Version`): Prevents duplicate notifications on concurrent access
- **PriceProvider interface**: Phase 2 Lazada integration requires only a new implementation

---

## Requirements

- Java 17+ (Java 21 in Docker)
- Maven 3.9+
- PostgreSQL 15+
- Docker + Docker Compose (for containerized deployment)
- Publicly accessible HTTPS URL (for Telegram webhook)

---

## Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `SERVER_PORT` | No | HTTP port (default: 8080) | `8080` |
| `DB_URL` | Yes | JDBC connection URL | `jdbc:postgresql://localhost:5432/thosandeal` |
| `DB_USERNAME` | Yes | Database username | `thosandeal` |
| `DB_PASSWORD` | Yes | Database password | `secret` |
| `TELEGRAM_BOT_TOKEN` | Yes | Bot token from BotFather | `123456:ABC...` |
| `TELEGRAM_WEBHOOK_SECRET` | Yes | Secret for webhook header validation | `random-secret-32chars` |
| `TELEGRAM_NOTIFICATION_CHANNEL_ID` | Yes | Channel ID for deal notifications | `-1001234567890` |
| `PRICE_CHECK_INTERVAL_MS` | No | Scheduler interval ms (default: 60000) | `60000` |
| `NOTIFICATION_OUTBOX_INTERVAL_MS` | No | Outbox worker interval ms (default: 15000) | `15000` |
| `MOCK_FINAL_PRICE` | No | MockPriceProvider fixed price (default: 1490000) | `1490000` |

---

## Run Local

```bash
# 1. Clone and enter directory
cd tho-san-deal-bot

# 2. Create .env file
cp .env.example .env
# Edit .env with your values

# 3. Start PostgreSQL (see below)

# 4. Export env vars
set -a; source .env; set +a   # Linux/macOS
# On Windows PowerShell:
Get-Content .env | ForEach-Object { $kv = $_ -split '=',2; [System.Environment]::SetEnvironmentVariable($kv[0], $kv[1]) }

# 5. Run
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## PostgreSQL Setup

```sql
-- Create database and user
CREATE USER thosandeal WITH PASSWORD 'yourpassword';
CREATE DATABASE thosandeal OWNER thosandeal;
GRANT ALL PRIVILEGES ON DATABASE thosandeal TO thosandeal;
```

Flyway will run all migrations automatically on startup.

---

## Run Docker

```bash
# 1. Create .env file
cp .env.example .env
# Edit .env — set DB_PASSWORD, TELEGRAM_BOT_TOKEN, etc.

# NOTE: DB_URL in .env for Docker should use the service name:
# DB_URL=jdbc:postgresql://postgres:5432/thosandeal

# 2. Start all services
docker-compose up -d

# 3. Check logs
docker-compose logs -f app

# 4. Health check
curl http://localhost:8080/actuator/health
```

---

## BotFather Setup

1. Open Telegram, search `@BotFather`
2. Send `/newbot`
3. Follow prompts to name your bot: `Thợ Săn Deal` / `thosandeal_bot`
4. Copy the bot token → put in `TELEGRAM_BOT_TOKEN`

---

## Telegram Privacy Mode

> ⚠️ **Important for group usage**

By default, Telegram bots only receive **command messages** (e.g. `/watch`) in groups.

To allow the bot to read **raw messages** like `https://s.lazada.vn/abc 1500000` in the group:

1. Open `@BotFather`
2. Send `/setprivacy`
3. Select `@thosandeal_bot`
4. Choose **Disable**

If Privacy Mode is ON, the bot will **not** receive plain URL + price messages in group chats.

---

## Add Bot to Channel

To send deal notifications to your channel:

1. Open your channel: `🔥 Thợ Săn Deal | Thông Báo`
2. Go to **Channel Settings → Administrators**
3. Click **Add Administrator**
4. Search for `@thosandeal_bot`
5. Add as administrator

---

## Give Post Messages Permission

When adding the bot as admin:

- Enable: **Post Messages** ✅
- All other permissions can be disabled

---

## Find Channel Chat ID

The channel ID is needed for `TELEGRAM_NOTIFICATION_CHANNEL_ID`.

**Method 1 — via bot:**
1. Forward any message from your channel to `@userinfobot`
2. It will reply with the channel ID (starts with `-100...`)

**Method 2 — via API:**
```
https://api.telegram.org/bot<TOKEN>/getUpdates
```
Send a test message to the channel and look for `"chat":{"id":-100...}`

**Method 3 — web.telegram.org:**
Open channel in browser. URL contains the ID: `https://web.telegram.org/a/#-1001234567890`

---

## Configure Webhook

Telegram needs to know your bot's public URL. The bot does not call Telegram to set webhook automatically — you must do it once:

```bash
curl -X POST "https://api.telegram.org/bot<YOUR_TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://your-domain.com/api/telegram/webhook",
    "secret_token": "<YOUR_WEBHOOK_SECRET>",
    "allowed_updates": ["message"]
  }'
```

**Never put the token in your source code or README.**

---

## Webhook Secret

Generate a strong random secret (32+ alphanumeric chars):

```bash
# Linux/macOS
openssl rand -hex 32

# Or use any password manager
```

Put the same value in:
- Telegram webhook `secret_token` parameter
- `TELEGRAM_WEBHOOK_SECRET` environment variable

---

## Test Bot

1. Open Telegram, send `/start` to your bot
2. Try `/help`
3. Try `/watch https://s.lazada.vn/yourproduct 1500000`
4. Try `/list`
5. Try `/remove <ID>`

**Test notification:**
Set `MOCK_FINAL_PRICE` below your watch price. The scheduler will trigger a notification to the channel on the next cycle.

---

## Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/start` | Welcome message | `/start` |
| `/help` | Usage guide | `/help` |
| `/watch <url> <price>` | Add product to watchlist | `/watch https://s.lazada.vn/abc 1500000` |
| `/list` | View your watchlist | `/list` |
| `/remove <id>` | Remove from watchlist | `/remove 15` |

**Also supported:**
- Commands with bot username: `/watch@thosandeal_bot https://...`
- Raw URL + price: `https://s.lazada.vn/abc 1500000`

**Price formats:**
`1500000`, `1.500.000`, `1,500,000`, `1500k`, `1.5tr`, `1tr5`

---

## MockPriceProvider

Phase 1 uses a **deterministic mock** instead of real Lazada price checking.

Configure via environment variable:
```
MOCK_FINAL_PRICE=1490000   # Always returns this price
```

**How to test notifications:**
- Add a watch item with target price `1500000`
- Set `MOCK_FINAL_PRICE=1490000` (below target)
- Wait for scheduler to run (default: 60s)
- Notification should appear in your channel

---

## Scheduler

**Price check scheduler:**
- Runs every `PRICE_CHECK_INTERVAL_MS` (default: 60,000ms = 1 minute)
- Uses `fixedDelay` — next run starts AFTER previous completes
- Each watch item is isolated — one failure doesn't stop others

**Notification outbox worker:**
- Runs every `NOTIFICATION_OUTBOX_INTERVAL_MS` (default: 15,000ms)
- Delivers pending notifications to Telegram
- Max 3 retry attempts per notification

---

## Single-Instance Limitation

> ⚠️ **Known Limitation**

The current scheduler has **no distributed lock**. Running multiple app instances will cause:
- Duplicate price checks
- Potential duplicate notifications (mitigated by outbox fingerprint)

**For multi-instance deployment (Phase 2+):**

Add [ShedLock](https://github.com/lukas-krecan/ShedLock) to `pom.xml`:
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
</dependency>
```

Then annotate schedulers with `@SchedulerLock(name = "priceWatchScheduler")`.

The architecture is designed to support this change without major refactoring.

---

## Future Lazada Integration (Phase 2)

To add real Lazada price checking:

1. Create `LazadaPriceProvider implements PriceProvider`
2. Annotate with `@Primary` (or remove `@Component` from `MockPriceProvider`)
3. **No other files need to change**

The `PriceProvider` interface is the integration point:
```java
public interface PriceProvider {
    PriceCheckResult checkPrice(WatchItem watchItem);
}
```

`PriceCheckResult` returns all needed fields:
- `productId`, `productName`, `variantName`
- `currentPrice`, `finalPrice`, `currency`
- `success`, `errorMessage`

---

## Troubleshooting

**Bot not responding to group messages:**
→ Disable Privacy Mode in BotFather (see [Privacy Mode](#telegram-privacy-mode))

**Webhook not receiving updates:**
→ Verify your URL is publicly accessible over HTTPS
→ Check webhook is registered: `GET https://api.telegram.org/bot<TOKEN>/getWebhookInfo`

**Notification not sent to channel:**
→ Verify bot is admin in channel with "Post Messages" permission
→ Check `TELEGRAM_NOTIFICATION_CHANNEL_ID` starts with `-100`
→ Check `notification_outbox` table for FAILED entries

**Flyway migration fails:**
→ Check PostgreSQL is running and `DB_URL` is correct
→ Check user has `CREATE TABLE` permissions

**Database connection refused:**
→ In Docker: ensure `DB_URL` uses `postgres` service name, not `localhost`
→ Local: ensure PostgreSQL is running on correct port

**"release version 21 not supported" build error:**
→ Local environment requires Java 17+. Docker uses Java 21 JRE.
→ Set `JAVA_HOME` to Java 17+ installation.
