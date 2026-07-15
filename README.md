# SuperStocks

SuperStocks 是一个面向 Paper / Purpur 服务器的 Minecraft 虚拟股票投资插件。插件可以把现实股票行情同步到服务器内，也可以创建完全本地模拟的自定义股市。玩家可以通过箱子 GUI 或命令买入、卖出、查询股票，所有交易通过 Vault 经济系统结算。

这个插件适合希望在服务器中加入长期经济玩法、投资系统、排行榜竞争、虚拟金融活动或高风险高收益玩法的服务器。它不会改变现实股票数据本身，但可以通过配置让游戏内股价更适合 Minecraft 经济规模，例如放大价格、增强随机波动、设置亏损代价等。

SuperStocks 是娱乐用途插件，不构成任何现实投资建议。

---

## 主要功能

- 现实行情同步，默认使用腾讯财经免 key 接口。
- 支持 A 股、港股、美股市场分类。
- 支持本地自定义虚拟股市，每个市场单独一个配置文件。
- 支持游戏内股价波动增强，让现实行情在服务器经济中更明显。
- 支持 Vault 经济系统，兼容 EssentialsX Economy 等 Vault 经济插件。
- 支持箱子 GUI 交易界面，含二次确认、分页、走势图。
- 支持命令买入、卖出、做空、查询、持仓、排行榜。
- 支持限价单、止损单、止盈单等自动订单。
- 支持做空交易，含保证金和强制平仓机制。
- 支持投资大赛，赛季制独立虚拟资金比拼。
- 支持 IPO 新股发行，玩家申购后按比例分配。
- 支持股票凭证物品，持仓可转为可交易的实体物品。
- 支持 BossBar 实时固定股价。
- 支持市场综合指数查询。
- 支持管理面板 GUI 和审计日志查询。
- 支持 SQLite 本地存储玩家持仓和交易数据。
- 支持 PlaceholderAPI 占位符。
- 支持 13 个内置语言文件。
- 支持股神榜和牛马榜，按当前浮动盈亏排行。
- 支持亏损比例代价，可按亏损档位动态扣除 Vault 金额或执行命令。

---

## 运行要求

| 项目 | 要求 |
| --- | --- |
| 服务端 | Paper / Purpur 兼容服务端 |
| Java | Java 21 |
| 必需依赖 | Vault + 一个 Vault 经济插件 |
| 推荐经济插件 | EssentialsX Economy |
| 可选依赖 | PlaceholderAPI |
| 数据库 | SQLite，插件自动创建 |
| 网络 | 如果使用现实行情，服务器需要能访问配置的数据源接口 |

推荐环境：

- Minecraft 1.21.x Paper / Purpur
- Java 21
- Vault
- EssentialsX + EssentialsX Economy
- PlaceholderAPI，如果需要在计分板、菜单、聊天等地方显示玩家资产信息

---

## 安装方法

1. 安装 `Vault`。
2. 安装 `EssentialsX Economy` 或其他支持 Vault 的经济插件。
3. 可选安装 `PlaceholderAPI`。
4. 将 `SuperStocks.jar` 放入服务器 `plugins` 文件夹。
5. 启动服务器，让插件生成默认配置文件。
6. 关闭服务器或使用 `/stocks reload` 前，按需要修改：

```text
plugins/SuperStocks/config.yml
plugins/SuperStocks/Language/
plugins/SuperStocks/CustomMarkets/
```

首次启动后通常会生成：

```text
plugins/SuperStocks/config.yml
plugins/SuperStocks/stocks.db
plugins/SuperStocks/Language/zh_CN.yml
plugins/SuperStocks/CustomMarkets/example.yml
```

---

## 快速开始

如果你只想先让插件跑起来，可以按这个流程配置：

1. 确认 Vault 经济正常工作。
2. 保持默认语言：

```yaml
language: zh_CN
```

3. 保持默认行情源：

```yaml
stock-provider:
  active: tencent
```

4. 暂时关闭高风险玩法：

```yaml
gameplay-volatility:
  price-multiplier:
    enabled: false
  random-adjustment:
    enabled: false

loss-penalty:
  enabled: false
```

