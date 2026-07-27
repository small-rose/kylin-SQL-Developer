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
| M5 | 工具 | `M5-1` 导出结果集 `M5-2` 高级导出 `M5-3` 导入文件 `M5-4` 数据生成 `M5-5` SQL 历史 `M5-6` 正则测试 `M5-7` 对象搜索 |
| M6 | 帮助 | `M6-1` 关于 `M6-2` 日志查看 |

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
| A2 | 编辑器区域 | MainFrame.java `editorPanel` |
| A2-WELCOME | 欢迎面板 | WelcomePanel |
| A2-TABS | 编辑器标签组 | MainFrame.java `editorTabs` (JTabbedPane) |
| A2-SPLIT | 编辑拆分面板 | MainFrame.java `editorSplit` (JSplitPane) |
| A2-SEC | 拆分副标签组 | MainFrame.java `secondaryTabs` |
| A2-SRCH | 查找替换栏 | SearchReplacePanel |

### 编辑器标签页类型 (A2-TABS)
| 编号 | 类型 | 类 |
|------|------|----|
| A2-ED | SQL 编辑器 | SqlEditorPanel |
| A2-SV | 源码查看器 | SourceViewerPanel |

### 标签页内容 (A2-ED)
| 编号 | 控件/特性 | 位置 |
|------|----------|------|
| A2-ED-TB | 编辑器工具栏 | SqlEditorPanel.java FlowLayout |
| A2-ED-TA | RSyntaxTextArea 编辑区 | SqlEditorPanel.java |
| A2-ED-GT | 行号区 (Gutter) | RTextScrollPane 左侧 |
| A2-ED-CL | 代码折叠指示器 | Gutter 右侧 |
| A2-ED-AC | 自动补全弹出 | PlSqlCompletionProvider + AutoCompletion |

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
| A3-STRIP | 左侧垂直标签条 | LeftPanel.java `tabStrip` |
| A3-CONTENT | 左侧内容区 | LeftPanel.java `contentPanel` |

### 垂直按钮 (A3-STRIP)
| 编号 | 标签 | 内容面板 |
|------|------|---------|
| B3-1 | DATABASE | A3-OB: ObjectBrowser |
| B3-2 | FILES | A3-FB: LocalFileBrowser |

### 对象浏览器 (A3-OB)
| 编号 | 名称/特性 | 位置 |
|------|----------|------|
| A3-OB | 对象浏览器树 | ObjectBrowser.java |
| A3-OB-TB | 对象浏览器工具栏 | ObjectBrowser.java |
| A3-OB-INFO | Schema 计数标签 | ObjectBrowser.java `"shown X of Y"` |

#### 对象浏览器工具栏按钮 (A3-OB-TB)
| 编号 | 标签/图标 | 动作 |
|------|----------|------|
| B3-3 | ＋ | 新建连接 |
| B3-4 | ⚙ | 连接属性 |
| B3-5 | ⟳ | 刷新当前连接 |
| B3-6 | ▶ | 新建 SQL 编辑器 |

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

### 本地文件浏览器 (A3-FB)
| 编号 | 特性 | 说明 |
|------|------|------|
| A3-FB-TREE | 文件树 | 本地文件目录树 |
| A3-FB-TB | 文件浏览器工具栏 | 刷新/根目录切换 |

---

## A4 右侧面板 (RightPanel)

### 区域
| 编号 | 名称 | 文件名 |
|------|------|--------|
| A4 | 右侧面板 | RightPanel |
| A4-STRIP | 右侧垂直标签条 | RightPanel.java `tabStrip` |
| A4-CONTENT | 右侧内容区 | RightPanel.java `contentPanel` |

### 垂直按钮 (A4-STRIP)
| 编号 | 标签 | 对应卡片 |
|------|------|---------|
| B4-1 | FILES | A4-FILES: 文件列表 |
| B4-2 | THUMBNAIL | A4-THUMB: 代码缩略图 |
| B4-3 | OUTLINE | A4-OL: 文档大纲 |

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

