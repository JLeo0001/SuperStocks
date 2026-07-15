# SuperStocks

A Minecraft virtual stock investment plugin for Paper/Purpur servers. Sync real stock quotes into your server or create fully simulated local markets. Players trade through chest GUIs or commands, with all transactions settled via Vault economy.

Designed for servers looking to add long-term economy gameplay, investment systems, leaderboard competition, virtual finance events, or high-risk/high-reward mechanics. It does not alter real stock data but can scale prices and volatility to fit your server's economy scale.

SuperStocks is for entertainment purposes only and does not constitute investment advice.

---

## Features

- Real stock quotes via Tencent Finance (no API key required).
- A-share, HK, and US markets with 60 pre-configured stocks.
- Local virtual markets with configurable random-walk simulation.
- Bull/bear/neutral market regimes for local markets.
- Chest GUI trading with pagination, confirmation dialogs, and price sparklines.
- Commands for buy, sell, short, quote, portfolio, rankings, and more.
- Limit orders, stop-loss, and take-profit automated orders.
- Short selling with margin requirements and forced liquidation.
- Investment competitions with virtual capital, seasons, and Vault rewards.
- IPO offerings with subscription, proportional allocation, and refunds.
- Stock certificates as tradeable in-game items.
- BossBar real-time stock price pinning.
- Market composite index and periodic market reports.
- Admin GUI panel and audit log queries.
- Vault economy integration (EssentialsX Economy compatible).
- PlaceholderAPI support.
- SQLite storage for all holdings, trades, orders, and audit data.
- 13 built-in language files.
- Winner and loser leaderboards by unrealized P&L.
- Configurable loss penalty system with tiered charges.
- Circuit breaker and quote anomaly protection.
- Market hours restrictions (optional).
- Config migration and startup validation.

---

## Requirements

| Item | Requirement |
|------|-------------|
| Server | Paper / Purpur compatible |
| Java | Java 21 |
| Required | Vault + a Vault economy plugin |
| Recommended | EssentialsX Economy |
| Optional | PlaceholderAPI |
| Database | SQLite (auto-created) |
| Network | Required for real stock quotes (Tencent API) |

Recommended environment:

- Minecraft 1.21.x Paper / Purpur
- Java 21
- Vault
- EssentialsX + EssentialsX Economy
- PlaceholderAPI (for scoreboards, menus, chat displays)

---

## Installation

1. Install `Vault`.
2. Install `EssentialsX Economy` or another Vault-compatible economy plugin.
3. Optionally install `PlaceholderAPI`.
4. Drop `SuperStocks.jar` into your server's `plugins` folder.
5. Start the server to generate default configuration files.
6. Modify as needed (then `/stocks reload`):

```text
plugins/SuperStocks/config.yml
plugins/SuperStocks/Language/
plugins/SuperStocks/CustomMarkets/
```

After first startup you should see:

```text
plugins/SuperStocks/config.yml
plugins/SuperStocks/stocks.db
plugins/SuperStocks/Language/zh_CN.yml
plugins/SuperStocks/CustomMarkets/example.yml
```

---

## Quick Start

If you just want the plugin running:

1. Confirm Vault economy is working.
2. Keep default language:

```yaml
language: zh_CN
```

3. Keep default quote provider:

```yaml
stock-provider:
  active: tencent
```

4. High-risk features are already disabled by default — no changes needed.

5. In-game:

```text
/stocks sync
/stocks
```

If you see stock quotes in the GUI and can buy/sell, the basic setup is working. You can then gradually enable volatility enhancement, local markets, and loss penalties.

