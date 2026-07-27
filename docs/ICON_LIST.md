# Kylin SQL Developer — 图标使用清单

所有图标文件位于 `kylin-sql-ui/src/main/resources/icons/` 目录。

---

## 品牌图标 `icons/database/`

| 文件 | 格式 | 渲染方式 | 使用位置 |
|------|------|---------|---------|
| `database.svg` | SVG | 图标库 | 通用数据库图标 |
| `oracle.svg` | SVG | 原始颜色 | 对象浏览器 Oracle 连接 |
| `mysql.svg` / `mysql.png` | SVG + PNG 回退 | 原始颜色 | 对象浏览器 MySQL 连接 |
| `mariadb.svg` | SVG | 原始颜色 | 对象浏览器 MariaDB 连接 |
| `oceanbase.svg` | SVG | 原始颜色 | 对象浏览器 OceanBase 连接 |
| `postgresql.svg` | SVG | 图标库 | 对象浏览器 PostgreSQL 连接 |
| `sqlite.svg` / `sqlite.png` | SVG + PNG 回退 | 图标库 | 对象浏览器 SQLite 连接 |
| `microsoftsqlserver.svg` | SVG | 图标库 | 对象浏览器 SQL Server 连接 |

> 渲染方式说明：**原始颜色** = 不经过染色管道，显示 SVG 原始颜色；**图标库** = 经过 IconUtil 染色（替换 `currentColor` + 遮罩）。

---

## 工具栏按钮

| 按钮 | SVG | 颜色 |
|------|-----|------|
| 新建 SQL 文件 | `new.svg` | 绿 `#2E7D32` |
| 打开 | `open.svg` | 蓝 `#1565C0` |
| 保存 | `save.svg` | 琥珀 `#F57F17` |
| 执行 | `execute.svg` | 绿 `#2E7D32` |
| 追加执行 | `append.svg` | 绿 `#2E7D32` |
| 格式化引擎选择 | 下拉框 | - |
| 格式化 | `format.svg` | 紫 `#6A1B9A` |
| 管理连接 | `connect.svg` | 青 `#00695C` |
| 定位文件 | `locate.svg` | 蓝 `#1565C0` |

---

## 主菜单栏

### 文件菜单

| 菜单项 | SVG |
|--------|-----|
| 新建 | `new.svg` |
| 打开 | `open.svg` |
| 保存 | `save.svg` |
| 另存为 | `save-plus.svg` |
| 关闭 | `x.svg` |
| 设置 | `settings.svg` |

### 编辑菜单

| 菜单项 | SVG |
|--------|-----|
| 撤销 | `undo-2.svg` |
| 重做 | `redo-2.svg` |
| 查找 | `search.svg` |
| 替换 | `search.svg` |
| 全局搜索 | `file-search.svg` |

### SQL 菜单

| 菜单项 | SVG |
|--------|-----|
| 执行 | `execute.svg` |
| 追加执行 | `append.svg` |
| 格式化 | `format.svg` |
| 执行计划 | `skip-forward.svg` |
| 调用层级 | 无图标 |
| SQL 历史 | `history.svg` |

### 工具菜单

| 菜单项 | SVG |
|--------|-----|
| SQL 工具 | `toolbox.svg` |
| SQL 格式化 | `format.svg` |
| 数据生成器 | `database-search.svg` |
| SQL 历史 | `history.svg` |
| 文本比较 | `compare.svg` |
| 正则测试 | `regex.svg` |
| 对象搜索 | `database-search.svg` |
| 高级导出 | `fire-extinguisher.svg` |
| 导入 | `file-play.svg` |

### 帮助菜单

| 菜单项 | SVG |
|--------|-----|
| 应用日志 | `info.svg` |
| 关于 | `help.svg` |

---

## 编辑器工具栏 (SqlEditorPanel)

| 按钮 | SVG |
|------|-----|
| 执行 (F8) | `execute.svg` |
| 追加执行 (F9) | `append.svg` |
| 执行历史 | `history.svg` |
| 提交 (Commit) | `commit.svg` |
| 回滚 (Rollback) | `rollback.svg` |
| 搜索/替换 | `search.svg` |

---

## 标签页右键菜单

| 菜单项 | SVG |
|--------|-----|
| 关闭 | `x.svg` |
| 关闭其他 | `x.svg` |
| 关闭全部 | `x.svg` |
| 关闭未修改 | `x.svg` |
| 关闭左侧标签 | `x.svg` |
| 关闭右侧标签 | `x.svg` |
| (取消)固定标签 | `pin.svg` / `pin-off.svg` |
| 向右拆分 | `split-vertical.svg` |
| 向下拆分 | `split-vertical.svg` |
| 执行 | `execute.svg` |
| 另存为 | `save-plus.svg` |
| 复制文件名 | `copy.svg` |
| 复制完整路径 | `copy.svg` |
| 重新打开已关闭标签 | `refresh-ccw.svg` |

