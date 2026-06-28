# Kylin PL/SQL Developer 编号清单

> 用于所有需求描述/Bug 报告中的精确定位。
> 格式：`A区-F功-B钮-E效[-序号]`

---

## A1 主框架窗口 (MainFrame)

### 区域
| 编号 | 名称 | 位置 |
|------|------|------|
| A1 | 主窗口 | 整个 JFrame |
| A1-TB | 主工具栏 | 顶部 BorderLayout.NORTH |
| A1-MB | 菜单栏 | 工具栏上方 |
| A1-SB | 状态栏 | `statusLabel`，底部 borderLayout.SOUTH |

### 菜单 (A1-MB)
| 编号 | 菜单 | 项目 |
|------|------|------|
| M1 | 文件 | `M1-1` 新建 `M1-2` 打开 `M1-3` 保存 `M1-4` 另存为 `M1-5` 关闭标签 `M1-6` 设置 `M1-7` 退出 |
| M2 | 编辑 | `M2-1` 撤销 `M2-2` 重做 `M2-3` 查找 `M2-4` 替换 |
| M3 | SQL | `M3-1` 执行 `M3-2` 格式化 `M3-3` 执行计划 `M3-4` 调用层级 `M3-5` SQL 历史 |
| M4 | 视图 | `M4-1` 主题-Darcula `M4-2` 主题-Light `M4-3` 主题-豆沙绿 |
| M5 | 工具 | `M5-1` 导出结果集 |
| M6 | 帮助 | `M6-1` 关于 |

### 主工具栏按钮 (A1-TB)
| 编号 | 标签 | 动作 |
|------|------|------|
| B1-1 | ＋ | 新建 SQL 文件 (Ctrl+N) |
| B1-2 | 打开图标 | 打开文件 (Ctrl+O) |
| B1-3 | 保存图标 | 保存 (Ctrl+S) |
| B1-4 | ▶ | 执行 (F8) |
| B1-5 | 格式化图标 | 格式化 SQL (Ctrl+Shift+F) |
| B1-6 | 链接图标 | 管理连接 |

---

## A2 编辑器区域 (EditorPanel / Center)

### 区域
| 编号 | 名称 | 文件名 |
|------|------|--------|
| A2 | 编辑器区域 | MainFrame.java:200 `editorPanel` |
| A2-WELCOME | 欢迎面板 | WelcomePanel |
| A2-TABS | 编辑器标签组 | MainFrame.java:188 `editorTabs` (JTabbedPane) |
| A2-SPLIT | 编辑拆分面板 | MainFrame.java:1059 `editorSplit` (JSplitPane) |
| A2-SEC | 拆分副标签组 | MainFrame.java:1043 `secondaryTabs` |

### 编辑器标签页类型 (A2-TABS)
| 编号 | 类型 | 类 |
|------|------|----|
| A2-ED | SQL 编辑器 | SqlEditorPanel |
| A2-SV | 源码查看器 | SourceViewerPanel |

### 标签页内容 (A2-ED)
| 编号 | 控件/特性 | 位置 |
|------|----------|------|
| A2-ED-TB | 编辑器工具栏 | SqlEditorPanel.java:61 FlowLayout |
| A2-ED-TA | RSyntaxTextArea 编辑区 | SqlEditorPanel.java:99-106 |
| A2-ED-GT | 行号区 (Gutter) | RTextScrollPane 左侧 |
| A2-ED-CL | 代码折叠指示器 | Gutter 右侧 |

### 编辑器工具栏按钮 (A2-ED-TB)
| 编号 | 标签 | 动作 |
|------|------|------|
| B2-1 | ▶ | 执行 (F8) |
| B2-2 | 📋 图标 | 执行历史 (未绑定) |
| B2-3 | 连接下拉框 | 切换连接 |
| B2-4 | Schema 下拉框 | 切换 Schema |
| B2-5 | □ 自动提交 | 切换自动提交 |
| B2-6 | 提交 | 手动提交事务 |
| B2-7 | 回滚 | 手动回滚事务 |