---

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/stocks` | Open the stock trading GUI |
| `/stocks help` | Show help |
| `/stocks open` | Open the stock GUI |
| `/stocks quote <symbol>` | View stock quote |
| `/stocks buy <symbol> <shares>` | Buy shares |
| `/stocks sell <symbol> <shares>` | Sell shares |
| `/stocks short <symbol> <shares>` | Open a short position |
| `/stocks cover <id> [shares]` | Close (cover) a short position |
| `/stocks portfolio` | View text-based portfolio |
| `/stocks history <symbol>` | View 24h price history |
| `/stocks stats` | View personal investment statistics |
| `/stocks watch add <symbol>` | Add to watchlist |
| `/stocks watch remove <symbol>` | Remove from watchlist |
| `/stocks watch list` | View watchlist |
| `/stocks alert <symbol> <above|below> <price>` | Create price alert |
| `/stocks order buy <symbol> <shares> <price> [seconds]` | Create limit buy order |
| `/stocks order sell <symbol> <shares> <price> [seconds]` | Create limit sell order |
| `/stocks order stop-loss <symbol> <shares> <price> [seconds]` | Create stop-loss order |
| `/stocks order take-profit <symbol> <shares> <price> [seconds]` | Create take-profit order |
| `/stocks order list` | View open orders |
| `/stocks order cancel <id>` | Cancel an order |
| `/stocks rank winners` | View top investors |
| `/stocks rank losers` | View bottom investors |
| `/stocks index` | View market indices |
| `/stocks pin <symbol>` | Pin stock price to BossBar |
| `/stocks unpin` | Remove BossBar pin |
| `/stocks report` | View market report |
| `/stocks competition list` | List competitions |
| `/stocks competition join <id>` | Join a competition |
| `/stocks competition buy <id> <symbol> <shares>` | Buy in competition |
| `/stocks competition sell <id> <symbol> <shares>` | Sell in competition |
| `/stocks certificate issue <symbol> <shares>` | Convert holdings to certificate item |
| `/stocks certificate list` | List your certificates |
| `/stocks ipo list` | List IPO offerings |
| `/stocks ipo subscribe <id> <shares>` | Subscribe to an IPO |

Examples:

```text
/stocks quote sh600519
/stocks buy sh600519 10
/stocks sell sh600519 5
/stocks short usTSLA 5
/stocks cover 1
/stocks portfolio
/stocks rank winners
```

### Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/stocks sync` | `superstocks.admin` | Force quote sync |
| `/stocks status` | `superstocks.admin` | View system status |
| `/stocks pause` | `superstocks.admin` | Pause all trading |
| `/stocks resume` | `superstocks.admin` | Resume trading |
| `/stocks penalty history <player>` | `superstocks.admin` | View player penalty history |
| `/stocks admin portfolio <player>` | `superstocks.admin` | View player portfolio |
| `/stocks admin give <player> <symbol> <shares>` | `superstocks.admin` | Grant holdings to player |
| `/stocks admin remove <player> <symbol> <shares>` | `superstocks.admin` | Remove holdings from player |
| `/stocks admin clear <player>` | `superstocks.admin` | Clear all player holdings |
| `/stocks admin panel` | `superstocks.admin` | Open admin GUI |
| `/stocks admin audit [player] [action]` | `superstocks.admin` | Query audit log |
| `/stocks competition create <name> <days> <capital>` | `superstocks.admin` | Create a competition |
| `/stocks competition end <id>` | `superstocks.admin` | End competition & distribute rewards |
| `/stocks ipo create <symbol> <name> <market> <price> <total> <max>` | `superstocks.admin` | Create an IPO |
| `/stocks ipo cancel <id>` | `superstocks.admin` | Cancel an IPO |
| `/stocks reload` | `superstocks.admin` | Reload config, language, markets, services |

### Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `superstocks.use` | Everyone | Use GUI and player commands |
| `superstocks.admin` | OP | Use admin commands |
| `superstocks.penalty.exempt` | OP | Exempt from loss penalties |

---

## GUI Overview

**Main screen** — Market entries, portfolio button, winners/losers leaderboards, watchlist, system stats.

**Market screen** — Stock list showing name, symbol, current price, change amount, change %, and last update time. Supports pagination for markets with many stocks.

**Stock detail page** — Quick-buy and quick-sell buttons (3 preset amounts), 24h price trend sparkline, and price history summary.

**Trade confirmation dialog** — Appears when the estimated trade gross exceeds the configured threshold (default 1000), preventing accidental large trades.

**Watchlist** — A personal page showing only the stocks the player has added via `/stocks watch add`.

**Portfolio** — Shows all current holdings with cost basis, market value, and unrealized profit.

