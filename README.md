# SuperStocks

SuperStocks 是一个面向 Paper/Purpur 服务端的 Minecraft 虚拟投资插件，使用免 key 行情源配置，支持 Vault 经济、EssentialsX Economy、PlaceholderAPI、箱子 GUI、持仓排行和多语言。

## 运行依赖

- Java 21
- Paper/Purpur 兼容服务端
- Vault
- EssentialsX Economy 或其他 Vault 经济插件
- PlaceholderAPI 可选

## 构建方式

把本项目上传到 GitHub 后，进入仓库的 Actions 页面，运行 `Build SuperStocks` workflow。构建完成后，在 artifact 中下载 `SuperStocks`，里面包含可放入服务器 `plugins` 文件夹的 jar。

## 命令

- `/stocks` 打开股市 GUI
- `/stocks sync` 管理员手动同步行情
- `/stocks reload` 管理员重载配置、语言和行情源参数

## 权限

- `superstocks.use` 使用股市，默认所有玩家
- `superstocks.admin` 管理命令，默认 OP

## 多语言

`config.yml` 中的 `language` 决定当前语言文件：

```yaml
language: zh_CN
```

服务器首次启动后会生成：

```text
plugins/SuperStocks/Language/zh_CN.yml
```

GUI 标题、按钮、lore、命令提示、交易提示、排行榜文本都可以在语言文件中修改。

## GUI

大厅已改为 45 格，市场入口、股神榜、持仓、牛马榜使用内部槽位对称排列，列表页只会把股票和持仓放在非边框槽位，避免物品挤到玻璃边框上。

关键配置：

```yaml
gui:
  main-size: 45
  fill-empty-slots: true
  filler-material: GRAY_STAINED_GLASS_PANE
  ranking-limit: 10
  layout:
    main-stats-slot: 13
    main-market-slots: [20, 22, 24]
    main-winners-slot: 29
    main-portfolio-slot: 31
    main-losers-slot: 33
```

## 排行榜

- 股神榜：按当前持仓浮动盈亏从高到低排序。
- 牛马榜：按当前持仓浮动盈亏从低到高排序。

排行榜使用当前缓存行情计算，未成功调取行情的股票不会计入排名。

## 行情源配置

默认启用腾讯财经。`active` 只能选择一个数据源：

```yaml
stock-provider:
  active: tencent
```

已提供配置项：

- `tencent` 腾讯财经，默认可用
- `eastmoney` 东方财富，预留配置
- `ths` 同花顺，预留配置
- `xueqiu` 雪球，预留配置
- `sina` 新浪财经，预留配置

每个 source 都支持：

```yaml
endpoint: "https://qt.gtimg.cn/q={symbols}"
encoding: "GBK"
symbol-separator: ","
max-symbols-per-request: 80
user-agent: "SuperStocks/1.0"
```

当前内置解析器按腾讯 `v_symbol="...~..."` 格式解析。如果切到东方财富、同花顺、雪球或新浪，建议先接入一个返回腾讯兼容格式的代理接口，或后续为对应源补专用解析器。

## 默认股票

默认每个市场内置 20 个股票代码：

- A 股 20 个
- 港股 20 个
- 美股 20 个

可在 `config.yml` 的 `markets.*.symbols` 中自由增删。

## PlaceholderAPI

- `%superstocks_cash%`
- `%superstocks_portfolio_value%`
- `%superstocks_total_value%`
- `%superstocks_profit%`
- `%superstocks_profit_percent%`
- `%superstocks_shares_sh600519%`

## 版本说明

构建依赖版本在 `gradle.properties`：

```properties
paperApiVersion=1.21.4-R0.1-SNAPSHOT
```

如果你的 Purpur 26.1 对应了更高的 Minecraft/Paper API 版本，只需要修改这个值后重新运行 GitHub Actions。