### 标签页右键菜单 (A2-TABS)
| 编号 | 项目 | 动作 |
|------|------|------|
| M7-1 | 关闭 | closeTab |
| M7-2 | 关闭其他 | closeOtherTabs |
| M7-3 | 关闭全部 | closeAllTabs |
| M7-4 | 关闭未修改 | closeUnmodifiedTabs |
| M7-5 | 关闭左侧标签 | closeLeftTabs |
| M7-6 | 关闭右侧标签 | closeRightTabs |
| M7-7 | 向右拆分 | splitEditor(false) |
| M7-8 | 向下拆分 | splitEditor(true) |
| M7-9 | 开始执行 (Ctrl+Shift+F10) | executeActiveEditor |
| M7-10 | 另存为... | saveActiveFileAs |
| M7-11 | 复制文件名 | copyFileName |
| M7-12 | 复制完整路径 | copyFilePath |
| M7-13 | 文件管理器打开 | openInFileManager |
| M7-14 | 终端打开 | openInTerminal |
| M7-15 | 外部编辑器打开 | openInExternalEditor |
| M7-16 | 本地历史 > 显示历史 | showLocalHistory |
| M7-17 | 本地历史 > 对比上个版本 | diffLocalHistory |
| M7-18 | 本地历史 > 恢复 | restoreLocalHistory |
| M7-19 | 重新打开已关闭标签 | reopenClosedTab |

### 编辑器视觉特性 (A2-ED)
| 编号 | 效果 | 说明 |
|------|------|------|
| E2-1 | 代码段框 | 光标所在 SQL 段的半透明框+边框 (exec.highlight / accent.tab) |
| E2-2 | 执行成功圆点 | 行号左侧绿色圆点 |
| E2-3 | 执行失败圆点 | 行号左侧红色圆点 |
| E2-4 | 修改标记 * | 标题前的星号表示未保存 |
| E2-5 | 标签关闭按钮 × | hover 红色圆形 |

---

## A3 左侧面板 (LeftPanel)

### 区域
| 编号 | 名称 | 文件名 |
|------|------|--------|
| A3 | 左侧面板 | LeftPanel |
| A3-STRIP | 左侧垂直标签条 | LeftPanel.java:22 `tabStrip` |
| A3-CONTENT | 左侧内容区 | LeftPanel.java:34 `contentPanel` |

### 垂直按钮 (A3-STRIP)
| 编号 | 标签 | 状态 |
|------|------|------|
| B3-1 | DATABASE | 默认激活，展开时显示 ObjectBrowser |

### 对象浏览器 (A3-OB)
| 编号 | 名称/特性 | 位置 |
|------|----------|------|
| A3-OB | 对象浏览器树 | ObjectBrowser.java:21 |
| A3-OB-TB | 对象浏览器工具栏 | ObjectBrowser.java:178-196 |
| A3-OB-INFO | Schema 计数标签 | ObjectBrowser.java:226 `"shown X of Y"` |

#### 对象浏览器工具栏按钮 (A3-OB-TB)
| 编号 | 标签/图标 | 动作 |
|------|----------|------|
| B3-2 | ＋ | 新建连接 |
| B3-3 | ⚙ | 连接属性 |
| B3-4 | ⟳ | 刷新当前连接 |
| B3-5 | ▶ | 新建 SQL 编辑器 |

#### 树节点级别
| 编号 | 级别 | 内容 |
|------|------|------|
| A3-OB-L1 | Level 1 | 连接（服务器） |
| A3-OB-L2 | Level 2 | 连接实例 |
| A3-OB-L3 | Level 3 | Schema |
| A3-OB-L4 | Level 4 | 对象 (TABLE/VIEW/INDEX 等) |
| A3-OB-L5 | Level 5 | 列 |

#### 对象类型图标
| 编号 | 对象类型 | 颜色 |
|------|---------|------|
| E3-1 | 数据库 | 蓝 #4A90D9 |
| E3-2 | Schema | 绿 #5CB85C |
| E3-3 | 表 | 蓝 #337AB7 |
| E3-4 | 视图 | 青 #5BC0DE |
| E3-5 | 索引 | 黄 #F0AD4E |
| E3-6 | 序列 | 紫 #8E44AD |
| E3-7 | 函数/过程 | 红 #D9534F |
| E3-8 | 包 | 棕 #A0522D |
| E3-9 | 同义词 | 灰绿 #7B8D8E |
| E3-10 | 列 | 灰 #888888 |