**Admin panel** — Overview of total holdings, active players, total trades, last sync time, trading pause status, and Vault status.

All GUI sizes, materials, and slot layouts are configurable in `config.yml`. The preset trade amounts default to `[1, 10, 100]` and can be adjusted to fit your economy scale — for high-currency servers, consider `[10, 100, 1000]`.

---

## Quote Provider

Default: Tencent Finance (no API key needed).

```yaml
stock-provider:
  active: tencent
  refresh-seconds: 300
  timeout-seconds: 8
  log-success: true
```

Recommendations:

| Setting | Recommended | Notes |
|---------|-------------|-------|
| `refresh-seconds` | `300 - 600` | Don't set too low — avoid API rate limiting |
| `timeout-seconds` | `8 - 15` | Increase if network is unstable |
| `log-success` | `true` | Useful for monitoring sync health |

The default endpoint:

```yaml
sources:
  tencent:
    display-name: "Tencent Finance"
    endpoint: "https://qt.gtimg.cn/q={symbols}"
    encoding: "GBK"
    symbol-separator: ","
    max-symbols-per-request: 80
    user-agent: "SuperStocks/1.0"
```

Placeholder entries exist for Eastmoney, THS, Xueqiu, and Sina. The built-in parser only handles Tencent's `v_symbol="...~..."` format. If switching sources, use a compatible proxy that outputs the same format.

---

## Real Stock Markets

Configured under `markets` in `config.yml`.

Example:

```yaml
markets:
  cn:
    display-name: "A-Share"
    icon: EMERALD
    symbols:
      - "sh600519"
      - "sz000001"

  hk:
    display-name: "HK Stocks"
    icon: GOLD_INGOT
    symbols:
      - "hk00700"

  us:
    display-name: "US Stocks"
    icon: DIAMOND
    symbols:
      - "usAAPL"
```

Tencent symbol format:

| Market | Format | Example |
|--------|--------|---------|
| Shanghai A-Share | `sh` + code | `sh600519` |
| Shenzhen A-Share | `sz` + code | `sz000001` |
| Hong Kong | `hk` + code | `hk00700` |
| US | `us` + ticker | `usAAPL` |

Recommendations:

- Start with 10-30 stocks per market.
- Too many stocks increase sync time and API load.
- Avoid removing symbols that players already hold — orphaned holdings won't appear in rankings or valuations.

---

## Local Custom Markets

SuperStocks reads `.yml` files from:

```text
plugins/SuperStocks/CustomMarkets/
```

Each file defines one local virtual market. An example file is generated on first startup:

```text
plugins/SuperStocks/CustomMarkets/example.yml
```

Local markets don't call external APIs. They use a random-walk model close to real market behavior:

```text
new price = old price × (1 + drift% + random volatility%)
```

Local prices also pass through `gameplay-volatility`, so local and real markets share the same in-game volatility rules.

### Global Switch

```yaml
custom-markets:
  enabled: true
  global-tick-seconds: 300
```

Guidance:

| Playstyle | `global-tick-seconds` |
|-----------|----------------------|
| Stable | `600 - 1800` |
| Normal | `300 - 600` |
| Fast-paced | `60 - 180` |

### Market File Example

```yaml
market:
  id: local_tech
  display-name: "Local Tech Stocks"
  icon: AMETHYST_SHARD
  enabled: true

simulation:
  tick-seconds: 300
  tick-limit-percent: 15
  regimes:
    enabled: true
    duration-seconds: 3600

stocks:
  local_chip:
    symbol: "lcCHIP"
    name: "Block Chip Inc."
    enabled: true
    initial-price: 120.0
    min-price: 10.0
    max-price: 1000.0
    drift-percent-per-tick: 0.03
    volatility-percent: 2.0
```

### Local Stock Parameters

| Setting | Description | Guidance |
|---------|-------------|----------|
| `symbol` | Stock code | Use a prefix to avoid conflicts, e.g. `lcCHIP` |
| `name` | Display name | Shown in GUI |
| `initial-price` | Starting price | Match your server's economy scale |
| `min-price` | Price floor | Keep above 0 |
| `max-price` | Price ceiling | Prevents runaway prices |
| `drift-percent-per-tick` | Long-term trend | Typically `-0.2` to `0.2` |
| `volatility-percent` | Random swing strength | Stable `0.5-1.5`, normal `1.5-3.0`, risky `3.0-8.0` |

