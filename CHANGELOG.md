# Changelog

## 1.0.1

### Fixed
- 所有硬编码中文已迁移到语言文件，13 种语言完整覆盖
- BossBar 异步线程调用安全问题
- 凭证右键不区分动作、硬编码文本
- `ConcurrentHashMap` 迭代中修改导致的并发异常
- AdminGui 分页参数未使用
- LocalMarketService 静默异常吞没
- TencentStockProvider `Charset.forName` 可能崩溃
- 股息存款异步与记录同步不一致
- 做空开仓未记入卖出所得
- 命令输出颜色码未转换
- PlaceholderAPI 扩展残留硬编码中文日志，未知 placeholder 返回 convention 修正为 `null`

### Changed
- 配置校验器完全重写，注入 LanguageManager，所有提示走语言键
- 市场报告、GUI 物品名、列表格式统一使用语言键
- 12 个非 en_US 语言文件键名与 en_US 基准零缺失同步
