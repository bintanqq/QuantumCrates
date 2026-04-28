# QuantumCrates

A Minecraft crate plugin for Paper 1.21 built around a **web-first management philosophy** — configure everything from a browser dashboard without touching config files or restarting the server.

---

## Requirements

- Paper 1.21 (Java 21)
- SQLite (built-in) or MySQL 8+

**Optional integrations**
- PlaceholderAPI
- DecentHolograms
- MMOItems
- ItemsAdder
- Oraxen

---

## Installation

1. Drop the `.jar` into your `plugins/` folder.
2. Start the server once to generate config files.
3. Open `config.yml` and set:
    - `web.secret-token` — change this to something random and secure
    - `web.port` — default is `7420`
    - `web.hostname` — your server IP or domain (required if behind a proxy)
4. Restart the server.
5. In-game, run `/qc web` to generate a magic link and open the dashboard.

---

## Getting Started

### Accessing the Dashboard

Run `/qc web` in-game. The plugin will print a one-time link in chat that is valid for **5 minutes**. Click it to open the web dashboard and authenticate automatically.

If the link expires, just run `/qc web` again.

For manual login, navigate to the dashboard URL and enter your `web.secret-token` from `config.yml`.

### Creating Your First Crate

1. Open the dashboard and go to **Crate Architect**.
2. Click **+ New Crate** and give it an ID and display name.
3. Add rewards using the **Add Reward** button. Each reward has a type, material, rarity, and weight.
4. Configure the required key, cooldown, and pity settings in the right panel.
5. Click **Save Crate**. The crate is live immediately — no restart needed.
6. In-game, look at a block and run `/qc setloc <crateId>` to place the crate.

### Giving Keys to Players

**Virtual keys (default):**
```
/qc give <player> <keyId> <amount>
```

Or use the **Key Settings** page in the dashboard to give keys directly.

**Physical keys:** If you have switched to `keys.mode: physical` in `config.yml`, the command above will give a physical key item to the player's inventory instead.

---

## Features

### Crate System

Each crate is stored as a `.json` file in `plugins/QuantumCrates/crates/`. You can edit these manually or entirely through the dashboard.

**Per-crate configuration includes:**
- Display name with Minecraft color codes
- Required keys (how many and what type)
- Rewards with individual weights
- Cooldown between openings per player
- Mass open toggle and limit
- Idle and open particle animations
- Hologram lines above the crate block
- Schedule (time window, days of week, or event window)
- Preview GUI customization

### Reward System

Rewards use a **weighted random** system. A reward with weight `20` is twice as likely to be selected as one with weight `10`. Weight is not a percentage — it is relative to the sum of all weights in the crate.

**Reward types:**
| Type | Description |
|---|---|
| `VANILLA` | Standard Minecraft item |
| `COMMAND` | Runs console or player commands on win |
| `VANILLA_WITH_COMMANDS` | Item + commands together |
| `MMOITEMS` | Item from MMOItems plugin |
| `ITEMSADDER` | Item from ItemsAdder plugin |
| `ORAXEN` | Item from Oraxen plugin |

Each reward can optionally broadcast a message to the entire server when won.

### Pity System

The pity system guarantees rare rewards after a set number of unsuccessful openings.

- **Soft pity**: Once a player's counter reaches `softPityStart`, the weight of all rare-tier-and-above rewards increases with each subsequent open.
- **Hard pity**: Once the counter reaches `threshold`, the next open is guaranteed to give a reward at or above the configured minimum rarity. The counter resets after any qualifying rare drop.

Pity is tracked per-player and per-crate, stored in the database.

### Key System

Two modes are available server-wide (configured in `config.yml`):

- **Virtual**: Keys are stored as a balance in the database. Players have no physical item. More secure — cannot be dropped, traded, or duplicated.
- **Physical**: Keys are physical inventory items tagged with a unique identifier. Can be stored in chests, traded between players.

Crates can require keys from third-party plugins (MMOItems, ItemsAdder, Oraxen) by setting the key type in the crate config.

### Particle Animations

Each crate has separate idle and open animations. Available types:

| Type | Description |
|---|---|
| `RING` | Rotating horizontal circle |
| `HELIX` | Two strands rising and falling |
| `SPHERE` | Fibonacci point-cloud slowly rotating |
| `SPIRAL` | Contracting and expanding vortex |
| `RAIN` | Particles drifting down from above |
| `ORBIT` | Multiple layers orbiting at different heights |
| `PULSE` | Ring that breathes in and out |
| `NONE` | No particles |