#### 右键菜单 (A3-OB)
| 编号 | 触发级别 | 项目 | 动作编号 |
|------|---------|------|---------|
| M8-1 | L1 (未连接) | 连接 | - |
| M8-2 | L1 (已连接) | 断开 | - |
| M8-3 | L1 | 属性 | - |
| M8-4 | L1 | 刷新 | - |
| M8-5 | L1 | 新建 SQL 编辑器 | - |
| M8-6 | L4 (TABLE/VIEW) | 生成 SELECT | F5-1 |
| M8-7 | L4 (TABLE/VIEW) | 生成 INSERT | F5-2 |
| M8-8 | L4 (TABLE/VIEW) | 生成 UPDATE | F5-3 |
| M8-9 | L4 (TABLE/VIEW) | 生成 DELETE | F5-4 |
| M8-10 | L4 (TABLE/VIEW) | 数据预览 (前100行) | F5-5 |
| M8-11 | L4 (所有类型) | 查看 DDL | F5-6 |
| M8-12 | L4 (所有类型) | 复制表名 | - |
| M8-13 | L4 (PACKAGE) | 展开包 | - |
| M8-14 | L5 (列) | 复制列名 | - |

---

## A4 右侧面板 (RightPanel)

### 区域
| 编号 | 名称 | 文件名 |
|------|------|--------|
| A4 | 右侧面板 | RightPanel |
| A4-STRIP | 右侧垂直标签条 | RightPanel.java:40 `tabStrip` |
| A4-CONTENT | 右侧内容区 | RightPanel.java:22-23 `contentPanel` |

### 垂直按钮 (A4-STRIP)
| 编号 | 标签 | 对应卡片 |
|------|------|---------|
| B4-1 | FILES | A4-FILES: 文件列表 |
| B4-2 | THUMBNAIL | A4-THUMB: 代码缩略图 |

### 文件列表 (A4-FILES)
| 编号 | 特性 | 说明 |
|------|------|------|
| A4-FILES-LIST | 文件 JList | 最近打开文件列表 |
| A4-FILES-OPEN | 双击打开 | 导航到文件 |

### 代码缩略图 (A4-THUMB)
| 编号 | 特性 | 说明 |
|------|------|------|
| A4-THUMB-VIEW | 迷你代码渲染 | 线段表示代码行 |
| E4-1 | 光标指示线 | 绿色 `accent.green` 矩形标记当前位置 |
| A4-THUMB-CLICK | 点击导航 | 跳转到指定行 |

---

## A5 底部面板 (BottomPanel)

### 区域
| 编号 | 名称 | 文件名 |
|------|------|--------|
| A5 | 底部面板 | BottomPanel |
| A5-TAB | 底部标签条 | BottomPanel.java:45 `tabBar` |
| A5-CONTENT | 底部内容区 | CardLayout |

### 底部标签按钮 (A5-TAB)
| 编号 | 标签 | 内容 |
|------|------|------|
| B5-1 | TODO | A5-TODO: 待办文本区 |
| B5-2 | Services | A5-SRV: 连接树 + 结果面板 |

### Services 内容 (A5-SRV)
| 编号 | 名称 | 说明 |
|------|------|------|
| A5-CONN | 连接树 | JTree 显示连接及关联标签 |
| A5-RSLT | 结果面板 | ResultPanel (A6) |

### 连接树 (A5-CONN)
| 编号 | 特性 | 说明 |
|------|------|------|
| A5-CONN-TREE | 连接树 | 每个连接下显示打开标签 |
| A5-CONN-STT | 连接统计 | 根节点标题 "数据库连接 (N)" |

### TODO 区域 (A5-TODO)
| 编号 | 特性 | 说明 |
|------|------|------|
| A5-TODO-AREA | 待办文本区 | JTextArea, Monospaced 13px |

---

## A6 结果面板 (ResultPanel)

### 区域
| 编号 | 名称 | 文件名 |
|------|------|--------|
| A6 | 结果面板 | ResultPanel |
| A6-TABS | 结果标签组 | ResultPanel.java:23 `resultTabs` |
| A6-MSG | 消息标签 | 默认第一标签 "消息" |

