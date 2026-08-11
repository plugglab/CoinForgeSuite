# CoinForgeSuite

A two-plugin Gradle multi-module build for Spigot/Paper servers:

- **CoinForge** — a multi-coin economy. One coin can take over the server's
  Vault economy (overriding EssentialsX/CMI/etc), plus unlimited custom
  coins (Gems, Tokens, ...), pay/exchange tax as a currency sink, scheduled
  interest payouts, all owned and stored by CoinForge itself.
- **ShopForge** — a bordered, GUI shop with a live balance display, search,
  and (on Paper 1.21.7+) native Dialog menus, that buys and sells using
  *any* CoinForge coin. Hard-depends on CoinForge via its public API.

CoinForge compiles against plain **Spigot API**; ShopForge compiles against
**Paper API** instead (a superset of Spigot API) specifically so it can
optionally use Minecraft's new Dialog menus - see the dedicated section
below for exactly what that means for compatibility. Both are pinned to
**1.21.11** deliberately, not the newest available version - see "A note on
Minecraft's versioning" below for why.

## Building

You need a JDK and internet access to pull the Spigot, Paper, Vault, and
PlaceholderAPI dependencies. **CoinForge builds fine with JDK 8+, but
ShopForge needs JDK 21+ actively selected** (not just installed somewhere -
whatever `java -version` reports when you run Gradle) to compile against
paper-api - see the versioning note below for why. If you use a JDK version
manager (sdkman, jenv, IntelliJ's per-project JDK setting, etc.), make sure
it's pointed at 21 or newer before building. This is a single multi-project
Gradle build — one command builds both plugins.

```bash
# if you don't have Gradle installed, generate the wrapper once with any
# local Gradle install:
gradle wrapper

# then build everything:
./gradlew build        # Linux/macOS
gradlew.bat build        # Windows

# or without the wrapper at all:
gradle build
```

Output jars:
- `CoinForge/build/libs/CoinForge-1.1.0.jar`
- `ShopForge/build/libs/ShopForge-1.0.0.jar`

Drop **both** into your server's `plugins/` folder — ShopForge hard-depends
on CoinForge and won't enable without it.

To build/work on a single module: `./gradlew :CoinForge:build` or
`./gradlew :ShopForge:build`.

### A note on Minecraft's versioning (why this is pinned to 1.21.11)

Starting in 2026, Mojang (and Paper, following suit) switched Minecraft's
version numbering to a year-based scheme - `26.1`, `26.2`, etc. - instead
of `1.21.x`. `1.21.11` was the **last version under the old scheme**, and
this project is deliberately pinned to it rather than the newest Paper
release, for a concrete reason:

**Paper's 26.x builds require a Java 25 toolchain**, enforced by Gradle at
dependency-resolution time (not just at actual compilation) - depending on
a 26.x `paper-api` build from a project that targets Java 8 bytecode (as
this one does, for broad compatibility with older servers) fails outright
with a Gradle "no matching variant" error, before any code is even
compiled. Since 1.21.11 already includes everything this project actually
needs (Dialog menus were added back in 1.21.7), there's no reason to take
on that Java 25 requirement, which would also mean the built jars could no
longer load on any older server running an older JDK.

If you specifically want to target a newer Minecraft version, both
`build.gradle` files would need their `paper-api`/`spigot-api` coordinate
bumped, *and* the `java { sourceCompatibility / targetCompatibility }`
block would need raising to match whatever Java version that Paper release
requires - check https://papermc.io for current requirements before doing
that, since it's a real trade-off against older-server compatibility.

**Separately**, both SpigotMC's and PaperMC's Maven repos do eventually
stop hosting old build artifacts within a version over time (this
project's dependency pin already broke once before this way, when it
targeted an even older Minecraft version that had been fully removed from
the repo). If a build ever fails with a "could not resolve" error for the
exact `1.21.11-R0.1-SNAPSHOT` coordinates used here, that's what's
happened, and the fix is to re-pin both `build.gradle` files to whichever
version is now the closest still-supported release under the old `1.21.x`
numbering.

**A third, separate wrinkle**: Paper's published module metadata declares a
**Java 21 minimum for paper-api itself**, at every version - not just 26.x.
That's why `ShopForge/build.gradle` targets Java 21 while
`CoinForge/build.gradle` stays on Java 8: CoinForge depends on plain Spigot
API (no such Gradle-enforced minimum), but ShopForge depends on Paper API
for Dialog support, and Gradle refuses to resolve *any* paper-api version
from a Java-8-targeted module. In practice this costs nothing real - Paper
has required Java 21 to run the server at all since Minecraft 1.20.5, so
anyone whose server can even load ShopForge's Dialog feature already has
Java 21 installed. It does mean ShopForge's compiled bytecode can't load on
older/lower-Java servers the way CoinForge's can, which is why its
`api-version` is set higher (`1.20`) than CoinForge's (`1.13`).

---

## Native Dialog menus (ShopForge)

Minecraft 1.21.6 added **Dialogs** — a client-rendered menu system separate
from chest-style inventory GUIs, built for things like text input and
confirmations. Paper exposed this to plugins starting with **Paper 1.21.7**
- one version before what this project is pinned to, so it's available here
by default. It's still an experimental Paper API and could change in future
Paper releases.

ShopForge uses it in exactly two places, both with automatic fallback:



| Feature | With native dialogs (Paper 1.21.7+) | Fallback (Spigot, or older Paper) |
|---|---|---|
| Search | A proper text-input dialog box | Click the icon, then type in chat |
| Big-purchase confirmation | A proper Yes/No dialog box | Click the item again within the timeout |

Detection is automatic and safe: on startup ShopForge checks (via
reflection) whether the running server's `Player` type actually has a
`showDialog` method. If not — plain Spigot, or Paper older than 1.21.7 — it
silently uses the fallback behavior instead, and nothing else about the
plugin is affected. You can also force the fallback behavior everywhere by
setting `use-native-dialogs: false` in ShopForge's `config.yml`, even on a
server that supports dialogs.

All of this lives in one file, `dialog/DialogSupport.java` — it's the only
place in ShopForge that references Paper's Dialog classes at all.

### Why ShopForge compiles against paper-api

Dialog classes (`io.papermc.paper.dialog.*`) aren't part of vanilla Spigot
API — only Paper's API includes them. Since Paper API is a strict superset
of Spigot API (same `org.bukkit.*` classes, plus extras), swapping
ShopForge's compile dependency to `paper-api` doesn't remove or change
anything about its normal Bukkit-API-based code. The Dialog-specific code
is only ever *executed* after the runtime check above passes, so compiling
against paper-api doesn't stop ShopForge's base shop functionality from
running perfectly well on plain Spigot too.

---

## CoinForge

### Overriding your existing economy plugin

If you already run EssentialsX (or any other Vault economy plugin) and want
CoinForge's coin to take over:

```yaml
vault-currency:
  enabled: true
  register-with-vault: true
  priority: HIGHEST
```

Vault always defers to the single highest-priority registered provider, so
`HIGHEST` guarantees CoinForge wins. Use `priority: NORMAL` if you'd rather
it just be a fallback when nothing else is registered.

To disable the vault coin entirely (custom-coins-only mode):

```yaml
vault-currency:
  enabled: false
```

To keep it as a purely internal coin that never touches Vault (coexists with
an existing economy plugin):

```yaml
vault-currency:
  enabled: true
  register-with-vault: false
```

### Configuration (`config.yml`)

```yaml
vault-currency:
  enabled: true
  register-with-vault: true
  priority: HIGHEST
  id: coins
  display-name: "Coins"
  symbol: "$"
  decimal-places: 2
  starting-balance: 0
  icon: GOLD_INGOT

custom-currencies:
  gems:
    display-name: "Gems"
    symbol: "✦"
    decimal-places: 0
    starting-balance: 0
    icon: EMERALD
  tokens:
    display-name: "Tokens"
    symbol: "⛃ "
    decimal-places: 0
    starting-balance: 100
    icon: SUNFLOWER

exchange:
  enabled: true
  rates:
    gems-to-coins: 10
    tokens-to-gems: 0.5

pay-cooldown-seconds: 3
log-transactions: true

tax:
  pay-percent: 0
  exchange-percent: 0

interest:
  enabled: false
  interval-minutes: 60
  rate-percent: 1.0
  minimum-balance: 1.0
  only-online-players: false
  currencies: [coins]
```

`icon` sets the Material shown for that coin in `/coins gui` and the
leaderboard menu — any valid Bukkit `Material` name works.

Add as many entries under `custom-currencies` as you like. Run
`/coins reload` after editing.

**Tax** skims a percentage off `/coins pay` and `/coins exchange` as a true
currency sink — the sender still pays what they typed, the recipient gets
less, and the difference is destroyed rather than going anywhere. Players
with `coinforge.tax.bypass` pay no tax.

**Interest** periodically pays every eligible account a percentage of its
balance, like a savings account. Restarts automatically on `/coins reload`.

### Commands (`/coins`, aliases `/coin`, `/wallet`, `/balance`, `/bal`)

| Command | Description |
|---|---|
| `/coins` | Show all of your balances (chat) |
| `/coins gui` (or `/coins wallet`) | Open a bordered chest-GUI wallet - one icon per coin, click any coin to view its leaderboard |
| `/coins <currency>` | Show one balance |
| `/coins pay <player> <currency> <amount>` | Pay another player (respects the cooldown and tax) |
| `/coins top <currency>` | Opens a GUI leaderboard for players (up to 28 ranked entries with player heads); falls back to a chat list from console |
| `/coins exchange <from> <to> <amount>` | Convert one coin into another (respects tax) |
| `/coins admin give\|take\|set\|reset <player> <currency> [amount]` | Modify any balance |
| `/coins reload` | Reload `config.yml` |

### Chest-GUI wallet & leaderboard

Both use plain Bukkit inventory GUIs — no Paper dependency, works on any
Spigot server the plugin already supports:

- **Wallet** (`/coins gui`): a small bordered menu, one icon per configured
  coin (set `icon:` under `vault-currency`/`custom-currencies` in
  `config.yml` to customize), showing your current balance in the lore.
  Click any coin to jump straight to its leaderboard.
- **Leaderboard** (`/coins top <currency>`): a bordered menu of player heads
  ranked by balance (gold/white/red highlighting for the top 3), with a
  Refresh button to recalculate on demand and a Back button to return to
  the wallet.

### Permissions

| Permission | Default | Description |
|---|---|---|
| `coinforge.use` | true | Check your own balances |
| `coinforge.pay` | true | Pay other players |
| `coinforge.top` | true | View top balance lists |
| `coinforge.exchange` | true | Convert one coin into another |
| `coinforge.admin` | op | give/take/set/reset balances, reload config |
| `coinforge.tax.bypass` | false | Exempts a player from pay/exchange tax |

### Data storage

Every coin gets its own file under `plugins/CoinForge/data/`, e.g.
`data/coins.yml`, `data/gems.yml`. Each is tagged `format-version: '1.0'`
for future migrations. If `log-transactions` is on, every action (including
interest payouts) is appended to `plugins/CoinForge/transactions.log`.

### API (for other plugins, e.g. ShopForge)

```java
CoinForgeAPI api = CoinForgeAPI.get(); // null if CoinForge isn't loaded
if (api != null && api.has(player, "coins", 100)) {
    api.withdraw(player, "coins", 100);
}
```

Available on `CoinForgeAPI`: `currencyExists`, `getCurrencyIds`,
`getDisplayName`, `getSymbol`, `format`, `getBalance`, `has`, `deposit`,
`withdraw`.

### PlaceholderAPI

`%coinforge_balance_<currency>%` (raw number) and
`%coinforge_formatted_<currency>%` (e.g. `$1,250.00`), if PlaceholderAPI is installed.

---

## ShopForge

A bordered GUI shop (`/shop`) with categories, paginated item pages, search,
and a live balance display, all priced in whichever CoinForge coin you
choose per item.

### Visual style

Styled after the classic "economy shop" GUI look:

- **Main menu**: blue-glass bordered frame with light-blue interior filler,
  category icons arranged in a clean grid, distinct from category pages so
  it's obvious at a glance which screen you're on.
- **Category/search pages**: black-glass bordered footer row, gray filler
  on any unused item slot so a half-full page never looks sparse.
- **Item lore**: labeled `Buy Price:` / `Sell Price:` lines with a
  strikethrough divider above and below, plus clear per-action hints
  (`▸ Left-Click to buy 1`, etc.) — the same layout style used by popular
  economy shop plugins.
- Every screen's footer has a **Search** icon, your **player head**
  (showing live balances for every coin, click to refresh), and a
  **Close** button.

### Configuration (`config.yml`)

```yaml
gui-title: "&8Shop"
rows-per-page: 5
search-icon: "COMPASS"
buy-sound: "ENTITY_EXPERIENCE_ORB_PICKUP"
sell-sound: "ENTITY_ITEM_PICKUP"
confirm-purchases:
  threshold: 1000
  timeout-seconds: 5
use-native-dialogs: true
```

`confirm-purchases` requires confirming any single buy/sell whose total
value is at or above `threshold` — a native Yes/No dialog on Paper
1.21.7+, or a second click within `timeout-seconds` everywhere else. Set
`threshold: -1` to disable confirmations entirely.

### Catalog (`shops.yml`)

Ships with 12 categories and around 107 items out of the box - building
blocks, ores & gems, food, wood, tools, potions, combat gear, redstone
components, wool & dye, concrete & terracotta, plus a Gems-priced and a
Tokens-priced category to show off multi-currency pricing. Example entry:

```yaml
categories:
  blocks:
    display-name: "&aBuilding Blocks"
    icon: GRASS_BLOCK
    items:
      dirt:
        material: DIRT
        display-name: "&fDirt"
        buy-price: 1.0
        sell-price: 0.5
        currency: coins
      mystery-token:
        material: NETHER_STAR
        display-name: "&dMystery Token"
        buy-price: 50.0
        currency: gems
        # no sell-price -> can't be sold back
```

Each item picks its own `currency` — mix and match Coins, Gems, Tokens, or
any other coin you've defined in CoinForge freely, even within the same
category. Omit `buy-price` or `sell-price` to make an item one-directional.
Run `/shop reload` after editing.

### Controls

In any item's slot: **left-click** buys 1, **shift-left-click** buys a full
stack, **right-click** sells 1, **shift-right-click** sells everything
matching that item in your inventory.

### Commands (`/shop`, aliases `/shopforge`, `/store`)

| Command | Description |
|---|---|
| `/shop` | Open the shop |
| `/shop sellhand` | Instantly sell the whole stack you're holding, if it's listed anywhere in the shop |
| `/shop reload` | Reload `config.yml` and `shops.yml` |

### Permissions

| Permission | Default | Description |
|---|---|---|
| `shopforge.use` | true | Open the shop, buy/sell |
| `shopforge.admin` | op | Reload the shop |
| `shopforge.category.<id>` | true | Registered per category from `shops.yml` at load time — set to `false` for specific players/groups to hide a category |

## Project layout

```
CoinForgeSuite/
├── settings.gradle
├── gradle/wrapper/gradle-wrapper.properties
├── CoinForge/
│   ├── build.gradle
│   └── src/main/
│       ├── resources/{plugin.yml, config.yml}
│       └── java/dev/coinforge/plugin/
│           ├── CoinForge.java
│           ├── api/CoinForgeAPI.java
│           ├── commands/CoinsCommand.java
│           ├── currency/{Currency,CurrencyManager,ExchangeManager,InterestManager}.java
│           ├── economy/CoinForgeEconomy.java
│           ├── listeners/PlayerJoinListener.java
│           ├── placeholder/CoinForgeExpansion.java
│           └── storage/{DataStorage,TransactionLogger}.java
└── ShopForge/
    ├── build.gradle
    └── src/main/
        ├── resources/{plugin.yml, config.yml, shops.yml}
        └── java/dev/shopforge/plugin/
            ├── ShopForge.java
            ├── ShopManager.java
            ├── model/{Category,ShopItem}.java
            ├── gui/{GuiSession,ShopGuiManager,ShopGuiListener,ShopInventoryHolder}.java
            ├── dialog/DialogSupport.java
            └── commands/ShopCommand.java
```