Each type has configurable speed, radius, density, and particle effect.

### Hologram

When a crate has a location set, a floating hologram appears above it. Lines support Minecraft color codes.

Two backends are supported:
- **DecentHolograms** — used automatically if the plugin is installed
- **Built-in ArmorStand** — fallback with zero dependencies

Edit hologram lines from the dashboard without restarting.

### Scheduling

Crates can be restricted to only open during specific times:

- `ALWAYS` — always openable (default)
- `TIME_WINDOW` — openable between two times daily (e.g. 20:00–22:00)
- `DAYS_OF_WEEK` — openable on specific days, optionally within a time window
- `EVENT` — openable between two absolute timestamps (for limited-time events)

Timezone is configurable per crate.

### Mass Open

Players with the `quantumcrates.massopen` permission can hold Shift and right-click a crate to open it as many times as they have keys. Each crate has a configurable per-session limit. Mass opens are processed in batches of 10 per server tick to avoid TPS impact.

### Analytics

The **Analytics** page in the dashboard shows:
- Total opening count per crate
- Live feed of crate opens as they happen (via WebSocket)
- Pity trigger events
- Per-player history

All openings are stored in the database with player name, UUID, reward, pity counter at time of open, location, and timestamp.

---

## Rarity System

Rarities are fully user-defined in `rarities.yml`. You can add, remove, or rename any tier. The defaults are:

| ID | Display | Color |
|---|---|---|
| COMMON | Common | Gray |
| UNCOMMON | Uncommon | Green |
| RARE | Rare | Blue |
| EPIC | Epic | Purple |
| LEGENDARY | Legendary | Gold |
| MYTHIC | Mythic | Pink |

Each rarity has a hex color used in the web dashboard and a Minecraft color code used in-game. Changes made through the **Rarities** editor in the dashboard are applied immediately without a restart.

---

## Commands

All commands use `/quantumcrates` or the alias `/qc`.

| Command | Description | Permission |
|---|---|---|
| `/qc reload` | Reload all crates, rarities, particles, and holograms | `quantumcrates.admin` |
| `/qc give <player> <keyId> <amount>` | Give keys to a player | `quantumcrates.key.give` |
| `/qc open <crateId>` | Force-open a crate for yourself | `quantumcrates.admin` |
| `/qc massopen <crateId> [count]` | Mass open a crate | `quantumcrates.massopen` |
| `/qc preview <crateId>` | Open the reward preview GUI | `quantumcrates.preview` |
| `/qc info <crateId>` | Print crate info to chat | `quantumcrates.use` |
| `/qc list` | List all registered crates | `quantumcrates.use` |
| `/qc setloc <crateId>` | Set crate location to the block you are looking at | `quantumcrates.admin` |
| `/qc pity <player> <crateId>` | Check a player's pity counter | `quantumcrates.admin` |
| `/qc resetpity <player> <crateId>` | Reset a player's pity counter | `quantumcrates.admin` |
| `/qc keys <player> <keyId>` | Check a player's virtual key balance | `quantumcrates.admin` |
| `/qc web [ip]` | Generate a dashboard magic link | `quantumcrates.admin` |

### Block Interactions

| Action | Result |
|---|---|
| Left-click crate block | Open preview GUI |
| Right-click crate block | Open crate |
| Shift + Right-click crate block | Mass open |

---

## PlaceholderAPI

If PlaceholderAPI is installed, these placeholders are available:

| Placeholder | Returns |
|---|---|
| `%quantumcrates_keys_<keyId>%` | Player's virtual key balance |
| `%quantumcrates_pity_<crateId>%` | Player's current pity counter |
| `%quantumcrates_pity_max_<crateId>%` | Pity threshold for crate |
| `%quantumcrates_cooldown_<crateId>%` | Remaining cooldown, formatted |
| `%quantumcrates_cooldown_raw_<crateId>%` | Remaining cooldown in milliseconds |
| `%quantumcrates_open_<crateId>%` | `true` or `false` if crate is currently openable |
| `%quantumcrates_total_<crateId>%` | Total weight of crate rewards |

---

## Database Configuration

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

## Permissions

| Permission | Description | Default |
|---|---|---|
| `quantumcrates.admin` | Full admin access (includes all below) | OP |
| `quantumcrates.use` | Use crates | Everyone |
| `quantumcrates.preview` | View reward preview GUI | Everyone |
| `quantumcrates.massopen` | Mass open crates | Everyone |
| `quantumcrates.key.give` | Give keys to other players | OP |
| `quantumcrates.web` | Generate dashboard magic links | OP |

---