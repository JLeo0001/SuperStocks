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
- `/stocks reload` 管理员重载配置

## 权限

- `superstocks.use` 使用股市，默认所有玩家
- `superstocks.admin` 管理命令，默认 OP

## 配置

默认配置在 `src/main/resources/config.yml`。服务器首次启动后会生成到：

```text
plugins/SuperStocks/config.yml
```

市场配置示例：

```yaml
markets:
  cn:
    display-name: "A股"
    symbols:
      - "sh600519"
      - "sz000001"
  hk:
    display-name: "港股"
    symbols:
      - "hk00700"
  us:
    display-name: "美股"
    symbols:
      - "usAAPL"
```

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
