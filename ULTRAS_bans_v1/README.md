# ULTRAS_bans_v1

A production-ready punishment & administration system for Paper servers, with optional
Velocity/BungeeCord proxy-edge enforcement for networks.

## Features

- **Punishment engine**: Ban, Temp Ban, IP Ban, Temp IP Ban, Mute, Temp Mute, Warn, Kick,
  Freeze, Jail, Vanish — all through one central `PunishmentService` with a strict
  `ACTIVE / EXPIRED / REMOVED / REVOKED` state machine. **Conflicting punishments are
  impossible by design**: you can never double-ban, upgrade a temp-mute into a
  permanent one by accident, or unmute a player who isn't muted — the engine checks
  and reports the existing state instead.
- **Full GUI suite**: paginated player selector (online + offline, search, player
  heads), main punishment panel, shared temporary-duration picker, punishment
  history (Book GUI), warnings manager, jail settings, custom permissions
  (per-player and per-rank), gamemode, teleport. Every GUI is defined in its own
  `gui/*.yml` file — title, size, slots, materials, lore, sounds, permissions,
  actions — nothing is hardcoded.
- **Database**: SQLite by default, MySQL/MariaDB optional, all I/O fully async via
  a dedicated executor (HikariCP-pooled). Stores full player profiles (UUID,
  username, last IP, platform, rank, OP status, last server/world/location,
  join/quit times, playtime, stats) so **offline players are fully manageable**.
- **Command conflict protection** *(your specific request)*: `commands.yml` lets
  you disable any individual command network-wide — a disabled command returns
  the normal "Unknown command" message and disappears from tab completion for
  everyone without `ultrasbans.bypass.disabled`. A separate conflict guard
  rewrites/blocks vanilla and third-party commands that overlap with this
  plugin's functionality (ban, kick, mute, gamemode, teleport, EssentialsX
  equivalents, etc.) so only ULTRAS_bans_v1's implementation ever runs.
- **Discord integration**: webhook-based (no bot token required, nothing stored
  in code), with an independent on/off switch and its own channel per category:
  ban, ip-ban, mute, warn, freeze, jail, vanish, kick, admin, chat, join, quit,
  death, commands, console, statistics, achievements. Messages are fully
  customizable per-language in `discord/messages_en.yml` / `messages_ar.yml`.
- **Mass-Punishment Protection**: detects a staff member issuing an abnormal
  burst of punishments (configurable count/window per action type), alerts
  staff and Discord, logs the event, and — if the same staff member repeats
  the behavior — automatically removes OP, revokes plugin permissions, resets
  their rank, and locks their account from further punishment actions.
- **Proxy support**: designed around a shared MySQL/MariaDB database as the
  single source of truth. Includes standalone Velocity and BungeeCord/Waterfall
  plugin entry points in the same jar that reject banned UUIDs/IPs at the proxy
  edge, plus an optional in-Paper `ProxySyncService` that reconciles the local
  cache every few seconds so a ban/mute/freeze/jail/vanish applied on one
  backend server takes effect on every other backend server automatically.
- **Bedrock support**: Geyser/Floodgate players are detected automatically
  (Floodgate UUIDs have an all-zero most-significant-bits pattern) and their
  platform is recorded on their profile; no hard dependency on the Floodgate
  API is required.
- **Full English/Arabic localization** with identical keys in both languages
  and a startup parity check that warns about any mismatch.

## Supported platforms

| Component | Platform | Notes |
|---|---|---|
| Main plugin | Paper 1.21.x (Java Edition) | Compiled against the 1.21.1 API; uses only stable Paper/Bukkit API, so it is expected to keep working on newer 1.21.x point releases without a rebuild. A future major Minecraft/Paper API break would need a new compatibility-layer build — see `pom.xml`'s comment on `paper.version`. |
| Bedrock | via Geyser/Floodgate | Detected automatically; no separate plugin needed on the Paper side. |
| Proxy (ban enforcement) | Velocity 3.x, BungeeCord/Waterfall | Same jar, different entry point (see below). Requires MySQL/MariaDB. |
| Proxy (full network sync) | Any, via shared MySQL | `features.proxy-sync: true` in `config.yml` on every backend server. |

**Honesty note on version range:** "1.21 to 26.2" as literally written spans Minecraft
versions that do not exist yet at the time of writing and whose APIs cannot be targeted
today. This build targets the current stable Paper 1.21.x API using only forward-stable
patterns (no NMS, no reflection into version-specific internals), which is the most
realistic way to maximize forward compatibility without shipping speculative code for
versions with unknown APIs.

## Requirements

- Java 21+
- Paper 1.21.x (or a fork that implements the same API)
- Maven 3.9+
- SQLite (bundled, zero setup) or MySQL/MariaDB 8+

## Building

```bash
mvn clean package -DskipTests
```

Output: `target/ULTRAS_bans_v1-1.0.0.jar`