5. 进入服务器执行：

```text
/stocks sync
/stocks
```

如果 GUI 中能看到行情并能买卖股票，说明基础配置已经可用。之后再逐步开启游戏内波动增强、本地股市和亏损代价。

---

## 命令

### 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/stocks` | 打开股票 GUI |
| `/stocks help` | 查看帮助 |
| `/stocks open` | 打开股票 GUI |
| `/stocks quote <代码>` | 查询股票行情 |
| `/stocks buy <代码> <数量>` | 买入股票 |
| `/stocks sell <代码> <数量>` | 卖出股票 |
| `/stocks portfolio` | 查看文字版持仓 |
| `/stocks history <代码>` | 查看股票价格历史 |
| `/stocks stats` | 查看个人投资统计 |
| `/stocks watch add <代码>` | 添加自选股 |
| `/stocks watch remove <代码>` | 移除自选股 |
| `/stocks watch list` | 查看自选股 |
| `/stocks alert <代码> <above|below> <价格>` | 创建价格提醒 |
| `/stocks order buy <代码> <数量> <价格> [有效秒数]` | 创建限价买单 |
| `/stocks order sell <代码> <数量> <价格> [有效秒数]` | 创建限价卖单 |
| `/stocks order stop-loss <代码> <数量> <价格> [有效秒数]` | 创建止损单 |
| `/stocks order take-profit <代码> <数量> <价格> [有效秒数]` | 创建止盈单 |
| `/stocks order list` | 查看开放订单 |
| `/stocks order cancel <订单ID>` | 取消订单 |
| `/stocks rank winners` | 查看股神榜 |
| `/stocks rank losers` | 查看牛马榜 |
| `/stocks short <代码> <数量>` | 做空股票 |
| `/stocks cover <ID> [数量]` | 平空仓 |
| `/stocks index` | 查看市场指数 |
| `/stocks pin <代码>` | 固定股价到 BossBar |
| `/stocks unpin` | 取消 BossBar 固定 |
| `/stocks report` | 查看市场报告 |
| `/stocks competition list` | 查看投资大赛列表 |
| `/stocks competition join <ID>` | 参加投资大赛 |
| `/stocks competition buy <ID> <代码> <数量>` | 大赛内买入 |
| `/stocks competition sell <ID> <代码> <数量>` | 大赛内卖出 |
| `/stocks certificate issue <代码> <数量>` | 发行股票凭证 |
| `/stocks certificate list` | 查看持有的凭证 |
| `/stocks ipo list` | 查看 IPO 列表 |
| `/stocks ipo subscribe <ID> <数量>` | 申购新股 |

示例：

```text
/stocks quote sh600519
/stocks buy sh600519 10
/stocks sell sh600519 5
/stocks portfolio
/stocks rank winners
```

### 管理员命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/stocks sync` | `superstocks.admin` | 手动同步行情 |
| `/stocks status` | `superstocks.admin` | 查看系统状态 |
| `/stocks pause` | `superstocks.admin` | 暂停股票交易 |
| `/stocks resume` | `superstocks.admin` | 恢复股票交易 |
| `/stocks penalty history <玩家>` | `superstocks.admin` | 查看玩家亏损代价记录 |
| `/stocks admin portfolio <玩家>` | `superstocks.admin` | 查看玩家持仓 |
| `/stocks admin give <玩家> <代码> <数量>` | `superstocks.admin` | 增加玩家持仓 |
| `/stocks admin remove <玩家> <代码> <数量>` | `superstocks.admin` | 扣除玩家持仓 |
| `/stocks admin clear <玩家>` | `superstocks.admin` | 清空玩家持仓 |
| `/stocks reload` | `superstocks.admin` | 重载配置、语言、市场和交易参数 |
| `/stocks admin panel` | `superstocks.admin` | 打开管理面板 GUI |
| `/stocks admin audit [玩家] [操作]` | `superstocks.admin` | 查看审计日志 |
| `/stocks competition create <名称> <天数> <初始资金>` | `superstocks.admin` | 创建投资大赛 |
| `/stocks competition end <ID>` | `superstocks.admin` | 结束投资大赛并结算奖励 |
| `/stocks ipo create <代码> <名称> <市场> <价格> <总量> <每人上限>` | `superstocks.admin` | 创建 IPO |
| `/stocks ipo cancel <ID>` | `superstocks.admin` | 取消 IPO |