### 结果标签特性
| 编号 | 特性 | 说明 |
|------|------|------|
| A6-TAB-R | 结果标签 | 查询结果，标签名为表名/序号 |
| E6-1 | 图标 T | 表结果，蓝色 #337AB7 |
| E6-2 | 图标 R | 普通结果，蓝色 #337AB7 |

### 结果标签工具栏 (per-tab)
| 编号 | 标签 | 功能 |
|------|------|------|
| B6-1 | ◀ | 上一页 |
| B6-2 | 行数下拉框 | 25/50/100/500/全部 |
| B6-3 | ▶ | 下一页 |
| B6-4 | ⟳ | 刷新结果集 |
| B6-5 | ■ | 停止查询 |
| B6-6 | 📌 | 固定标签 (固定后绿色) |
| B6-7 | 页信息 | `"from - to / 共 total 行"` |

### 结果操作
| 编号 | 操作 | 触发 |
|------|------|------|
| F6-1 | 表头双击复制列名 | 双击列标题 |
| F6-2 | Toast 提示"已复制: xxx" | 复制成功后自动 |
| F6-3 | 排序 | 单击列标题 |
| F6-4 | 分页 | 翻页/改页大小 |

### 消息区 (A6-MSG)
| 编号 | 特性 | 说明 |
|------|------|------|
| A6-MSG-AREA | 消息文本区 | 只读，Monospaced 12px |
| A6-MSG-TYPE | 执行日志 | SQL 执行开始/结果/耗时 |
| A6-MSG-ERR | 错误消息 | SQL 执行失败输出 |

---

## A7 设置对话框 (SettingsDialog)

### 区域
| 编号 | 名称 | 说明 |
|------|------|------|
| D7 | 设置对话框 | Modal, 780x560 |
| D7-TREE | 左侧导航树 | 行高24 |
| D7-RIGHT | 右侧卡片面板 | CardLayout |
| D7-BOTTOM | 底部按钮栏 | 应用/保存/取消 |

### 导航树节点 (D7-TREE)
| 编号 | 节点 | 对应卡片 |
|------|------|---------|
| D7-N1 | 主题 | D7-P1: 主题面板 |
| D7-N2 | 个性化 | 展开子节点 |
| D7-N2-1~9 | 背景/前景/选中/边框/强调色/编辑器/列表/滚动条/执行结果 | D7-P2: 颜色组面板 |
| D7-N3 | SQL 格式化 | D7-P3: 格式化面板 |

### 底部按钮 (D7-BOTTOM)
| 编号 | 标签 | 动作 |
|------|------|------|
| B7-1 | 应用 | `saveToConfig()` |
| B7-2 | 保存 | `saveToConfig(); dispose()` |
| B7-3 | 取消 | `restoreOriginal(); dispose()` |

### 主题配置 (D7-P1)
| 编号 | 控件 | 值 |
|------|------|-----|
| D7-P1-C1 | 主题下拉框 | Darcula / Light / 豆沙绿 |

### 颜色组 (D7-P2)
| 编号 | 组 | 颜色键 |
|------|-----|--------|
| D7-P2-G1 | 背景 | `bg.main bg.editor bg.panel bg.toolbar bg.output` |
| D7-P2-G2 | 前景 | `fg.main fg.secondary fg.muted fg.title fg.tab.active fg.tab.inactive` |
| D7-P2-G3 | 选中 | `selection.bg selection.fg selection.listBg selection.listFg` |
| D7-P2-G4 | 边框 | `border.default border.light` |
| D7-P2-G5 | 强调色 | `accent.green accent.tab` |
| D7-P2-G6 | 编辑器 | `editor.caret` |
| D7-P2-G7 | 列表 | `list.bg list.fg` |
| D7-P2-G8 | 滚动条 | `scroll.bg` |
| D7-P2-G9 | 执行结果 | `exec.success exec.fail exec.highlight` |

每个颜色项操作：D7-P2-{键}-modify (修改) / D7-P2-{键}-reset (重置)