### 文档大纲 (A4-OL)
| 编号 | 特性 | 说明 |
|------|------|------|
| A4-OL-TREE | 大纲树 | SQL 对象/关键字结构化树 |
| A4-OL-NAV | 点击导航 | 跳转到对应编辑位置 |

---

## A5 底部面板 (BottomPanel)

### 区域
| 编号 | 名称 | 文件名 |
|------|------|--------|
| A5 | 底部面板 | BottomPanel |
| A5-TAB | 底部标签条 | BottomPanel.java `tabBar` |
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
| A6-TABS | 结果标签组 | ResultPanel.java `resultTabs` |
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
| D7-N3 | 字体 | D7-P3: 字体面板 (17 子项) |
| D7-N4 | SQL 格式化 | D7-P4: 格式化面板 |
| D7-N5 | 常用配置 | D7-P5: 通用配置面板 |
| D7-N6 | 元数据 | D7-P6: 元数据面板 |

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

### 字体面板 (D7-P3)
| 编号 | 控件 | 说明 |
|------|------|------|
| D7-P3-TREE | 字体键树 | 17 个字体区域节点 |
| D7-P3-FAMILY | 字体系列 ComboBox | 系统字体列表，可编辑 |
| D7-P3-SIZE | 字号 Spinner | 6-72 |
| D7-P3-STYLE | 样式 ComboBox | PLAIN/BOLD/ITALIC/BOLDITALIC |
| D7-P3-COLOR | 颜色色块 | 点击弹出 JColorChooser |
| D7-P3-PREVIEW | 预览区域 | 上下文感知预览 |
| D7-P3-RESET | 重置为默认 | 恢复出厂默认值 |

### SQL 格式化配置 (D7-P4)
| 编号 | 标签页 | 控件 |
|------|--------|------|
| D7-P4-T1 | 通用 | 关键字大小写 / 缩进空格 / 最大行宽 / 换行符 |
| D7-P4-T2 | DML | SELECT 列模式 / FROM换行 / JOIN ON换行对齐 / WHERE位置 / 逗号位置 / INSERT紧凑 / UPDATE对齐 |
| D7-P4-T3 | PL/SQL | THEN换行 / LOOP换行 / ELSE换行 / EXCEPTION对齐 / 声明区对齐 / 参数列表 / 括号间距 |
| D7-P4-T4 | DDL | 列定义对齐 / 存储子句格式 |

### 通用配置 (D7-P5)
| 编号 | 控件 | 说明 |
|------|------|------|
| D7-P5-DELAY | 自动补全延迟 | 毫秒输入框 |
| D7-P5-AUTO-INTERVAL | 自动保存间隔 | Spinner |
| D7-P5-AUTO-UNIT | 自动保存单位 | 秒/分钟/小时 |
| D7-P5-AUTO-PATH | 自动保存路径 | 文本字段 |
| D7-P5-SPLASH-MIN | 启动画面最短 | 毫秒 |
| D7-P5-SPLASH-MAX | 启动画面最长 | 毫秒 |
| D7-P5-BATCH-ORA | Oracle 批大小 | Spinner |
| D7-P5-BATCH-OB | OceanBase 批大小 | Spinner |
| D7-P5-BATCH-MYSQL | MySQL 批大小 | Spinner |
| D7-P5-BATCH-PG | PostgreSQL 批大小 | Spinner |

### 元数据面板 (D7-P6)
| 编号 | 控件 | 说明 |
|------|------|------|
| D7-P6-PREWARM | 预热按钮 | 预热元数据缓存 |

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
| D8-F2 | 数据库类型 | oceanbase / postgresql / oracle / mysql |
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
| K17 | Ctrl+Shift+O | 打开最近文件 |
| K18 | Alt+← | 导航后退 |
| K19 | Alt+→ | 导航前进 |

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
| E10-7 | 启动画面 | 应用启动时 SplashScreen |
| E10-8 | 启动诊断日志 | `[DIAG]` 前缀，输出各阶段耗时 |

---

## A11 关于对话框 (AboutDialog)

