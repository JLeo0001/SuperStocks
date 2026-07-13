# SuperStocks

SuperStocks 是一个面向 Paper/Purpur 服务端的 Minecraft 虚拟投资插件，使用腾讯财经免 key 行情源，支持 Vault 经济、EssentialsX Economy、PlaceholderAPI 和箱子 GUI。

## 运行依赖

- Java 21
- Paper/Purpur 兼容服务端
- Vault
- EssentialsX Economy 或其他 Vault 经济插件
- PlaceholderAPI 可选

## 构建方式

你不需要本机构建环境。把本项目上传到 GitHub 后，进入仓库的 Actions 页面，运行 `Build SuperStocks` workflow。构建完成后，在 artifact 中下载 `SuperStocks`，里面包含可放入服务器 `plugins` 文件夹的 jar。

## 命令

- `/stocks` 打开股市 GUI
- `/stocks sync` 管理员手动同步行情
- `/stocks reload` 管理员重载配置、语言和行情源参数

## 权限

- `superstocks.use` 使用股市，默认所有玩家
- `superstocks.admin` 管理命令，默认 OP

## 多语言

配置项 `language` 决定当前语言文件：

```yaml
language: zh_CN
```

服务器首次启动后会生成：

```text
plugins/SuperStocks/Language/zh_CN.yml
```

你可以在 `Language` 文件夹内新增语言文件，例如 `en_US.yml`，然后把 `config.yml` 的 `language` 改为 `en_US`。GUI 标题、按钮、lore、命令提示、交易提示都可以在语言文件中修改。

## GUI 美化配置

GUI 的尺寸、边框填充、图标材质、统计按钮、快捷交易数量都在 `config.yml` 中配置：

```yaml
gui:
  fill-empty-slots: true
  filler-material: GRAY_STAINED_GLASS_PANE
  stats-material: KNOWLEDGE_BOOK
  portfolio-material: CHEST
  quote-up-material: LIME_CONCRETE
  quote-down-material: RED_CONCRETE
  trade-amounts:
    - 1
    - 10
    - 100
```

市场入口图标可单独配置：

```yaml
markets:
  cn:
    display-name: "A股"
    icon: EMERALD
```

主界面和市场界面会显示股票数量统计：配置股票数、已成功调取行情数、数据源、刷新间隔和最后同步时间。

## 行情源配置

默认数据源仍是腾讯财经：

```yaml
stock-provider:
  type: tencent
  display-name: "腾讯财经"
  refresh-seconds: 300
  timeout-seconds: 8
  tencent:
    endpoint: "https://qt.gtimg.cn/q={symbols}"
    encoding: "GBK"
    symbol-separator: ","
    max-symbols-per-request: 80
    user-agent: "SuperStocks/1.0"
```

如果你有腾讯财经兼容格式的代理接口，可以修改 `endpoint`。接口返回格式需要保持腾讯 `v_symbol="...~..."` 结构，插件才能解析。

腾讯财经不是正式开放 API，字段格式未来可能变化。插件会缓存上次成功行情，单次同步失败时 GUI 继续显示旧数据。

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