### 权限节点

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `superstocks.use` | 所有玩家 | 使用股票 GUI 和玩家命令 |
| `superstocks.admin` | OP | 使用管理员命令 |

---

## GUI 说明

主界面包含：

- 市场入口
- 我的持仓
- 股神榜
- 牛马榜
- 行情统计

市场界面会显示：

- 股票名称
- 股票代码
- 当前价格
- 涨跌额
- 涨跌幅
- 更新时间

股票详情页支持快捷交易，默认数量为：

```yaml
gui:
  trade-amounts:
    - 1
    - 10
    - 100
```

你可以根据服务器经济规模修改这些数量。例如高货币量服务器可以改为：

```yaml
gui:
  trade-amounts:
    - 10
    - 100
    - 1000
```

---

## 行情源配置

默认配置使用腾讯财经：

```yaml
stock-provider:
  active: tencent
  refresh-seconds: 300
  timeout-seconds: 8
  log-success: true
```

建议：

| 配置 | 推荐值 | 说明 |
| --- | --- | --- |
| `refresh-seconds` | `300 - 600` | 现实行情同步间隔，不建议过低，避免接口限流 |
| `timeout-seconds` | `8 - 15` | 网络较差时可适当调高 |
| `log-success` | `true` | 方便观察同步数量和缓存状态 |

默认数据源配置：

```yaml
stock-provider:
  sources:
    tencent:
      display-name: "腾讯财经"
      endpoint: "https://qt.gtimg.cn/q={symbols}"
      encoding: "GBK"
      symbol-separator: ","
      max-symbols-per-request: 80
      user-agent: "SuperStocks/1.0"
```

配置中预留了东方财富、同花顺、雪球、新浪财经等数据源项。当前内置解析逻辑按腾讯财经 `v_symbol="...~..."` 格式解析。如果切换其他数据源，建议使用能输出兼容格式的代理接口。

---

## 现实股票市场配置

现实行情股票在 `config.yml` 的 `markets` 中配置。

示例：

```yaml
markets:
  cn:
    display-name: "A股"
    icon: EMERALD
    symbols:
      - "sh600519"
      - "sz000001"

  hk:
    display-name: "港股"
    icon: GOLD_INGOT
    symbols:
      - "hk00700"

  us:
    display-name: "美股"
    icon: DIAMOND
    symbols:
      - "usAAPL"
```

常用腾讯财经代码格式：

| 市场 | 示例 |
| --- | --- |
| 上海 A 股 | `sh600519` |
| 深圳 A 股 | `sz000001` |
| 港股 | `hk00700` |
| 美股 | `usAAPL` |

建议：

- 每个市场初期配置 `10 - 30` 只股票即可。
- 股票太多会增加行情同步耗时和外部接口压力。
- 不建议频繁删除已有玩家持仓的股票代码，否则这些持仓可能因为没有行情而无法参与排行或正常估值。

---

## 本地自定义股市

SuperStocks 支持本地虚拟股市。插件会读取：

```text
plugins/SuperStocks/CustomMarkets/
```

每个 `.yml` 文件代表一个本地市场。首次启动会生成示例：

```text
plugins/SuperStocks/CustomMarkets/example.yml
```

本地股市不会请求外部行情接口，而是使用接近现实市场的随机游走模型模拟价格变化：

```text
新价格 = 旧价格 × (1 + 长期趋势% + 随机波动%)
```

本地股市生成基础价格后，仍会经过 `gameplay-volatility` 处理，因此本地股市和现实股市可以共享统一的游戏内波动规则。

### 总开关

```yaml
custom-markets:
  enabled: true
  global-tick-seconds: 300
```

建议：