| 编号 | 控件 | 说明 |
|------|------|------|
| D11 | 关于对话框 | Modal, 居中显示 |
| D11-LOGO | 应用图标 | 左侧 logo 图 |
| D11-TITLE | 标题标签 | "Kylin PL/SQL Developer" |
| D11-VERSION | 版本标签 | 构建号和日期 |
| D11-INFO | 信息行 | 系统属性 + vCPU + 内存 |
| D11-TECH | 技术说明 | Java / 数据库驱动信息 |
| D11-COPYRIGHT | 版权标签 | GPL v3 声明 |
| D11-DETAILS | 详细面板 | 滚动显示技术细节 |

---

## A12 字体自定义 (FontManager)

### 核心类
| 编号 | 类 | 位置 |
|------|------|------|
| A12 | FontManager | kylin-sql-core: FontManager.java |
| A12-KEYS | 17 字体键 | `font.default`, `font.top`, `font.left`, `font.left.title`, `font.editor`, `font.editor.comment`, `font.editor.lineNum`, `font.right`, `font.right.title`, `font.bottom`, `font.bottom.title`, `font.bottom.result`, `font.bottom.message`, `font.bottom.result.header`, `font.status`, `font.dialog`, `font.dialog.title` |
| A12-UI | 设置面板 | SettingsDialog 字体面板 (D7-P3) |
| A12-PERSIST | 持久化 | ConfigManager.WorkspaceState.fontOverrides |

### 值格式
| 编号 | 格式 | 示例 |
|------|------|------|
| F12-1 | `FontName,Size` | `Consolas,14` |
| F12-2 | `FontName,Size,Style` | `Consolas,14,BOLD` |
| F12-3 | `FontName,Size,Style,#RRGGBB` | `Consolas,14,BOLD,#FF0000` |
| F12-4 | `FontName,Size,#RRGGBB` | `Consolas,14,#FF0000` |

### 级联回退
| F12-5 | 用户覆盖 → DEFAULTS → font.default → Dialog,PLAIN,12 |

---

## A13 SQL 格式化引擎

| 编号 | 组件 | 类/包 |
|------|------|--------|
| A13-FMT | 格式化入口 | SqlFormatter |
| A13-OPT | 40+ 格式化参数 | FormatOptions |
| A13-TPL | 模板系统 | FormatTemplate + TemplateRegistry |
| A13-TYPE | SQL 类型分类 | SqlType + SqlTypeClassifier |
| A13-SUB | 子查询展开 | SubqueryHandler (INLINE/EXPAND/AUTO) |
| A13-DL | 方言接口 | SqlDialect |
| A13-DL-ORA | Oracle 方言 | OracleDialect |
| A13-DL-OB | OceanBase 方言 | OceanBaseDialect |
| A13-DL-MYSQL | MySQL 方言 | MySqlDialect |
| A13-DL-PG | PostgreSQL 方言 | PostgreSqlDialect |
| A13-CONST | 约束引擎 | StructuralFrame / AlignmentCover / FinalLayout |
| A13-PHASE | 四阶段编排 | StructuralResolver → LineWidthResolver → AlignmentResolver → LayoutMerger |

### 格式化配置 (D7-P4 子项)
| 编号 | 标签页 | 说明 |
|------|--------|------|
| D7-P4-T1 | 通用 | keywordCase / indentSize / maxLineWidth / lineEnding |
| D7-P4-T2 | DML | selectColumnMode / whereAndPosition / commaPosition / insertCompact |
| D7-P4-T3 | PL/SQL | thenNewline / loopNewline / elseNewline / exceptionAlign |
| D7-P4-T4 | DDL | columnDefAlign / storageClauseFormat |

---

## A14 自动补全系统

| 编号 | 组件 | 类/包 |
|------|------|--------|
| A14-AC | 自动补全引擎 | AutoCompletion (rsyntaxtextarea) |
| A14-PROV | 补全提供器 | PlSqlCompletionProvider (kylin-sql-ui) |
| A14-CACHE | 元数据缓存 | MetadataCache (kylin-sql-core) |
| A14-TOKEN | 语法标记 | PlSqlTokenMaker |
| A14-ACT | 激活监听 | DocumentListener 直接触发 `doCompletion()` |
| A14-DELAY | 补全延迟 | 设置可调 (D7-P5-DELAY) |

