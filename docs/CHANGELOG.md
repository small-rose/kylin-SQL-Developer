# Changelog

## v1.1.0 (2026-07-27)

### ✨ 新功能
- **SQL 格式化引擎重构** — 模板驱动架构，4 方言（Oracle/OceanBase/MySQL/PostgreSQL），40+ 可配置参数
- **FontManager 字体体系** — 17 区域字体独立配置，支持系列/字号/样式/颜色，实时预览
- **SettingsDialog 全面重构** — 左侧导航树，新增字体/常用配置/元数据面板
- **Service 层** — SchemaService + DataQueryService + ExportService 抽象，4 方言实现，ServiceFactory 工厂
- **AdvancedExportDialog** — 结果集/表/自定义 SQL 三模式切换，conn→schema→table 级联
- **ExportEngine** — 统一导出引擎，支持 CSV/XLSX/JSON/XML/MD/INSERT/HTML 7 格式
- **驱动自动下载** — MissingDriverHandler，全局 URLClassLoader，HikariCP 兼容
- **导入功能** — ImportDialog 文件导入 SQL 执行
- **数据库厂商图标** — Oracle/OceanBase/MySQL/PostgreSQL 连接树图标区分

### 🎨 UI/UX
- **关于对话框** — 紫色背景移除，深色文字，logo+文字水平布局，间距优化
- **日志查看器** — 70% 屏幕尺寸，自动滚动
- **连接树图标** — 数据库厂商特定图标 + 对象类型颜色编码
- **源文件查看器** — 抗锯齿渲染，方法列表导航
- **编辑器** — 代码段框高亮，执行成功/失败圆点，行号缩放跟随

### 🚀 性能
- **启动耗时诊断** — `[DIAG]` 日志输出各阶段耗时，标签恢复逐段计时
- **MetadataCache 预热** — 后台线程预加载元数据

### 🐛 Bug 修复
- **NPE** — `setDbTypeKey()` 将 systemViews 设为 null 导致空指针
- **MySQL 大小写** — 缓存全小写 vs `upper()` 不匹配，修复 3 处 `toUpperCase()`
- **Schema 不切换** — `executeSql()` 添加 `applySchemaIfNeeded()`，按方言执行 USE/ALTER SESSION/setSchema
- **AutoComplete** — 取消 `setAutoCompleteSingleChoices` 防止意外自动插入
- **编辑器缩放** — 字体/行号同步缩放
- **底部面板分隔线** — ComponentAdapter 确保布局完成后设置，展开 35%

### 📚 文档
- 中英文 README（16 badges + 功能对比表）
- 许可证 MIT → GPL v3
- 快捷键/THEME/USER_MANUAL/FONT_CUSTOMIZATION/ICON_LIST/REFERENCE_NUMBERING 全部补全并移至 docs/

### 🔧 基础设施
- GitHub Actions CI（push/PR 自动编译测试）+ Release（tag v* 自动构建发布）
- AGENTS.md 操作规范
- .gitignore 清理

## v1.0.0 (2026-06)

初始发布：PL/SQL 数据库开发工具，支持多方言、语法高亮、自动补全、结果集浏览、SQL 格式化。