### SQL 格式化配置 (D7-P3)
| 编号 | 标签页 | 控件 |
|------|--------|------|
| D7-P3-T1 | 通用 | 关键字大小写 / 缩进空格 / 最大行宽 / 换行符 |
| D7-P3-T2 | DML | SELECT 列模式 / FROM换行 / JOIN ON换行对齐 / WHERE位置 / 逗号位置 / INSERT紧凑 / UPDATE对齐 |
| D7-P3-T3 | PL/SQL | THEN换行 / LOOP换行 / ELSE换行 / EXCEPTION对齐 / 声明区对齐 / 参数列表 / 括号间距 |
| D7-P3-T4 | DDL | 列定义对齐 / 存储子句格式 |

---

## A8 连接对话框 (ConnectionDialog)

| 编号 | 控件 | 说明 |
|------|------|------|
| D8 | 连接对话框 | Modal, 700x500 |
| D8-LIST | 连接列表 | 左侧 JList |
| D8-NEW | ＋ 新建连接 | 添加连接 |
| D8-DEL | 删除连接 | 删除选中 |
| D8-FORM | 连接表单 | 右侧 GridBagLayout |
| D8-F1 | 连接名称 | JTextField |
| D8-F2 | 数据库类型 | oceanbase / postgresql / oracle |
| D8-F3 | 使用 JDBC URL | 复选框切换 |
| D8-F4 | JDBC URL | 文本字段 |
| D8-F5 | 主机 | 默认 127.0.0.1 |
| D8-F6 | 端口 | 默认 2881 |
| D8-F7 | 服务名/数据库 | 默认 oceanbase |
| D8-F8 | 用户名 | - |
| D8-F9 | 密码 | JPasswordField |
| D8-F10 | Schema | - |
| D8-F11 | 查询超时 | 默认 0(不限) |
| B8-1 | 测试连接 | testConnection |
| B8-2 | 保存 | saveConnection |
| B8-3 | 关闭 | dispose |

---

## A9 快捷键总表

| 编号 | 快捷键 | 动作 |
|------|--------|------|
| K1 | Ctrl+N | 新建 SQL 文件 |
| K2 | Ctrl+O | 打开 SQL 文件 |
| K3 | Ctrl+S | 保存 |
| K4 | Ctrl+Shift+S | 另存为 |
| K5 | Ctrl+W | 关闭当前标签 |
| K6 | Ctrl+Alt+S | 设置 |
| K7 | Ctrl+Z | 撤销 |
| K8 | Ctrl+Y | 重做 |
| K9 | Ctrl+F | 查找 |
| K10 | Ctrl+H | 替换 |
| K11 | F8 | 执行 SQL |
| K12 | Ctrl+Shift+F | 格式化 SQL |
| K13 | Ctrl+E | 执行计划 |
| K14 | Ctrl+Alt+H | 调用层级 |
| K15 | Ctrl+Shift+H | SQL 历史记录 |
| K16 | Ctrl+Shift+F10 | 执行选中编辑器 |

---

## A10 全局视觉效果

| 编号 | 效果 | 位置 |
|------|------|------|
| E10-1 | 垂直标签激活指示条 | 左侧3px绿色条 accent.green |
| E10-2 | 垂直标签激活背景 | selection.listBg |
| E10-3 | 底部标签激活上边线 | 2px accent.tab 强调线 |
| E10-4 | 底部标签文字色 | fg.tab.active / fg.tab.inactive |
| E10-5 | Toast 通知 | 右下角浮动 1.5s 自动消失 |
| E10-6 | 状态栏文字 | 底部 "就绪" / 执行状态 |

---

## 使用示例

```
Bug: 选中多 SQL ▶ 执行后，E2-2/E2-3 圆点只出现在第一条语句的行号旁
→ 应每个 SQL 语句都有独立的 E2-2/E2-3

需求：D7-P2-G9 exec.highlight 颜色不生效于 E2-1 代码段框

Bug: 点击 A2-ED-TB B2-1 ▶ 执行后，E2-2/E2-3 需要点一下 A2-ED-TA 才显示
→ 应执行后立即显示

需求：双击 A6-TABS 列标题触发 F6-1 后，E10-5 Toast 提示