### 补全范围
| 编号 | 类型 | 数据源 |
|------|------|--------|
| A14-C1 | 表名 | MetadataCache.getObjectNamesByType(conn, schema) → "TABLE" |
| A14-C2 | 视图名 | MetadataCache.getObjectNamesByType(conn, schema) → "VIEW" |
| A14-C3 | 列名（点后缀） | MetadataCache.getColumns(conn, schema, table) |
| A14-C4 | 关键字 | RSyntaxTextArea 内置关键字补全 |

### 别名解析
| 编号 | 机制 | 说明 |
|------|------|------|
| A14-ALIAS | `resolveAlias()` | 通过 `\b(?:FROM|JOIN|INTO)\s+table(?:\s+AS)?\s+alias` 正则解析 |

---

## A15 服务层 (Service Layer)

### Schema 服务
| 编号 | 类 | 说明 |
|------|------|------|
| A15-SCH | SchemaService | 抽象基类 |
| A15-SCH-OB | OceanBaseSchemaService | OB Schema 查询 |
| A15-SCH-MY | MySqlSchemaService | MySQL Schema 查询 |
| A15-SCH-PG | PostgreSqlSchemaService | PG Schema 查询 |

### 数据查询服务
| 编号 | 类 | 说明 |
|------|------|------|
| A15-DQ | DataQueryService | 抽象基类 |
| A15-DQ-OB | OceanBaseDataQueryService | OB 分页/预览查询 |
| A15-DQ-MY | MySqlDataQueryService | MySQL 分页/预览查询 |
| A15-DQ-PG | PostgreSqlDataQueryService | PG 分页/预览查询 |

### 导出服务
| 编号 | 类 | 说明 |
|------|------|------|
| A15-EXP | ExportService | 抽象基类 + 格式导出 (CSV/XLSX/JSON/XML/MD/INSERT) |
| A15-EXP-OB | OceanBaseExportService | OB 批量导出 |
| A15-EXP-MY | MySqlExportService | MySQL 批量导出 |
| A15-EXP-PG | PostgreSqlExportService | PG 批量导出 |

### 工厂
| 编号 | 类 | 说明 |
|------|------|------|
| A15-FACT | ServiceFactory | 根据 dbProduct 创建方言服务实例 |

### 数据模型
| 编号 | 类 | 说明 |
|------|------|------|
| A15-DP | DataPreview | 结果集元数据 + 行数据 |

---

## A16 高级导出对话框 (AdvancedExportDialog)

| 编号 | 控件 | 说明 |
|------|------|------|
| D16 | 高级导出对话框 | Modal |
| D16-MODE | 模式切换 | 结果集 / 表 / 自定义 SQL |
| D16-CONN | 连接选择 | 连接下拉框 |
| D16-SCHEMA | Schema 选择 | 跟随连接的级联下拉 |
| D16-TABLE | 表选择 | 跟随 Schema 的级联下拉 |
| D16-COLUMNS | 列列表 | 选取导出列 |
| D16-PREVIEW | 数据预览 | 10 行预览表格 |
| D16-FORMAT | 导出格式 | CSV / XLSX / JSON / XML / MD / INSERT |
| D16-PATH | 导出路径 | 文件保存路径 + 浏览按钮 |

### "表"模式级联
| 编号 | 步骤 | 动作 |
|------|------|------|
| D16-C1 | conn → schema | ServiceFactory → getSchemas() |
| D16-C2 | schema → table | ServiceFactory → getTables() |
| D16-C3 | table → columns | MetadataCache.getColumns() |
| D16-C4 | table → preview | DataQueryService.previewTable() |

---

## A17 通用对话框

### 日志查看器 (LogViewerDialog)
| 编号 | 控件 | 说明 |
|------|------|------|
| D17-LOG | 日志查看器 | 70% 屏幕尺寸 |
| D17-LOG-AREA | 日志文本区 | 只读 Monospaced 字体 |
| D17-LOG-AUTO | 自动滚动 | 自动跟踪最新日志 |