Tips:

- Local markets are great for server-made companies, town economies, event stocks, or lore-driven economies.
- Give a small positive `drift-percent-per-tick` (e.g. `0.02`) for stocks you want to trend upward long-term.
- For high-risk stocks, raise `volatility-percent` but keep `min-price` and `max-price` as guardrails.
- Don't set `drift-percent-per-tick` too high or prices will quickly leave the intended range.

---

## Gameplay Volatility Enhancement

Real stocks often have small daily moves that feel insignificant in Minecraft economies. `gameplay-volatility` transforms base prices into more dramatic in-game prices.

Processing order:

```text
Base quote → Price multiplier → Random offset → In-game display & trading price
```

Configuration:

```yaml
gameplay-volatility:
  price-multiplier:
    enabled: false
    multiplier: 1.0

  random-adjustment:
    enabled: false
    min-percent: -3.0
    max-percent: 3.0
    refresh-seconds: 300

  safety:
    min-price: 0.01
    max-price: 1000000000.0
```

### Price Multiplier

```yaml
price-multiplier:
  enabled: true
  multiplier: 5.0
```

In-game price = base price × multiplier.

| Server Type | `multiplier` |
|-------------|-------------|
| Vanilla-scale economy | `1.0 - 5.0` |
| Normal economy | `5.0 - 20.0` |
| High-inflation economy | `20.0 - 100.0` |

If unsure, keep disabled initially, observe the trading experience, then enable.

### Random Adjustment

```yaml
random-adjustment:
  enabled: true
  min-percent: -5.0
  max-percent: 5.0
  refresh-seconds: 300
```

Each stock gets an independent random offset (e.g. `-5%` to `+5%`), regenerated every `refresh-seconds`.

| Style | `min-percent` | `max-percent` | `refresh-seconds` |
|-------|--------------|--------------|-------------------|
| Stable | `-2.0` | `2.0` | `600 - 1800` |
| Normal | `-5.0` | `5.0` | `300 - 600` |
| Aggressive | `-10.0` | `10.0` | `60 - 180` |

### Safety Bounds

```yaml
safety:
  min-price: 0.01
  max-price: 1000000000.0
```

- Keep `min-price` at `0.01`.
- Set `max-price` to match your economy scale to prevent misconfiguration damage.

### Recommended Combinations

Normal survival economy server:

```yaml
gameplay-volatility:
  price-multiplier:
    enabled: true
    multiplier: 5.0
  random-adjustment:
    enabled: true
    min-percent: -5.0
    max-percent: 5.0
    refresh-seconds: 300
```

Fast-paced investment server:

```yaml
gameplay-volatility:
  price-multiplier:
    enabled: true
    multiplier: 20.0
  random-adjustment:
    enabled: true
    min-percent: -10.0
    max-percent: 10.0
    refresh-seconds: 120
```

---

## Automated Orders, Stop-Loss & Take-Profit

SuperStocks supports automated orders so players can set buy/sell conditions in advance.

```yaml
orders:
  enabled: true
  max-open-per-player: 10

automation:
  check-seconds: 30
```

Order types:

| Type | Command | Description |
|------|---------|-------------|
| Limit Buy | `/stocks order buy <symbol> <shares> <price> [seconds]` | Auto-buy when price reaches trigger |
| Limit Sell | `/stocks order sell <symbol> <shares> <price> [seconds]` | Auto-sell when price reaches trigger |
| Stop-Loss | `/stocks order stop-loss <symbol> <shares> <price> [seconds]` | Auto-sell when price drops to limit losses |
| Take-Profit | `/stocks order take-profit <symbol> <shares> <price> [seconds]` | Auto-sell when price rises to lock in gains |

Utility commands:

```text
/stocks order list
/stocks order cancel <id>
```

Mechanics:

- Limit buy orders reserve the estimated cost upfront; funds are returned if the order expires or is cancelled.
- Sell/stop/take-profit orders check available holdings to prevent over-selling.
- Orders are checked every `automation.check-seconds`.
- `orders.max-open-per-player` caps how many open orders each player can have.

Recommendations:

- Normal servers: `max-open-per-player: 5 - 10`.
- Investment-focused servers: `10 - 20`.
- Don't set `automation.check-seconds` below 10 to avoid excessive database scanning.

---

## Price Alerts & Watchlist

Players can track stocks of interest and receive notifications when price targets are hit.

Watchlist commands:

```text
/stocks watch add <symbol>
/stocks watch remove <symbol>
/stocks watch list
```

Alert commands:

```text
/stocks alert <symbol> above <price>
/stocks alert <symbol> below <price>
```

Configuration:

```yaml
alerts:
  enabled: true
```

The main GUI also has a watchlist entrance. Watchlists help players track key stocks; alerts pair well with limit/stop/take-profit orders.

---

## Quote Safety & Circuit Breaker

Public servers should keep these enabled to protect the economy from API anomalies, player spam, or misconfiguration.

Quote anomaly protection:

```yaml
quote-safety:
  reject-stale-quotes: true
  max-quote-age-seconds: 900
  reject-abnormal-change: true
  max-change-percent-per-sync: 25.0
  circuit-breaker:
    enabled: true
    stock-max-change-percent: 15.0
    freeze-seconds: 600
```

| Setting | Description | Guidance |
|---------|-------------|----------|
| `reject-stale-quotes` | Block trading with expired quotes | Enable for public servers |
| `max-quote-age-seconds` | Quote expiry threshold | 2-3× your refresh interval |
| `reject-abnormal-change` | Reject single-sync price spikes | Enable |
| `max-change-percent-per-sync` | Max allowed change per sync | `20 - 30` for normal servers |
| `circuit-breaker.enabled` | Freeze trading on abnormal in-game price swings | Enable |
| `freeze-seconds` | Freeze duration | `300 - 900` |

Trading rate limits:

```yaml
trading:
  paused: false
  cooldown-milliseconds: 1000
  max-trades-per-minute: 20
  max-shares-per-trade: 10000
  max-position-value: 0.0
```

| Setting | Description | Guidance |
|---------|-------------|----------|
| `paused` | Global trading pause | Toggle during maintenance |
| `cooldown-milliseconds` | Minimum interval between player trades | `500 - 2000` |
| `max-trades-per-minute` | Max manual trades per minute per player | `10 - 30` |
| `max-shares-per-trade` | Max shares per single trade | Match your economy scale |
| `max-position-value` | Max market value per stock per player (0 = unlimited) | Prevents monopolization |

Admin commands:

```text
/stocks pause
/stocks resume
/stocks status
```

---

## Fees, Short-Term Tax & Dividends

Base transaction fees:

```yaml
economy:
  transaction-tax-percent: 0.5
  min-shares: 1
  large-trade-threshold-shares: 1000
  large-trade-extra-tax-percent: 0.2
```

Guidance:

- `transaction-tax-percent`: `0.2 - 1.0` for normal servers.
- Large trade extra tax discourages high-frequency whale trades.
- Raise `min-shares` to match your economy scale.

Short-term tax:

```yaml
economy:
  short-term-tax:
    enabled: false
    minimum-holding-hours: 24
    percent: 1.0
```

Charges an extra fee when selling positions held for less than the minimum duration. Encourages long-term holding.

Dividends:

```yaml
dividends:
  enabled: false
  interval-hours: 168
  yield-percent: 0.5
  minimum-holding-hours: 24
```

| Setting | Description | Guidance |
|---------|-------------|----------|
| `interval-hours` | Payout interval | `168` = weekly |
| `yield-percent` | % of market value paid out | `0.1 - 1.0` |
| `minimum-holding-hours` | Minimum holding period to qualify | Prevents last-minute buying |

⚠️ Dividends continuously inject money into the economy. Disabled by default.

---

## Loss Penalty

`loss-penalty` triggers consequences when a player's portfolio is losing money. Penalties are selected by loss-percentage tier, then applied as Vault withdrawals or console commands.