| 玩法风格 | `global-tick-seconds` |
| --- | --- |
| 稳健 | `600 - 1800` |
| 普通 | `300 - 600` |
| 快节奏 | `60 - 180` |

### 市场文件示例

```yaml
market:
  id: local_tech
  display-name: "本地科技股"
  icon: AMETHYST_SHARD
  enabled: true

simulation:
  tick-seconds: 300

stocks:
  local_chip:
    symbol: "lcCHIP"
    name: "方块芯片"
    enabled: true
    initial-price: 120.0
    min-price: 10.0
    max-price: 1000.0
    drift-percent-per-tick: 0.03
    volatility-percent: 2.0
```

### 本地股票参数

| 配置 | 说明 | 建议 |
| --- | --- | --- |
| `symbol` | 股票代码 | 建议加前缀，如 `lcCHIP`，避免和真实股票冲突 |
| `name` | 股票名称 | GUI 中显示 |
| `initial-price` | 初始价格 | 按服务器经济规模设置 |
| `min-price` | 最低价格 | 大于 0，防止价格归零 |
| `max-price` | 最高价格 | 防止价格失控 |
| `drift-percent-per-tick` | 长期趋势 | 通常设置在 `-0.2` 到 `0.2` |
| `volatility-percent` | 随机波动强度 | 稳健 `0.5 - 1.5`，普通 `1.5 - 3.0`，高风险 `3.0 - 8.0` |

推荐做法：

- 本地股市适合服务器自创公司、城镇经济、活动股票、剧情经济。
- 如果想让某只股票长期偏上涨，可以给 `drift-percent-per-tick` 设置小正数，例如 `0.02`。
- 如果想模拟高风险股票，可以提高 `volatility-percent`，但建议配合 `min-price` 和 `max-price`。
- 不建议把 `drift-percent-per-tick` 设置得过高，否则价格会快速脱离正常范围。

---

## 游戏内股市波动增强

现实股票的日常波动通常较小，直接放进 Minecraft 经济系统后，玩家可能感受不到明显收益变化。`gameplay-volatility` 可以把现实价格或本地模拟价格转换为更适合服务器经济的游戏内价格。

处理顺序：

```text
基础行情价格 -> 固定价格倍数 -> 随机波动调整 -> 游戏内展示和交易价格
```

基础配置：

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

### 固定价格倍数

```yaml
price-multiplier:
  enabled: true
  multiplier: 5.0
```

作用：

```text
游戏内价格 = 基础行情价格 × multiplier
```

建议：

| 服务器类型 | `multiplier` |
| --- | --- |
| 接近原版经济 | `1.0 - 5.0` |
| 普通经济服 | `5.0 - 20.0` |
| 高通胀经济服 | `20.0 - 100.0` |

如果不确定服务器经济规模，建议先保持关闭，确认交易体验后再开启。

### 随机波动增强

```yaml
random-adjustment:
  enabled: true
  min-percent: -5.0
  max-percent: 5.0
  refresh-seconds: 300
```

随机波动会给每只股票生成一个独立偏移，例如 `-5%` 到 `+5%`。偏移会按 `refresh-seconds` 重新生成。

建议：

| 风格 | `min-percent` | `max-percent` | `refresh-seconds` |
| --- | --- | --- | --- |
| 稳健 | `-2.0` | `2.0` | `600 - 1800` |
| 普通 | `-5.0` | `5.0` | `300 - 600` |
| 高风险 | `-10.0` | `10.0` | `60 - 180` |

### 安全价格范围

```yaml
safety:
  min-price: 0.01
  max-price: 1000000000.0
```

建议：

- `min-price` 保持 `0.01` 即可。
- `max-price` 应按服务器经济规模设置，防止配置错误导致价格过高。

### 推荐组合

普通生存经济服：

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

快节奏投资服：

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

## 自动订单、止损和止盈

SuperStocks 支持自动订单系统，适合让玩家提前设置买入或卖出条件。

启用配置：

```yaml
orders:
  enabled: true
  max-open-per-player: 10

automation:
  check-seconds: 30
```

支持的订单类型：