### 全局搜索 (GlobalSearchDialog)
| 编号 | 控件 | 说明 |
|------|------|------|
| D17-GS | 全局搜索 | 跨标签搜索 |
| D17-GS-FIELD | 搜索输入 | 实时搜索 |
| D17-GS-RESULTS | 结果列表 | 匹配文件+行号 |
| D17-GS-PREVIEW | 代码预览 | 光标行上下文 |

### 基础工具对话框 (BaseToolDialog)
| 编号 | 控件 | 说明 |
|------|------|------|
| D17-BASE | 工具对话框基类 | 统一工具栏 + 编辑区字体 |

---

## A18 导出/导入工具

### 导出对话框 (ExportDialog)
| 编号 | 控件 | 说明 |
|------|------|------|
| D18-EXP | 导出对话框 | 结果集导出 |
| D18-EXP-FMT | 格式选择 | CSV / XLSX / JSON / XML / MD / INSERT / HTML |
| D18-EXP-PATH | 文件路径 | 选择保存路径 |
| D18-EXP-SEP | 分隔符 | CSV 自定义分隔符 |
| D18-EXP-ENC | 编码 | UTF-8 / GBK |

### 导入对话框 (ImportDialog)
| 编号 | 控件 | 说明 |
|------|------|------|
| D18-IMP | 导入对话框 | 文件导入 SQL 执行 |
| D18-IMP-FILE | 文件选择 | 选择 SQL/CSV 文件 |
| D18-IMP-PREV | 内容预览 | 导入前预览 |

### 导出任务列表 (ExportTaskListDialog)
| 编号 | 控件 | 说明 |
|------|------|------|
| D18-TASK | 任务列表 | 批量导出任务队列 |
| D18-TASK-PROG | 进度 | 每个任务的进度条 |

---

## A19 其他工具对话框

| 编号 | 对话框 | 功能 |
|------|--------|------|
| D19-HIST | SqlHistoryDialog | SQL 执行历史记录浏览 |
| D19-CALL | CallHierarchyDialog | 函数/过程调用层级 |
| D19-DATA | DataGeneratorDialog | 测试数据生成 |
| D19-REGEX | RegexTesterDialog | 正则表达式测试 |
| D19-DIFF | TextDiffDialog | 文本差异对比 |
| D19-OBJ | ObjectSearchDialog | 数据库对象搜索 |
| D19-SQLTOOL | SqlToolsDialog | SQL 工具集合 |

---

## A20 持久化与配置

| 编号 | 组件 | 说明 |
|------|------|--------|
| A20-CFG | ConfigManager | 配置读写 (JSON 工作区) |
| A20-WS | WorkspaceState | 工作区状态模型（theme / colorOverrides / fontOverrides / 窗口尺寸等） |
| A20-THEME | ThemeManager | 主题管理（3 预设 + 用户颜色覆盖） |
| A20-FONT | FontManager | 字体管理（17 区域 + 用户覆盖） |
| A20-AUTOSAVE | 自动保存 | ConfigManager 定时保存当前标签 |
| A20-WS-RESTORE | 工作区恢复 | tryRestoreWorkspace() 按 tabStates 恢复标签 |

---

## 使用示例

```
Bug: 选中多 SQL ▶ 执行后，E2-2/E2-3 圆点只出现在第一条语句的行号旁
→ 应每个 SQL 语句都有独立的 E2-2/E2-3

需求：D7-P2-G9 exec.highlight 颜色不生效于 E2-1 代码段框

Bug: 点击 A2-ED-TB B2-1 ▶ 执行后，E2-2/E2-3 需要点一下 A2-ED-TA 才显示
→ 应执行后立即显示

需求：双击 A6-TABS 列标题触发 F6-1 后，E10-5 Toast 提示

优化：A12-KEYS font.editor 字号从 14 调到 16 时，A12-UI 预览立即反映变化
```