---

## 源码查看器 (SourceViewerPanel)

| 按钮 | SVG |
|------|-----|
| 编辑 | `edit.svg` |
| 保存 | `save-plus.svg` |
| 编译 | `compile.svg` |

---

## 对象浏览器右键菜单 (ObjectBrowser)

| 菜单项 | SVG |
|--------|-----|
| 连接 | `connect.svg` |
| 断开 | `connect.svg` |
| 属性 | `info.svg` |
| 刷新 | `refresh.svg` |
| 新建 SQL 编辑器 | `new.svg` |
| 数据预览 (前100行) | `search.svg` |
| 查看 DDL | `database-search.svg` |
| 生成 SELECT | `search.svg` |
| 生成 INSERT | `save-plus.svg` |
| 生成 UPDATE | `edit.svg` |
| 生成 DELETE | `trash.svg` |
| 复制名称 | `copy.svg` |
| 展开包 (过程/函数) | `skip-forward.svg` |

---

## 结果面板右键菜单 (ResultPanel / BottomPanel)

| 菜单项 | SVG |
|--------|-----|
| 关闭 / 关闭其他 / 关闭全部 | `x.svg` |
| 关闭左侧 / 关闭右侧 | `x.svg` |
| (取消)固定标签 | `pin.svg` / `pin-off.svg` |
| 刷新结果 | `refresh.svg` |
| 展开全部 | `skip-forward.svg` |
| 新建 SQL 查询 | `new.svg` |
| 保存 | `save.svg` |
| 打开 | `open.svg` |
| 打开到新标签 | `open.svg` |
| 删除记录 | `trash.svg` |
| 重新打开已关闭标签 | `refresh-ccw.svg` |

---

## 右侧面板 (RightPanel / LocalFileBrowser)

| 菜单项 | SVG |
|--------|-----|
| 在标签页中打开 | `open.svg` |
| 打开文件所在位置 | `locate.svg` |
| 删除文件 | `trash.svg` |
| 永久删除 | `trash-2.svg` |
| 刷新 | `refresh.svg` |
| 移除根目录 | `x.svg` |
| 复制路径 | `copy.svg` |
| 刷新全部 | `refresh.svg` |
| 展开全部 | `skip-forward.svg` |
| 打开文件夹 | `open.svg` |

---

## 编辑器右键菜单

| 菜单项 | SVG |
|--------|-----|
| 格式化 | `format.svg` |

---

## 其他图标

| SVG | 使用位置 |
|-----|---------|
| `arrow-left.svg` | 搜索结果导航 |
| `arrow-right.svg` | 搜索结果导航 |
| `arrow-up-to-line.svg` | 文本比较跳转 |
| `arrow-down-to-line.svg` | 文本比较跳转 |
| `arrow-left-to-line.svg` | 文本比较跳转 |
| `arrow-right-to-line.svg` | 文本比较跳转 |
| `arrow-big-up.svg` | 连接管理对话框 |
| `chevron-down.svg` | 下拉箭头 |
| `plus.svg` | 添加按钮 |
| `minus.svg` | 移除按钮 |
| `info.svg` | 属性、信息提示 |
| `key.svg` | 密码字段标识 |
| `case-sensitive.svg` | 搜索大小写切换 |
| `stop.svg` | 停止操作 |
| `loader.svg` | 加载动画（spinner） |
| `folder-open-dot.svg` | 文件夹图标 |
| `retry.svg` | 重试按钮 |
| `square-split-vertical.svg` | 垂直拆分图标 |
| `square-split-horizontal.svg` | 水平拆分图标 |
| `square-function.svg` | 函数图标 |
| `square-pause.svg` | 暂停图标 |
| `debug-play.svg` | 调试运行 |
| `debug-pause.svg` | 调试暂停 |
| `search-alert.svg` | 搜索警告替换图标 |
| `package.svg` | 包节点图标 |
| `squirrel.svg` | 动物图案装饰 |
| `faces/smile.svg` | 微笑表情（工具栏/启动） |

---

## 未使用 / 装饰性图标

| SVG | 说明 |
|-----|------|
| `save-check.svg` | 暂未引用 |
| `star.svg` | 暂未引用 |
| `wrench.svg` | 暂未引用 |
| `x-circle.svg` | 暂未引用 |
| `x-square.svg` | 暂未引用 |