| 类型 | 命令 | 说明 |
| --- | --- | --- |
| 限价买单 | `/stocks order buy <代码> <数量> <价格> [有效秒数]` | 当前价格达到触发价时自动买入 |
| 限价卖单 | `/stocks order sell <代码> <数量> <价格> [有效秒数]` | 当前价格达到触发价时自动卖出 |
| 止损单 | `/stocks order stop-loss <代码> <数量> <价格> [有效秒数]` | 价格跌到目标时自动卖出，控制亏损 |
| 止盈单 | `/stocks order take-profit <代码> <数量> <价格> [有效秒数]` | 价格涨到目标时自动卖出，锁定收益 |

常用命令：

```text
/stocks order list
/stocks order cancel <订单ID>
```

机制说明：

- 限价买单创建时会预扣预计资金，订单取消或过期后退回。
- 限价卖单、止损单、止盈单会检查可用持仓，避免重复挂出超过实际持仓的股票。
- 自动订单会按 `automation.check-seconds` 周期检查。
- 每个玩家的最大开放订单数由 `orders.max-open-per-player` 控制。

建议：

- 普通服务器建议 `max-open-per-player: 5 - 10`。
- 投资玩法服务器可以设置为 `10 - 20`。
- `automation.check-seconds` 不建议低于 `10`，避免频繁扫描数据库。

---

## 价格提醒和自选股

玩家可以把关注的股票加入自选股，也可以设置价格提醒。

自选股命令：

```text
/stocks watch add <代码>
/stocks watch remove <代码>
/stocks watch list
```

价格提醒命令：

```text
/stocks alert <代码> above <价格>
/stocks alert <代码> below <价格>
```

配置：

```yaml
alerts:
  enabled: true
```

GUI 主界面也提供自选股入口。自选股适合玩家跟踪少量重点股票，价格提醒适合配合限价单、止损单和止盈单使用。

---

## 行情异常保护和交易风控

公开服务器建议保持行情保护和交易风控开启，避免外部接口异常、玩家高频操作或配置错误影响经济系统。

行情异常保护：

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

说明：

| 配置 | 说明 | 建议 |
| --- | --- | --- |
| `reject-stale-quotes` | 拒绝过期行情交易 | 公开服务器建议开启 |
| `max-quote-age-seconds` | 行情超过该时间视为过期 | 外部刷新间隔的 2 - 3 倍 |
| `reject-abnormal-change` | 拒绝单次异常跳价 | 建议开启 |
| `max-change-percent-per-sync` | 外部行情单次最大允许变化 | 普通服 `20 - 30` |
| `circuit-breaker.enabled` | 游戏内价格异常时冻结交易 | 建议开启 |
| `freeze-seconds` | 熔断冻结时间 | `300 - 900` |

交易风控：

```yaml
trading:
  paused: false
  cooldown-milliseconds: 1000
  max-trades-per-minute: 20
  max-shares-per-trade: 10000
  max-position-value: 0.0
```

说明：

| 配置 | 说明 | 建议 |
| --- | --- | --- |
| `paused` | 全局暂停交易 | 维护或事故处理时开启 |
| `cooldown-milliseconds` | 玩家两次手动交易间隔 | `500 - 2000` |
| `max-trades-per-minute` | 每分钟最大手动交易次数 | `10 - 30` |
| `max-shares-per-trade` | 单笔最大股数 | 按服务器经济规模设置 |
| `max-position-value` | 单只股票最大持仓市值，0 为不限制 | 防止玩家集中垄断单只股票 |

管理员也可以使用：

```text
/stocks pause
/stocks resume
/stocks status
```

---

## 手续费、短线税和股息

基础交易费用：

```yaml
economy:
  transaction-tax-percent: 0.5
  min-shares: 1
  large-trade-threshold-shares: 1000
  large-trade-extra-tax-percent: 0.2
```

建议：

- `transaction-tax-percent` 普通服务器建议 `0.2 - 1.0`。
- 大额交易附加税适合抑制超大单高频套利。
- `min-shares` 可以按服务器经济规模提高。

短线税：

