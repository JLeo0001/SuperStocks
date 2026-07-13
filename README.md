# SuperStocks

**SuperStocks** 是一个面向 Paper/Purpur 服务器的 Minecraft 虚拟股票投资插件。插件将现实行情同步到服务器内，玩家可以通过箱子 GUI 或命令买入、卖出、查询股票，并通过 Vault 经济系统完成资金结算。

> 默认数据源为腾讯财经。插件同时预留东方财富、同花顺、雪球、新浪财经配置项。当前非腾讯源建议接入返回腾讯兼容格式的代理接口，后续可继续扩展专用解析器。

---

## 功能概览

- 现实股票行情同步，默认腾讯财经免 key 接口。
- 支持 A 股、港股、美股市场分类。
- 每个市场默认内置 20 只股票，可自由增删。
- Vault 经济系统对接，EssentialsX Economy 可通过 Vault 使用。
- PlaceholderAPI 支持。
- 箱子 GUI 操作界面。
- 命令行交易、查询、持仓、排行榜。
- SQLite 本地持仓数据库。
- 多语言系统，支持 12 个内置语言文件。
- 股神榜与牛马榜，根据当前缓存行情计算浮动盈亏排名。
- GitHub Actions 自动构建，无需本地构建环境。

---

## 运行环境

| 项目 | 要求 |
| --- | --- |
| Java | 21 |
| 服务端 | Paper / Purpur 兼容服务端 |
| 经济插件 | Vault + EssentialsX Economy 或其他 Vault 经济插件 |
| 可选依赖 | PlaceholderAPI |
| 数据库 | SQLite，插件自动创建 |

---

## 安装步骤

1. 安装 `Vault`。
2. 安装 `EssentialsX` 与 `EssentialsX Economy`，或其他 Vault 经济插件。
3. 可选安装 `PlaceholderAPI`。
4. 将 `SuperStocks.jar` 放入 `plugins`。
5. 启动服务器生成配置文件。
6. 修改 `plugins/SuperStocks/config.yml`。
7. 使用 `/stocks reload` 重载配置。

---

## 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/stocks` | 打开股票 GUI |
| `/stocks help` | 查看所有可用命令 |
| `/stocks open` | 打开股票 GUI |
| `/stocks quote <代码>` | 查询股票行情 |
| `/stocks buy <代码> <数量>` | 通过命令买入股票 |
| `/stocks sell <代码> <数量>` | 通过命令卖出股票 |
| `/stocks portfolio` | 查看文字版持仓 |
| `/stocks rank winners` | 查看股神榜 |
| `/stocks rank losers` | 查看牛马榜 |

示例：

```text
/stocks quote sh600519
/stocks buy sh600519 10
/stocks sell sh600519 5
/stocks portfolio
/stocks rank winners
```

---

## 管理员命令

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/stocks sync` | `superstocks.admin` | 手动同步行情 |
| `/stocks reload` | `superstocks.admin` | 重载配置、语言和行情源参数 |

---

## 权限节点

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `superstocks.use` | true | 使用股票 GUI 和玩家命令 |
| `superstocks.admin` | OP | 使用管理员命令 |

---

## GUI 说明

主界面包含：

- A 股市场入口
- 港股市场入口
- 美股市场入口
- 我的持仓
- 股神榜
- 牛马榜
- 行情统计

市场界面会显示：

- 股票名称
- 股票代码
- 当前价格
- 涨跌幅
- 行情更新时间

股票详情页可快捷交易：

- 买入 1 / 10 / 100 股
- 卖出 1 / 10 / 100 股

快捷数量可在配置中修改：

```yaml
gui:
  trade-amounts:
    - 1
    - 10
    - 100
```

---

## 排行榜

### 股神榜

`/stocks rank winners`

按玩家当前持仓的浮动盈亏从高到低排序。

### 牛马榜

`/stocks rank losers`

按玩家当前持仓的浮动盈亏从低到高排序，即亏损越多越靠前。

排行榜只计算已有缓存行情的股票。如果某只股票当前没有成功同步行情，将不会计入当次排名计算。

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

新增语言方法：

1. 复制 `zh_CN.yml`。
2. 重命名为你的语言代码，例如 `ko_KR.yml`。
3. 翻译文件内容。
4. 修改 `config.yml`：

```yaml
language: ko_KR
```

5. 执行 `/stocks reload`。

---

## 行情源配置

默认启用腾讯财经：

```yaml
stock-provider:
  active: tencent
```

可选配置项：

```yaml
stock-provider:
  active: tencent
  sources:
    tencent:
      display-name: "腾讯财经"
      endpoint: "https://qt.gtimg.cn/q={symbols}"
      encoding: "GBK"
      symbol-separator: ","
      max-symbols-per-request: 80
      user-agent: "SuperStocks/1.0"
```

预留数据源：

- `tencent` 腾讯财经
- `eastmoney` 东方财富
- `ths` 同花顺
- `xueqiu` 雪球
- `sina` 新浪财经

注意：当前内置解析逻辑按腾讯财经 `v_symbol="...~..."` 格式解析。切换到其他源时，建议使用能输出兼容格式的代理接口。

---

## 股票代码配置

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

腾讯财经常用代码格式：

| 市场 | 示例 |
| --- | --- |
| 上海 A 股 | `sh600519` |
| 深圳 A 股 | `sz000001` |
| 港股 | `hk00700` |
| 美股 | `usAAPL` |

---

## PlaceholderAPI

| 占位符 | 说明 |
| --- | --- |
| `%superstocks_cash%` | 玩家 Vault 余额 |
| `%superstocks_portfolio_value%` | 持仓市值 |
| `%superstocks_total_value%` | 余额 + 持仓市值 |
| `%superstocks_profit%` | 当前浮动盈亏 |
| `%superstocks_profit_percent%` | 当前收益率 |
| `%superstocks_shares_sh600519%` | 指定股票持仓数量 |

---

## 数据存储

插件使用 SQLite 文件存储持仓与交易记录：

```yaml
database:
  file: "stocks.db"
```

默认生成位置：

```text
plugins/SuperStocks/stocks.db
```

---

## 配置重载

`/stocks reload` 会重载：

- `config.yml`
- 当前语言文件
- 市场股票列表
- 行情源配置
- 交易税率和最低交易数量

数据库文件路径建议不要在服务器运行中频繁修改。

---

## 常见问题

### 1. 为什么某只股票无法交易？

该股票可能还没有成功同步行情。可尝试：

```text
/stocks sync
/stocks quote <代码>
```

### 2. 为什么排行榜没有数据？

排行榜需要玩家拥有持仓，并且相关股票有缓存行情。

### 3. EssentialsX 为什么没有生效？

请确认：

- 已安装 Vault。
- 已安装 EssentialsX Economy。
- Vault 能识别经济服务。

### 4. 切换语言后没有变化？

执行：

```text
/stocks reload
```

并确认 `plugins/SuperStocks/Language/<language>.yml` 存在。

---

## 开发与版本

Paper API 版本在：

```text
gradle.properties
```

示例：

```properties
paperApiVersion=1.21.4-R0.1-SNAPSHOT
```

如你的 Purpur 版本对应更高 Minecraft/Paper API，可修改此值后重新运行 GitHub Actions。

---

## 免责声明

SuperStocks 是 Minecraft 服务器内的虚拟投资娱乐插件，不构成任何现实投资建议。行情接口可能受第三方服务稳定性、访问限制、字段变化影响，请合理设置同步间隔并遵守相关服务条款。