This environment could not run a live Maven build (no network access to
Maven Central / PaperMC's repository to resolve dependencies), so the jar has
not been produced or verified here. The code was written and reviewed against
the exact API surface of Paper 1.21, Velocity 3.3, and BungeeCord 1.20, with a
manual cross-reference pass to confirm every internal method call resolves to
a real, correctly-typed declaration (see "What was verified" below). Please
run the build command above in an environment with internet access and open
an issue/report back any compiler errors — none are expected, but this is
the one thing this environment genuinely could not test end-to-end.

## Installation

1. Build or obtain `ULTRAS_bans_v1-1.0.0.jar`.
2. Drop it into your Paper server's `plugins/` folder.
3. Start the server once to generate the default config files under
   `plugins/ULTRAS_bans_v1/`.
4. Edit `config.yml`, `database.yml`, `commands.yml`, `security.yml`,
   `permissions.yml`, `jail.yml`, `discord/*.yml`, `lang/*/*.yml` and
   `gui/*.yml` as needed.
5. `/ultrasbans reload` applies config/lang/GUI/Discord/permission changes
   without a restart (database connection is never reloaded this way).

## Proxy setup (Velocity or BungeeCord/Waterfall)

Network-wide punishments require a **shared MySQL/MariaDB database** — SQLite
is a single local file and cannot be safely used by more than one server
process at a time.

1. Set every backend Paper server's `database.yml` to `type: MYSQL` pointing
   at the same database, and set `features.proxy-sync: true` in `config.yml`
   on each of them.
2. Copy the same jar to your proxy's `plugins/` folder:
   - **Velocity**: the jar's `velocity-plugin.json` makes Velocity load
     `com.ultras.bans.proxy.velocity.UltrasBansVelocityPlugin` automatically.
   - **BungeeCord/Waterfall**: the jar's `bungee.yml` makes Bungee load
     `com.ultras.bans.proxy.bungee.UltrasBansBungeePlugin` automatically.
3. In the proxy's own plugin data folder, create `database.yml` with the same
   MySQL credentials (copy it from a backend server) and, if you want localized
   ban screens, a `lang/en/punishments.yml` (and `lang/ar/...`) copied from a
   backend server too.
4. Restart the proxy. It will reject already-banned UUIDs/IPs before they can
   occupy a backend server slot at all.

Backend servers stay in sync with each other via `ProxySyncService`, which
polls the shared database every 5 seconds by default and immediately kicks/
notifies any online player whose punishment state changed on another server.

## Geyser/Floodgate setup

No extra configuration is required. If Geyser/Floodgate is installed and a
Bedrock player joins, their profile is automatically recorded with
`platform: BEDROCK`. IP bans, UUID-based bans, mutes, freezes and jail all work
identically regardless of platform.

## Database

Default is SQLite (`plugins/ULTRAS_bans_v1/ultrasbans.db`). To use MySQL/MariaDB,
edit `database.yml`:

```yaml
type: MYSQL
mysql:
  host: localhost
  port: 3306
  database: ultrasbans
  username: root
  password: "your-password"
  use-ssl: false
  pool-size: 8
table-prefix: "ultras_"
```

All tables are created automatically on first connect (`ultras_punishments`,
`ultras_players`, `ultras_jail_locations`, `ultras_jail_sessions`,
`ultras_player_permissions`, `ultras_rank_permissions`, `ultras_security_logs`).

## Commands

| Command | Aliases | Description |
|---|---|---|
| `/ban [player] [time] [reason]` | `/tempban` for explicit temp bans | Ban / open GUI |
| `/unban <player>` | | Remove an active ban |
| `/ipban [player\|ip] [time] [reason]` | `/ip_ban` | IP ban / open GUI |
| `/unipban <player\|ip>` | `/un_ip_ban` | Remove an active IP ban |
| `/mute [player] [time] [reason]` | | Mute / open GUI |
| `/unmute <player>` | | Remove an active mute |
| `/warn [player] [reason]`, `/warn list`, `/warn logs [player]` | | Warnings |
| `/kick [player] [reason]` | | Kick an online player |
| `/freeze [player]`, `/freeze list`, `/freeze logs [player]` | `/frz` | Freeze |
| `/unfreeze <player>` | `/unfrz` | Release from freeze |
| `/setjail <name>` | | Save current position as a jail |
| `/jail [player]`, `/jail list`, `/jail logs [player]`, `/jail setting [player]` | | Jail |
| `/unjail <player>` | | Release from jail |
| `/vanish [player]`, `/vanish list`, `/vanish logs [player]` | `/vn`, `/nv` | Vanish |
| `/admin`, `/admin permissions [player\|ranks]` | | Admin GUI + custom permissions |
| `/tam <action> ...` | | Central dispatcher for every action above |
| `/game0` `/game1` `/game2` `/game3` `/games [player]` | | Gamemode |
| `/tp [player\|all]`, `/tphere [player\|all]` | | Teleport administration |
| `/ip_list <player>` | | IP/session lookup (works offline) |
| `/ultrasbans reload\|version` | `/ub` | Reload / version |

Any command above can be disabled per-server via `commands.yml`.

## Permissions

Standard nodes: `ultrasbans.ban`, `ultrasbans.ipban`, `ultrasbans.mute`,
`ultrasbans.warn`, `ultrasbans.kick`, `ultrasbans.freeze`, `ultrasbans.jail`,
`ultrasbans.jail.set`, `ultrasbans.vanish`, `ultrasbans.vanish.see`,
`ultrasbans.teleport`, `ultrasbans.teleport.all`, `ultrasbans.gamemode`,
`ultrasbans.logs`, `ultrasbans.permissions`, `ultrasbans.admin`,
`ultrasbans.reload`, plus `ultrasbans.bypass.freeze` / `.bypass.jail` /
`.bypass.disabled` / `.bypass.op-protect` and `ultrasbans.security.exempt`.

On top of that, `/admin permissions` and `permissions.yml` let you toggle any
of the commands above **per player or per rank**, independent of OP status —
see spec-matching sections 22-23 in the original brief. Precedence is:
per-player override → rank setting → normal server permission.

## Discord setup

1. In your Discord server, create a webhook per channel you want to use
   (Channel Settings → Integrations → Webhooks → New Webhook → Copy URL).
2. Paste each URL into `discord/channels.yml` under the matching category.
3. Set `enabled: true` in `discord/config.yml`.
4. `/ultrasbans reload`.

No bot needs to be invited and no token is ever required for this mode.

## Security system

`security.yml` controls the mass-punishment thresholds (actions/window per
type), what happens the moment a threshold is crossed (staff alert, Discord
alert, security log), and the escalation response if the same staff member
repeats the behavior within a rolling window (remove OP, revoke plugin
permissions, reset rank, lock the account). Everything is configurable — no
values are hardcoded in the plugin.

## GUI customization

Every screen lives in its own file under `gui/`: `main.yml`, `players.yml`,
`punishment.yml`, `duration.yml`, `history.yml`, `logs.yml`, `warnings.yml`,
`warning_action.yml`, `jail_settings.yml`, `permissions.yml`, `ranks.yml`,
`gamemode.yml`, `teleport.yml`, and `sounds.yml`. Each item supports `slot`/
`slots`, `material`, `name`, `lore`, `sound`, `command`, `permission`,
`action`, `custom-model-data`, `amount` and `enabled`.

## Languages

`lang/en/` and `lang/ar/` each contain `commands.yml`, `punishments.yml`,
`warnings.yml`, `freeze.yml`, `jail.yml`, `vanish.yml`, `admin.yml`, `gui.yml`,
`errors.yml`, `player.yml`, `discord.yml` — identical keys in both. Change the
active language with `language: en` (or `ar`) in `config.yml`.

## What was verified in this environment

Without live internet access to resolve Maven dependencies, a real
`mvn clean package` could not be executed here. Instead, the following was
checked by direct inspection of every source file:

- Every `import com.ultras.bans.*` statement resolves to a class that exists
  at that exact package path (automated cross-reference, 0 mismatches).
- Every method called on the main plugin class, the GUI manager/screen base,
  and every manager/service class (`JailService`, `FreezeService`,
  `VanishService`, `PermissionService`, `SecurityService`, `DiscordService`,
  `PunishmentService`, `PlayerRepository`, etc.) is declared with a matching
  name (automated cross-reference, 0 mismatches after correcting the check
  itself for interface method syntax).
- One real bug was found and fixed during this review: a `ConcurrentHashMap`
  in the GUI manager was being given a `null` value, which throws at runtime
  in a plain `HashMap`-incompatible way — fixed to skip that entry instead.
- Schema DDL was written in both SQLite and MySQL-compatible dialects, and
  index creation is wrapped to tolerate MySQL's lack of
  `CREATE INDEX IF NOT EXISTS`.

This is a thorough manual/automated static review, not a substitute for an
actual compiler pass — please run the Maven build in a networked environment
before deploying to production, and report back anything it catches.

## Troubleshooting

- **"Unknown command" for something you expect to work**: check
  `commands.yml`'s `disabled-commands` and `hidden-conflicting-commands` lists.
- **SQLite "database is locked"**: SQLite only supports one writer; this is
  normal under very heavy concurrent write load — switch to MySQL for a busy
  network.
- **Proxy not rejecting banned players**: confirm the proxy's own
  `database.yml` points at the same MySQL database as your backend servers,
  and that `type: MYSQL` (not `SQLITE`) is set everywhere.
- **Discord messages not sending**: confirm `discord/config.yml` has
  `enabled: true` and the specific category is both `enabled: true` and has a
  non-empty webhook URL in `discord/channels.yml`.