```yaml
economy:
  short-term-tax:
    enabled: false
    minimum-holding-hours: 24
    percent: 1.0
```

短线税会对持仓时间较短的卖出交易收取额外费用，适合鼓励长期持有。

股息系统：

```yaml
dividends:
  enabled: false
  interval-hours: 168
  yield-percent: 0.5
  minimum-holding-hours: 24
```

说明：

| 配置 | 说明 | 建议 |
| --- | --- | --- |
| `interval-hours` | 股息发放间隔 | `168` 表示每周 |
| `yield-percent` | 按持仓市值发放的比例 | `0.1 - 1.0` |
| `minimum-holding-hours` | 最低持仓时长 | 防止发放前临时买入 |

---

## 亏损比例代价

`loss-penalty` 用于在玩家投资组合亏损时触发额外代价。代价按亏损比例选择档位，再根据档位计算 Vault 扣款或执行命令。

默认关闭：

```yaml
loss-penalty:
  enabled: false
```

### 亏损计算

插件会使用当前游戏内价格计算：

```text
持仓成本 = 持股数量 × 平均成本
持仓市值 = 持股数量 × 当前价格
亏损金额 = max(0, 持仓成本 - 持仓市值)
亏损比例 = 亏损金额 / 持仓成本 × 100%
```

### 档位配置

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

插件会选择符合当前亏损比例且 `min-loss-percent` 最高的档位。

扣款公式：

```text
扣款 = fixed-amount + 亏损金额 × percent-of-loss% + 持仓市值 × percent-of-portfolio-value%
```

建议档位：

| 档位 | 亏损比例 | `percent-of-loss` | `percent-of-portfolio-value` | `max-amount` |
| --- | --- | --- | --- | --- |
| light | `5% - 10%` | `0.5 - 1.0` | `0 - 0.2` | 普通玩家日收入 0.5 - 1 倍 |
| normal | `10% - 20%` | `1.0 - 3.0` | `0 - 0.5` | 普通玩家日收入 1 - 2 倍 |
| severe | `20%+` | `2.0 - 6.0` | `0.2 - 1.0` | 普通玩家日收入 2 - 5 倍 |

### Vault 扣款

```yaml
loss-penalty:
  actions:
    vault-withdraw:
      enabled: true
      never-negative-balance: true
```

建议：

- 普通服务器建议保持 `never-negative-balance: true`。
- 不建议在公共生存服一开始就启用高额亏损代价。
- 可以先只启用通知，观察玩家亏损规模，再开启扣款。

### 控制台命令

```yaml
loss-penalty:
  actions:
    commands:
      enabled: true
      list:
        - "effect give {player} minecraft:slowness 30 0"
```

可用变量：

| 变量 | 说明 |
| --- | --- |
| `{player}` | 玩家名 |
| `{uuid}` | 玩家 UUID |
| `{tier}` | 触发档位 ID |
| `{loss}` | 当前亏损金额 |
| `{loss_percent}` | 当前亏损比例 |
| `{portfolio_value}` | 当前持仓市值 |
| `{cost}` | 当前持仓成本 |
| `{vault_penalty}` | 本次 Vault 扣款金额 |

### 玩家通知

```yaml
loss-penalty:
  actions:
    notify:
      enabled: true
      message: "&c你的投资组合亏损 {loss} ({loss_percent}%)，触发 {tier} 档亏损代价，本次扣款 {vault_penalty}。"
```

这条消息放在 `config.yml` 中，方便服务器按自己的玩法风格和语言直接修改。

---

## 排行榜

SuperStocks 提供两个排行榜：

| 排行榜 | 命令 | 说明 |
| --- | --- | --- |
| 股神榜 | `/stocks rank winners` | 按当前浮动盈利从高到低排序 |
| 牛马榜 | `/stocks rank losers` | 按当前浮动亏损从高到低排序 |

排行榜计算依赖当前缓存行情。如果某只股票没有成功同步行情，它不会参与当次估值。

建议：

- 排行榜适合配合服务器活动或赛季经济使用。
- 如果开启亏损代价，建议先明确告知玩家排行榜和亏损规则。

