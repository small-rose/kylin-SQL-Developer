# 工具栏图标指南

## 图标目录

将 PNG 图标放入 `kylin-sql-ui/src/main/resources/icons/` 目录，应用启动时会自动加载。
找不到 PNG 时回退显示中文文字。

## 图标列表

| 位置 | 类 | 方法 | PNG 文件名 | 回退文字 | Tooltip |
|------|----|------|-----------|---------|---------|
| MainFrame 工具栏 | `MainFrame.java` | `tb()` | `new.png` | 新建 | 新建 SQL 文件 (Ctrl+N) |
| MainFrame 工具栏 | `MainFrame.java` | `tb()` | `open.png` | 打开 | 打开 (Ctrl+O) |
| MainFrame 工具栏 | `MainFrame.java` | `tb()` | `save.png` | 保存 | 保存 (Ctrl+S) |
| MainFrame 工具栏 | `MainFrame.java` | `tb()` | `execute.png` | 执行 | 执行 (F8) |
| MainFrame 工具栏 | `MainFrame.java` | `tb()` | `format.png` | 格式化 | 格式化 (Ctrl+Shift+F) |
| MainFrame 工具栏 | `MainFrame.java` | `tb()` | `connect.png` | 连接 | 管理连接 |
| MainFrame 工具栏 | `MainFrame.java` | `tb()` | `locate.png` | 定位 | 定位文件 |
| SourceViewerPanel | `SourceViewerPanel.java` | `flatBtn()` | `edit.png` | 编辑 | 编辑 |
| SourceViewerPanel | `SourceViewerPanel.java` | `flatBtn()` | `compile.png` | 编译 | 编译 |
| SqlEditorPanel | `SqlEditorPanel.java` | `flatBtn()` | `execute.png` | 执行 | 执行 (F8) |
| SqlEditorPanel | `SqlEditorPanel.java` | `flatBtn()` | `append.png` | 追加 | 追加执行 (F9) |
| SqlEditorPanel | `SqlEditorPanel.java` | `flatBtn()` | `history.png` | 历史 | 执行历史 |
| SqlEditorPanel | `SqlEditorPanel.java` | `flatBtn()` | `commit.png` | 提交 | 提交 (Commit) |
| SqlEditorPanel | `SqlEditorPanel.java` | `flatBtn()` | `rollback.png` | 回滚 | 回滚 (Rollback) |

## 推荐图标来源

- [Lucide](https://lucide.dev) — MIT 许可，简洁线条风格
- [allsvgicons.com/pack/lucide](https://allsvgicons.com/pack/lucide) — 在线预览并下载 PNG
- 尺寸建议：24x24px PNG