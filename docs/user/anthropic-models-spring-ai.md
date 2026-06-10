# Anthropic Claude Models (Spring AI Usage)

This document lists Anthropic Claude model IDs in order from cheapest to most expensive, ready to use in code (e.g., Spring AI configuration).

---

## 💸 Cheapest → Most Expensive

### 1. Claude 3.5 Haiku
**Model ID:**
```
claude-3-5-haiku-latest
```

---

### 2. Claude 4.5 Haiku
**Model ID:**
```
claude-4-5-haiku-latest
```

---

### 3. Claude 4.x Sonnet (Recommended Default)
**Model IDs:**
```
claude-4-sonnet-latest
claude-4-5-sonnet-latest
claude-4-6-sonnet-latest
```

---

### 4. Claude 4.5 / 4.6 Opus
**Model IDs:**
```
claude-4-5-opus-latest
claude-4-6-opus-latest
```

---

### 5. Claude 4 / 4.1 Opus (Most Expensive / Legacy)
**Model IDs:**
```
claude-4-opus-latest
claude-4-1-opus-latest
```

---

## 🧠 Recommendation

- **Best default:** `claude-4-sonnet-latest`
- **Cheapest:** `claude-3-5-haiku-latest`
- **Highest intelligence:** `claude-4-6-opus-latest`

---

## ⚙️ Example (Spring AI application.yml)

```yaml
spring:
  ai:
    anthropic:
      api-key: YOUR_API_KEY
      chat:
        options:
          model: claude-4-sonnet-latest
```

---

Generated on: 2026-04-03 00:49:43