---

## 多语言

配置项：

```yaml
language: zh_CN
```

语言文件目录：

```text
plugins/SuperStocks/Language/
```

内置语言：

| Locale | 语言 |
| --- | --- |
| `ar_SA` | العربية |
| `de_DE` | Deutsch |
| `en_US` | English |
| `es_ES` | Español |
| `fr_FR` | Français |
| `ja_JP` | 日本語 |
| `ko_KR` | 한국어 |
| `pt_BR` | Português do Brasil |
| `ru_RU` | Русский |
| `zh_CN` | 简体中文 |
| `zh_HK` | 繁體中文（香港） |
| `zh_MO` | 繁體中文（澳門） |
| `zh_TW` | 繁體中文（台灣） |

新增或修改语言：

1. 复制一个已有语言文件。
2. 重命名为新的语言代码，例如 `ko_KR.yml`。
3. 修改其中显示文本。
4. 在 `config.yml` 中设置：

```yaml
language: ko_KR
```

5. 执行：

```text
/stocks reload
```

---

## PlaceholderAPI

安装 PlaceholderAPI 后可使用以下占位符：

| 占位符 | 说明 |
| --- | --- |
| `%superstocks_cash%` | 玩家 Vault 余额 |
| `%superstocks_portfolio_value%` | 持仓市值 |
| `%superstocks_total_value%` | 余额 + 持仓市值 |
| `%superstocks_profit%` | 当前浮动盈亏 |
| `%superstocks_profit_percent%` | 当前收益率 |
| `%superstocks_shares_sh600519%` | 指定股票持仓数量 |

示例：

```text
%superstocks_profit_percent%
%superstocks_shares_usAAPL%
```

---

## 数据存储

SuperStocks 使用 SQLite 保存持仓和交易数据。

配置：

```yaml
database:
  file: "stocks.db"
```

默认文件位置：

```text
plugins/SuperStocks/stocks.db
```

建议：

- 定期备份 `stocks.db`。
- 不建议在服务器运行中直接编辑数据库。
- 如果删除数据库，玩家持仓和交易记录会丢失。

---

## 配置重载

执行：

```text
/stocks reload
```

会重载：

- `config.yml`
- 当前语言文件
- 现实市场列表
- 本地自定义股市文件
- 行情源参数
- 游戏内波动配置
- 亏损代价配置
- 交易税率和最低交易数量

数据库文件路径不建议在服务器运行中频繁修改。

---

## 推荐配置方案

### 稳健生存服

适合普通生存经济，不希望股市影响过大。

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

### 普通经济服

适合希望股票成为一项稳定玩法的服务器。

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

### 高风险投资服

适合明确以投资、排行、风险惩罚为核心玩法的服务器。

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

高风险配置建议配合公告、规则说明或新手提示，避免玩家误解亏损代价。

---

## 做空交易

做空允许玩家借入股票高价卖出，等股价下跌后再低价买回归还。做空需要缴纳保证金，亏损超过保证金一定比例会被强制平仓。

```
/stocks short <代码> <数量>          # 开空仓
/stocks cover <ID> [数量]            # 平空仓（部分或全部）
```

配置：

```yaml
short-selling:
  enabled: true                      # 总开关
  max-shares-per-position: 10000     # 单笔最大做空股数
  check-seconds: 120                 # 强平检测间隔
  interest-percent-per-day: 0.0      # 日利息，0 = 免息
  margin:
    required-percent: 50             # 保证金比例
    liquidation-percent: 50          # 强平触发线（亏损 ÷ 保证金）
```

说明：

- 开空仓时冻结保证金，平仓后退还。
- 股价涨到触发强平线时，插件自动平仓、扣除保证金。
- 默认日利息为 0%，不会偷偷扣钱。

---

## 投资大赛

创建赛季制投资比赛：每个参赛玩家支付报名费获得等额虚拟资金，在赛季内自由买卖，赛季结束按总资产排名发放 Vault 奖励。