Disabled by default:

```yaml
loss-penalty:
  enabled: false
```

### Loss Calculation

Uses current in-game prices:

```text
Cost basis = shares × average cost
Market value = shares × current price
Loss amount = max(0, cost basis - market value)
Loss % = loss amount ÷ cost basis × 100%
```

### Tier Configuration

```yaml
loss-penalty:
  check-interval-seconds: 600
  player-cooldown-seconds: 3600

  tiers:
    light:
      enabled: true
      min-loss-percent: 5.0
      max-loss-percent: 10.0
      fixed-amount: 0.0
      percent-of-loss: 0.5
      percent-of-portfolio-value: 0.0
      max-amount: 1000.0
```

The plugin selects the highest `min-loss-percent` tier that matches the current loss percentage.

Penalty formula:

```text
Charge = fixed-amount + loss × percent-of-loss% + portfolio value × percent-of-portfolio-value%
```

Tier guidance:

| Tier | Loss % | `percent-of-loss` | `percent-of-portfolio-value` | `max-amount` |
|------|--------|-------------------|------------------------------|-------------|
| light | `5% - 10%` | `0.5 - 1.0` | `0 - 0.2` | 0.5-1× daily player income |
| normal | `10% - 20%` | `1.0 - 3.0` | `0 - 0.5` | 1-2× daily player income |
| severe | `20%+` | `2.0 - 6.0` | `0.2 - 1.0` | 2-5× daily player income |

### Vault Withdrawal

```yaml
loss-penalty:
  actions:
    vault-withdraw:
      enabled: false
      never-negative-balance: true
```

Recommendations:

- Keep `never-negative-balance: true` on normal servers.
- Don't enable heavy penalties on a public survival server from day one.
- Start with notify-only, observe player losses, then gradually enable withdrawals.

### Console Commands

```yaml
loss-penalty:
  actions:
    commands:
      enabled: false
      list:
        - "effect give {player} minecraft:slowness 30 0"
```

Available variables:

| Variable | Description |
|----------|-------------|
| `{player}` | Player name |
| `{uuid}` | Player UUID |
| `{tier}` | Triggered tier ID |
| `{loss}` | Current loss amount |
| `{loss_percent}` | Current loss percentage |
| `{portfolio_value}` | Current portfolio market value |
| `{cost}` | Current cost basis |
| `{vault_penalty}` | This round's Vault charge |

### Player Notification

```yaml
loss-penalty:
  actions:
    notify:
      enabled: true
      message: "&cYour portfolio is down {loss} ({loss_percent}%), triggering {tier} tier penalty. Charge: {vault_penalty}."
```

This message lives in `config.yml` so you can edit it directly for your server's style and language.

---

## Short Selling

Players borrow shares to sell high, then buy back lower to return them. Short positions require a margin deposit and are subject to forced liquidation if the price rises too much.

```yaml
short-selling:
  enabled: true
  max-shares-per-position: 10000
  check-seconds: 120
  interest-percent-per-day: 0.0        # 0 = no interest
  margin:
    required-percent: 50                # Margin = position value × rate
    liquidation-percent: 50             # Auto-close when loss reaches margin × rate
```

Commands:

```text
/stocks short <symbol> <shares>       # Open a short
/stocks cover <id> [shares]           # Close all or part of a short
```

Mechanics:

- Opening a short freezes the margin deposit; it's returned when you close.
- If the stock price rises to the liquidation threshold, the position is force-closed and the margin is lost.
- Default daily interest is 0% — no silent money drain.
- Partial covers are supported.

---

## Investment Competitions

Create seasonal competitions where participants pay an entry fee for equal virtual capital, compete on returns, and win Vault rewards.

```yaml
competition:
  enabled: true
  rewards:
    - "1: 5000"     # 1st place reward
    - "2: 2000"     # 2nd place
    - "3: 1000"     # 3rd place
```

Player commands:

```text
/stocks competition list
/stocks competition join <id>
/stocks competition buy <id> <symbol> <shares>
/stocks competition sell <id> <symbol> <shares>
```

Admin commands:

```text
/stocks competition create <name> <days> <start-capital>
/stocks competition end <id>
```

