![QuantumCrates Header Banner](https://i.imgur.com/kfhA9ox.png)

# QuantumCrates

A Minecraft crate plugin for Paper 1.20.1+ built around a **web-first management philosophy** — configure everything from a browser dashboard without touching a single config file or restarting the server.

---

## Requirements

- Paper / Spigot / Purpur 1.20.1+ (Java 21)
- SQLite (built-in) or MySQL 8+

**Optional integrations**
- PlaceholderAPI
- DecentHolograms
- MMOItems / ItemsAdder / Oraxen
- Vault

---

## Installation

1. Drop the `.jar` into your `plugins/` folder
2. Start the server once to generate config files
3. Set the following in `config.yml`:
```yaml
   web:
     secret-token: "your-long-random-token"
     port: 7420
     hostname: "auto"
```
4. Restart the server
5. Run `/qc web` in-game to get your dashboard link

---

## Features

- 🌐 **Web Dashboard** — manage everything from your browser, no restarts needed
- 🎰 **5 GUI Animations** — Roulette, Shuffler, Boundary, Single Spin, Flicker
- ⭐ **Pity System** — soft & hard pity with configurable thresholds per crate
- 🔑 **Virtual & Physical Keys** — database-backed or physical item keys
- 📊 **Live Analytics** — real-time opening feed via WebSocket
- 🎆 **Particle Animations** — 7 idle & open animation types
- 📅 **Scheduling** — time windows, day restrictions, limited-time events
- 👁 **Preview GUI** — paginated reward browser with pity bar, key balance, and chance display
- 🔄 **Mass Open** — bulk open with per-session limit and TPS-friendly batching
- ⏱ **Lifetime Open Limit** — cap how many times a player can ever open a crate
- 🚫 **Rate Limiting** — prevent macro abuse with per-second open rate cap
- 💥 **Access Denied Knockback** — push players away when they lack required keys
- 💰 **Vault Economy** — soft-depend economy integration for rewards
- 💻 **Developer API** — full API and custom events for external plugins
- 🔌 **Integrations** — MMOItems, ItemsAdder, Oraxen, PlaceholderAPI, DecentHolograms

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/qc reload` | Reload all crates, rarities, and holograms | `quantumcrates.admin` |
| `/qc give <player> <keyId> <amount>` | Give keys — supports offline players & UUID | `quantumcrates.key.give` |
| `/qc open <crateId>` | Force-open a crate for yourself | `quantumcrates.admin` |
| `/qc setloc <crateId>` | Add a crate location at the block you're looking at | `quantumcrates.admin` |
| `/qc delloc <crateId> [index]` | Remove a crate location | `quantumcrates.admin` |
| `/qc pity <player> <crateId>` | Check a player's pity counter | `quantumcrates.admin` |
| `/qc resetpity <player> <crateId>` | Reset a player's pity counter | `quantumcrates.admin` |
| `/qc resetlifetime <player> <crateId>` | Reset a player's lifetime open count | `quantumcrates.admin` |
| `/qc keys <player> <keyId>` | Check a player's virtual key balance | `quantumcrates.admin` |
| `/qc list` | List all registered crates | `quantumcrates.use` |
| `/qc info <crateId>` | Print crate info including all locations | `quantumcrates.use` |
| `/qc massopen <crateId> [count]` | Mass open a crate | `quantumcrates.massopen` |
| `/qc web [ip]` | Generate a dashboard magic link | `quantumcrates.admin` |

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `quantumcrates.admin` | Full admin access (includes all below) | OP |
| `quantumcrates.use` | Use crates and view info | Everyone |
| `quantumcrates.massopen` | Mass open crates | Everyone |
| `quantumcrates.key.give` | Give keys to other players | OP |
| `quantumcrates.bypasscooldown` | Bypass crate opening cooldown | OP |
| `quantumcrates.bypasslimit` | Bypass lifetime open limit | OP |
| `quantumcrates.web` | Generate dashboard magic links | OP |

---

## PlaceholderAPI

| Placeholder | Returns |
|---|---|
| `%quantumcrates_keys_<keyId>%` | Player's virtual key balance |
| `%quantumcrates_pity_<crateId>%` | Current pity counter |
| `%quantumcrates_pity_max_<crateId>%` | Pity threshold |
| `%quantumcrates_cooldown_<crateId>%` | Remaining cooldown, human-readable |
| `%quantumcrates_cooldown_raw_<crateId>%` | Remaining cooldown in milliseconds |
| `%quantumcrates_open_<crateId>%` | `true` / `false` — whether crate is openable now |
| `%quantumcrates_total_<crateId>%` | Total reward weight |
| `%quantumcrates_lifetime_<crateId>%` | Player's lifetime open count |
| `%quantumcrates_lifetime_max_<crateId>%` | Lifetime open limit (`0` = unlimited) |

> 📌 **Note:** This is only a partial list. For all 20+ placeholders (including Pity Status, Cooldowns, and Vault Balance), please check the [Documentation](https://bintanq.my.id/docs).

---

## Developer API

QuantumCrates provides a stable Java API and custom events for third-party developers. 
For Maven/Gradle installation instructions, available methods, DTOs, and event examples, please read the official documentation:

👉 **[Read the Developer API Documentation](https://bintanq.my.id/docs)**

---

## Database

**SQLite (default)**
```yaml
database:
  type: sqlite
  sqlite:
    file: quantumcrates.db
```

**MySQL**
```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: quantumcrates
    username: root
    password: yourpassword
    pool-size: 10
```

---

## Web Dashboard

The dashboard runs as an embedded web server — no external software required.

**Default port:** `7420`

**Login options:**
- **Magic link** — run `/qc web` in-game and click the link (valid 5 minutes, single use)
- **Manual** — navigate to the dashboard URL and enter your `secret-token`

**Pages:** Crate Architect · Analytics & Logs · Key Settings · Messages · Players · Rarities · Settings

---

## Links

- [Documentation](https://bintanq.my.id/docs)
- [SpigotMC](https://www.spigotmc.org/resources/quantumcrate-%E2%9C%A8-first-web-dashboard-crates-%E2%9C%A81-21-11.134691/)
- [Discord](https://discord.com/users/696526236175827015)

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).  
© 2026 bintanq — You may use, modify, and distribute this plugin freely under the terms of the GPL v3.