```yaml
competition:
  enabled: true
  rewards:
    - "1: 5000"     # 第 1 名奖励
    - "2: 2000"     # 第 2 名
    - "3: 1000"     # 第 3 名
```

命令：

```
/stocks competition list
/stocks competition join <ID>
/stocks competition buy <ID> <代码> <数量>
/stocks competition sell <ID> <代码> <数量>
```

管理员命令：

```
/stocks competition create <名称> <天数> <初始资金>
/stocks competition end <ID>
```

说明：

- 大赛使用独立虚拟资金，不影响玩家真实持仓。
- 结束大赛时自动按当前行情估值、排名、发奖。
- 奖励金额务必根据服务器经济规模调整。

---

## IPO 新股发行

管理员可以为本地自定义股票创建 IPO（首次公开发行），玩家在申购期内申购，申购结束后按比例分配。

```
/stocks ipo list
/stocks ipo subscribe <ID> <数量>
```

管理员命令：

```
/stocks ipo create <代码> <名称> <市场> <价格> <总量> <每人上限>
/stocks ipo cancel <ID>
```

说明：

- IPO 申购时预扣 Vault 资金，分配后多退少补。
- 超额认购时按比例缩股，未中签部分自动退款。
- 分配完成后股票直接进入玩家持仓。

---

## 股票凭证

玩家可以把持仓转为可交易的实体物品（凭证），方便在箱子商店、拍卖行或玩家间交易。

```
/stocks certificate issue <代码> <数量>    # 转为凭证物品
/stocks certificate list                   # 查看持有凭证
```

获得凭证物品后，右键空气即可兑换回持仓。凭证可以在玩家间自由转手。

---

## BossBar 股价固定

把一只股票的实时价格固定到屏幕顶部的 BossBar 上，边走边看。

```
/stocks pin <代码>      # 固定
/stocks unpin            # 取消
```

BossBar 颜色随涨跌变化，进度条反映日内波动幅度。

---

## 市场指数与报告

查看各市场的综合均价：

```
/stocks index
```

生成文字版市场摘要：

```
/stocks report
```

---

## 管理面板与审计日志

打开可视化管理员面板：

```
/stocks admin panel
```

查询操作审计记录：

```
/stocks admin audit [玩家名] [操作类型]
```

审计操作类型包括：BUY / SELL / ORDER_CREATED / ADMIN_GIVE / SHORT_OPEN / IPO_ALLOCATED 等。

---

## 常见问题

### 股票无法交易怎么办？

通常是该股票还没有成功同步行情。可以尝试：

```text
/stocks sync
/stocks quote <代码>
```

如果仍然没有行情，请检查服务器是否能访问行情接口，以及股票代码是否正确。

### 排行榜为什么没有数据？

排行榜需要玩家拥有持仓，并且相关股票有缓存行情。本地自定义股票需要先完成一次模拟 tick，现实股票需要先同步成功。

### Vault 或 EssentialsX Economy 不生效怎么办？

请确认：

- 已安装 Vault。
- 已安装 EssentialsX Economy 或其他 Vault 经济插件。
- Vault 能识别当前经济服务。
- 控制台没有经济插件加载错误。

### 切换语言后为什么没变化？

确认 `config.yml` 中的语言代码正确，例如：

```yaml
language: en_US
```

然后执行：

```text
/stocks reload
```

### 开启随机波动后价格为什么变化更明显？

这是正常现象。`random-adjustment` 会在现实行情或本地模拟行情基础上增加游戏内随机偏移。想降低波动，可以缩小 `min-percent` / `max-percent`，或调高 `refresh-seconds`。

### 本地股市会不会影响现实行情？

不会。本地股市使用 `CustomMarkets` 中的独立配置，不请求外部接口。它只会和现实行情一样，最终经过 `gameplay-volatility` 生成游戏内交易价格。

---

## 免责声明

SuperStocks 是 Minecraft 服务器内的虚拟投资娱乐插件，不提供、不构成、也不暗示任何现实投资建议。现实行情接口可能受第三方服务稳定性、访问限制、字段变化影响。请合理设置同步间隔，并遵守相关服务条款。