Mechanics:

- Competitions use separate virtual portfolios — real holdings are untouched.
- Ending a competition auto-valuates all entries at current market prices, ranks them, and distributes rewards.
- Adjust reward amounts to match your server's economy scale.

---

## IPO Offerings

Admins can create IPOs for local market stocks. Players subscribe during the offering window; shares are allocated proportionally when it closes.

Player commands:

```text
/stocks ipo list
/stocks ipo subscribe <id> <shares>
```

Admin commands:

```text
/stocks ipo create <symbol> <name> <market> <price> <total-shares> <max-per-player>
/stocks ipo cancel <id>
```

Mechanics:

- IPO subscriptions pre-withdraw Vault funds. After allocation, excess is refunded.
- Oversubscribed IPOs scale down proportionally; unallocated funds are returned.
- Allocated shares go directly into player portfolios.

---

## Stock Certificates

Players can convert holdings into tradeable certificate items, enabling peer-to-peer trading, chest shops, or auctions.

```text
/stocks certificate issue <symbol> <shares>    # Convert to item
/stocks certificate list                       # View your certificates
```

Right-click the certificate item in the air to redeem it back into your portfolio. Certificates can be freely traded between players.

---

## BossBar, Index & Reports

**BossBar pinning** — Pin a stock's real-time price to the top of your screen:

```text
/stocks pin <symbol>       # Pin
/stocks unpin              # Remove
```

The bar color follows price direction (green up, red down) and the progress bar reflects intraday volatility.

**Market index** — View average price per market:

```text
/stocks index
```

**Market report** — Generate a text-based summary:

```text
/stocks report
```

---

## Admin Panel & Audit Log

**Admin GUI** — Visual overview of total holdings, active players, trades, sync status, trading pause state, and Vault health:

```text
/stocks admin panel
```

**Audit log** — Query the operation history:

```text
/stocks admin audit [player] [action]
```

Audit action types include: BUY, SELL, ORDER_CREATED, ORDER_EXECUTED, ADMIN_GIVE, ADMIN_REMOVE, ADMIN_CLEAR, SHORT_OPEN, SHORT_COVER, SHORT_LIQUIDATED, COMPETITION_CREATE, COMPETITION_JOIN, IPO_CREATE, IPO_ALLOCATED, CERT_REDEEM, and more.

---

## Leaderboards

Two leaderboards:

| Board | Command | Description |
|-------|---------|-------------|
| Winners | `/stocks rank winners` | Ranked by unrealized profit (highest first) |
| Losers | `/stocks rank losers` | Ranked by unrealized loss (highest first) |

Leaderboards depend on cached quotes. Stocks without successful syncs are excluded from that cycle's valuation.

---

## Languages

Set in `config.yml`:

```yaml
language: zh_CN
```

Language files live in:

```text
plugins/SuperStocks/Language/
```

Built-in languages:

| Code | Language |
|------|----------|
| `zh_CN` | 简体中文 |
| `zh_HK` | 繁體中文（香港） |
| `zh_MO` | 繁體中文（澳門） |
| `zh_TW` | 繁體中文（台灣） |
| `en_US` | English |
| `ja_JP` | 日本語 |
| `ko_KR` | 한국어 |
| `ar_SA` | العربية |
| `de_DE` | Deutsch |
| `es_ES` | Español |
| `fr_FR` | Français |
| `pt_BR` | Português do Brasil |
| `ru_RU` | Русский |

Missing keys fall back to `en_US` at runtime. To add a custom language, copy any existing file, rename it, edit the text, set it in `config.yml`, and run `/stocks reload`.

---

## PlaceholderAPI

| Placeholder | Description |
|-------------|-------------|
| `%superstocks_cash%` | Vault balance |
| `%superstocks_portfolio_value%` | Current portfolio market value |
| `%superstocks_total_value%` | Cash + portfolio value |
| `%superstocks_profit%` | Unrealized profit/loss |
| `%superstocks_profit_percent%` | Unrealized return percentage |
| `%superstocks_shares_<symbol>%` | Shares held of a specific stock |

Examples:

```text
%superstocks_profit_percent%
%superstocks_shares_usAAPL%
```

---

## Data Storage

SuperStocks uses SQLite to store holdings and trade data.

```yaml
database:
  file: "stocks.db"
```

Default location:

```text
plugins/SuperStocks/stocks.db
```

Recommendations:

- Back up `stocks.db` regularly.
- Do not edit the database directly while the server is running.
- Deleting the database will wipe all player holdings and trade history.

---

## Reloading

```text
/stocks reload
```

Reloads:

- `config.yml`
- Current language file
- Real market lists
- Local custom market files
- Quote provider parameters
- Gameplay volatility settings
- Loss penalty configuration
- Trading fees and minimum trade amounts

Database file path changes require a full server restart.

---

## Recommended Configurations

### Stable Survival Server

For normal survival economy — stocks as a side activity.

```yaml
gameplay-volatility:
  price-multiplier:
    enabled: false
  random-adjustment:
    enabled: true
    min-percent: -2.0
    max-percent: 2.0
    refresh-seconds: 900

loss-penalty:
  enabled: false
```

### Normal Economy Server

For servers where stocks are a core feature.

```yaml
gameplay-volatility:
  price-multiplier:
    enabled: true
    multiplier: 5.0
  random-adjustment:
    enabled: true
    min-percent: -5.0
    max-percent: 5.0
    refresh-seconds: 300

loss-penalty:
  enabled: false
```

### High-Risk Investment Server

For servers built around trading, rankings, and risk mechanics.

```yaml
gameplay-volatility:
  price-multiplier:
    enabled: true
    multiplier: 20.0
  random-adjustment:
    enabled: true
    min-percent: -10.0
    max-percent: 10.0
    refresh-seconds: 120

loss-penalty:
  enabled: true
  check-interval-seconds: 300
  player-cooldown-seconds: 3600
```

High-risk configurations should be paired with clear announcements, rules, or tutorials so players understand the penalty mechanics.

---

## Market Hours (Optional)

Restrict trading to real market hours. Disabled by default (24/7 trading).

```yaml
market-hours:
  cn:
    enabled: false
    timezone: "Asia/Shanghai"
    open: "09:30"
    close: "15:00"
    weekends: false
    closed-action: "DENY"
```

`closed-action`: `DENY` (block all trading) or `SELL_ONLY` (allow selling only during closed hours).

---

## Market Events (Optional)

Random "breaking news" events that cause price jumps in local markets. Disabled by default.

```yaml
market-events:
  enabled: false
  chance-percent-per-tick: 2.0
  min-impact-percent: -5.0
  max-impact-percent: 5.0
  announce-threshold-percent: 3.0
```

When an event's impact exceeds the threshold, a server-wide announcement is broadcast.

---

## FAQ

### Can't trade a stock?

The stock likely hasn't synced quotes yet. Try:

```text
/stocks sync
/stocks quote <symbol>
```

If still no data, check your server's network access to the quote API and verify the symbol is correct.

### Leaderboard shows no data?

Leaderboards require players to hold positions with cached quotes. Local stocks need at least one simulation tick; real stocks need a successful sync first.

### Vault or EssentialsX Economy not working?

Confirm:

- Vault is installed.
- A Vault economy plugin (e.g. EssentialsX Economy) is installed.
- Vault recognizes the economy provider.
- No economy plugin loading errors in console.

### Language didn't change after switching?

Verify the language code in `config.yml`, e.g.:

```yaml
language: en_US
```

Then run:

```text
/stocks reload
```

### Prices seem too volatile?

This is expected if `random-adjustment` is enabled. To reduce it, narrow `min-percent` / `max-percent` or increase `refresh-seconds`.

### Do local markets affect real stock quotes?

No. Local markets use independent configs in `CustomMarkets/` and never call external APIs. They pass through the same `gameplay-volatility` processing as real quotes.

---

## Disclaimer

SuperStocks is a virtual investment entertainment plugin for Minecraft servers. It does not provide, constitute, or imply any real-world investment advice. Real stock quote APIs may be subject to third-party stability, access restrictions, or field changes. Set reasonable sync intervals and comply with applicable terms of service.
