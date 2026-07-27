# Kylin PL/SQL Developer 项目规划

> 麒麟系统 PL/SQL 开发工具 - OceanBase Oracle 模式兼容  
> JDK 17 | 麒麟 V10 SP1 | 配置目录: `~/.kylin-plsql`

---

## 一、技术选型

| 层面 | 选择 | 理由 |
|------|------|------|
| **UI 框架** | Java Swing + FlatLaf 3.x | 成熟稳定、麒麟系统自带 JDK 即可运行、长期维护成本最低 |
| **构建工具** | Maven 多模块 | 企业级标准、依赖管理成熟、麒麟 maven 包可用 |
| **SQL 编辑器** | RSyntaxTextArea 3.x | 原生 Swing、轻量、PL/SQL 语法高亮/折叠/补全框架就绪 |
| **PL/SQL 解析** | ANTLR 4 + Oracle PL/SQL Grammar | 标准解析器生成器、定义跳转/大纲/格式化均基于 AST |
| **数据库连接池** | HikariCP 6.x | 业界最快的连接池、零配置即可用 |
| **JDBC 驱动** | PostgreSQL JDBC 42.x (OceanBase 优先) + Oracle JDBC 21.x (备选) | OceanBase Oracle 模式 PG 协议兼容性好 |
| **格式化** | 自研 ANTLR AST 重写 | 可控、可扩展 |
| **配置持久化** | JSON (Gson) | 零依赖、可读可编辑 |
| **日志** | Logback | 标准 SLF4J 实现 |
| **打包** | maven-assembly-plugin + jpackage | 解压即用 (tar.gz/zip) + 原生 .deb 双通道 |

---

## 二、项目结构

```
kylin-plsql/
├── pom.xml                          # 父 POM
├── JK_PLAN.MD                       # 本规划文件
├── task_log.md                      # 进度日志
├── kylin-plsql-core/                # 核心模块
│   ├── pom.xml
│   └── src/main/java/com/kylin/plsql/core/
│       ├── cache/
│       │   └── MetadataCache.java     # 元数据本地缓存 (Gson JSON)
│       ├── config/ConfigManager.java  # ~/.kylin-plsql 配置管理
│       └── db/
│           ├── ConnectionInfo.java    # 连接信息模型
│           ├── ConnectionManager.java # HikariCP 连接池管理
│           ├── SqlExecutor.java       # SQL 执行器 + DDL/预览生成
│           └── SqlHistory.java        # SQL 历史记录
├── kylin-plsql-ui/                  # UI 模块
│   ├── pom.xml
│   └── src/main/java/com/kylin/plsql/ui/
│       ├── KylinPlSqlApp.java        # 主入口
│       ├── MainFrame.java            # 主窗口 (经典 PL/SQL Developer 布局)
│       ├── component/
│       │   ├── ObjectBrowser.java    # 左侧对象浏览器
│       │   ├── SqlEditorPanel.java   # 中间编辑器 (RSyntaxTextArea)
│       │   ├── RightPanel.java       # 右侧面板 (FILES + THUMBNAIL)
│       │   ├── LeftPanel.java        # 左侧面板 (DATABASE 可折叠)
│       │   ├── VerticalTabButton.java# 竖排标签按钮(左右面板共用)
│       │   ├── WelcomePanel.java     # 欢迎页 (快捷键/技巧)
│       │   └── ResultPanel.java      # 底部结果集
│       └── dialog/
│           └── ConnectionDialog.java # 连接管理对话框
└── kylin-plsql-assembly/            # 打包模块
    ├── pom.xml
    └── src/main/assembly/
        ├── dist.xml                  # 解压即用包配置
        ├── kylin-plsql.sh            # Linux/Kylin 启动脚本
        └── kylin-plsql.bat           # Windows 启动脚本
```

---

## 三、功能清单与优先级

### P0 - 核心骨架 (M1: Week 1)
- [x] 项目初始化 (Maven 多模块、FlatLaf 主题)
- [x] 主窗口布局 (左: 对象浏览器 / 中: 编辑器+结果 / 右: 大纲)
- [x] 菜单栏、工具栏、状态栏
- [x] 连接管理 (新建/保存/测试/删除/切换)
- [x] 基础 SQL 执行 (SELECT/INSERT/UPDATE/DELETE)
- [x] 结果集表格展示
- [x] 配置管理 (`~/.kylin-plsql`)
- [x] 解压即用包 (tar.gz/zip + 启动脚本)

### P1 - 编辑器增强 (M2: Week 2) ⚡ 待 M7.9 增强
- [x] 代码折叠 — RSyntaxTextArea 内置
- [x] 括号/关键字匹配 — RSyntaxTextArea 内置
- [x] 自动补全 (关键词 7 大类)
- [x] 查找替换 (Ctrl+F/H)
- [x] 打开/保存 SQL 文件
- [ ] PL/SQL 语法高亮深化 (%TYPE, %ROWTYPE, BULK COLLECT 等) — 待 M7.9

### P2 - PL/SQL 导航 (M3: Week 3) ⭐ 核心差异化
- [x] ANTLR PL/SQL 语法集成 (grammars-v4 官方语法 + antlr4-maven-plugin)
- [x] 包体大纲解析 (提取 Procedure/Function 签名及行号)
- [x] 定义跳转 (大纲点击 → navigateToLine)
- [ ] 大纲与编辑器同步 (光标移动高亮当前方法) — 待 M7.5
- [ ] 调用层级 (Ctrl+Alt+H) — 待 M7.7
- [ ] 跨包符号索引 — 待 M7.8

### P3 - 代码格式化 (M4: Week 4) ⚡ 待 M7.2 增强
- [x] 格式化引擎（基于 ANTLR PlSqlLexer 词法标记 + 关键字集合匹配）
- [x] 关键字大小写控制（UPPER / LOWER / PRESERVE）
- [x] 缩进/换行（BEGIN/LOOP/THEN 缩进跟踪，分号新行）
- [x] 首选项面板（PreferencesDialog）
- [ ] 对齐配置（列对齐等精细控制）— 待 M7.2

### P4 - 对象浏览增强 (M5: Week 5) ✅ / 完毕

### P5 - 结果与工具 (M5: Week 5) ⚡ 待 M7 增强
- [x] 列宽自适应 (FontMetrics 精确计算)
- [x] 导出 CSV (文件 + 剪贴板，UTF-8 BOM)
- [x] 行数状态显示
- [ ] 事务控制 (提交/回滚) — 待 M7.3
- [ ] SQL 历史记录 — 待 M7.4
- [ ] 执行计划可视化 — 待 M7.6

### M6 - 交付 (Week 6) ⚡ 待 M7.1 补充
- [x] 解压即用包 (tar.gz + zip + 启动脚本)
- [ ] 麒麟原生 deb 包构建说明 — 待 M7.1
- [x] 快捷键速查表
- [x] 使用手册

---

## M7: 剩余功能补全 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 7.1 | ⭐ | ✅ | M6 | deb 包构建说明 |
| 7.2 | ⭐⭐ | ✅ | P3 | 对齐配置 |
| 7.3 | ⭐⭐⭐ | ✅ | P5 | 事务控制 |
| 7.4 | ⭐⭐ | ✅ | P5 | SQL 历史记录 |
| 7.5 | ⭐⭐ | ✅ | P2 | 大纲同步 |
| 7.6 | ⭐⭐⭐ | ✅ | P5 | 执行计划可视化 |
| 7.7 | ⭐⭐⭐⭐ | ✅ | P2 | 调用层级 |
| 7.8 | ⭐⭐⭐⭐ | ✅ | P2 | 跨包符号索引 |
| 7.9 | ⭐⭐⭐⭐⭐ | ✅ | P1 | 语法高亮深化 |

---

## M8: 连接 + 对象浏览器修复 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 8.1 | ⭐⭐ | ✅ | P0 | 连接对话框支持 JDBC URL 直连模式 |
| 8.2 | ⭐⭐⭐ | ✅ | P4 | 对象浏览器 schema 获取降级 + 空数据提示 |
| 8.3 | ⭐⭐ | ✅ | P0 | HikariCP 连接测试查询 Oracle 兼容 (SELECT 1 FROM DUAL) |
| 8.4 | ⭐⭐⭐⭐ | ✅ | P4 | DataGrip 风格树结构重构（按库类型自适应） |

## M9: DataGrip 风格编辑器 + 元数据缓存 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 9.1 | ⭐⭐⭐⭐⭐ | ✅ | P0 | 编辑器顶部区域重设计 — 连接/Schema 移到每个标签页工具栏 |
| 9.2 | ⭐⭐⭐ | ✅ | P4 | 左侧对象浏览器 DataGrip 风格图标 + 纯图标工具栏 |
| 9.3 | ⭐⭐⭐ | ✅ | P5 | 数据预览数据库语法差异处理 (FETCH FIRST / LIMIT) |
| 9.4 | ⭐⭐⭐⭐ | ✅ | P4 | 增强 DDL 生成（分区表/主键/注释/索引） |
| 9.5 | ⭐⭐⭐⭐⭐ | ✅ | P4 | 元数据本地缓存 (MetadataCache JSON 磁盘缓存) |
| 9.6 | ⭐⭐ | ✅ | P0 | 连接管理对话框属性预选 |

## M10: DataGrip Darcula 主题 + UI 精简 + 查询超时 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 10.1 | ⭐⭐ | ✅ | P0 | Darcula 暗色主题 (FlatDarculaLaf) |
| 10.2 | ⭐⭐ | ✅ | P0 | 工具栏全部 icon-only flat buttons |
| 10.3 | ⭐ | ✅ | P0 | 全部 TitledBorder 移除 |
| 10.4 | ⭐⭐ | ✅ | P0 | RSyntaxTextArea 暗色主题加载 |
| 10.5 | ⭐⭐⭐ | ✅ | P0 | 连接查询超时（per-statement） |

## M11: SQL 语句自动检测 + 执行日志 + 异步优化 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 11.1 | ⭐⭐⭐ | ✅ | P0 | 分号段 SQL 语句检测 + 高亮 |
| 11.2 | ⭐⭐ | ✅ | P0 | Gutter 执行结果图标 (✓/❗) |
| 11.3 | ⭐⭐ | ✅ | P0 | 结构化执行日志 |
| 11.4 | ⭐ | ✅ | P0 | SqlExecutor 去尾分号 |
| 11.5 | ⭐⭐⭐ | ✅ | P0 | onObjectAction 全异步 SwingWorker |

## M12: 工作空间持久化 + 欢迎页 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 12.1 | ⭐⭐⭐ | ✅ | P0 | 标签页状态持久化 (workspace.json) |
| 12.2 | ⭐⭐ | ✅ | P0 | 欢迎页面 (WelcomePanel) |

## M13: DataGrip 风格右侧面板 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 13.1 | ⭐⭐⭐ | ✅ | P0 | RightPanel 竖排标签栏 + FILES 文件列表 |
| 13.2 | ⭐⭐⭐ | ✅ | P0 | THUMBNAIL 迷你缩略图 + 行跳转 |
| 13.3 | ⭐⭐ | ✅ | P0 | 已保存文件追踪 (saved_files.json) |

## M14: DataGrip 风格左侧面板（可折叠）+ 工具栏样式 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 14.1 | ⭐⭐⭐ | ✅ | P0 | LeftPanel DATABASE 折叠/展开 |
| 14.2 | ⭐ | ✅ | P0 | 工具栏底边 1px 分割线 + 竖线分隔符 |

## M15: VerticalTabButton 字体/尺寸修复 + 左右标签条 Debug ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 15.1 | ⭐⭐⭐ | ✅ | P0 | VerticalTabButton 字体/尺寸修复——`getFontMetrics()` 返回无效指标的排查与修复 |
| 15.2 | ⭐ | ✅ | P0 | 发现 `FlatButtonUI.paint()` 返回 true 时拦截 `paintComponent()` |
| 15.3 | ⭐ | ✅ | P0 | Debug 彩色边框保留待后续统一移除 |

## M16: DataGrip 风格底部工具窗口（TODO + Services） ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 16.1 | ⭐⭐⭐ | ✅ | P0 | BottomPanel 底部工具窗口（Todo/Services 标签，展开/收起） |
| 16.2 | ⭐⭐⭐ | ✅ | P0 | 布局改造：移除 editorSplit，editorPanel 直入 leftSplit；bottomWrapper 全宽底部 |
| 16.3 | ⭐⭐ | ✅ | P0 | Services 连接树 auto-refresh（标签增删/切换同步） |
| 16.4 | ⭐ | ✅ | P0 | auto-expand：SQL 执行时自动展开 Services 面板 |

## M17: DataGrip 风格 Services 结果区 + 语句高亮修复 + 标签紧凑化 ✅

| 序号 | 难度 | 状态 | 所属 | 功能 |
|------|------|------|------|------|
| 17.1 | ⭐⭐⭐⭐ | ✅ | P0 | Services 结果区仿 DataGrip：消息标签固定在左侧第 1 个；多结果标签 `resultN`；行号列；分页工具栏（◀ 下拉框 ▶ ↻ ■ 📌） |
| 17.2 | ⭐⭐⭐ | ✅ | P0 | 同 SQL 复用：连续执行同 SQL 覆盖 unpinned 标签；Pin 标签防止覆盖 |
| 17.3 | ⭐⭐ | ✅ | P0 | 结果标签关闭按钮（×），消息标签不可关闭 |
| 17.4 | ⭐⭐ | ✅ | P0 | 智能标签命名：单表查询显示表名，复杂查询回落 `resultN` |
| 17.5 | ⭐⭐⭐ | ✅ | P0 | 分页工具栏嵌入每个结果标签内容区 NORTH（标签按钮之下、表格之上） |
| 17.6 | ⭐⭐⭐ | ✅ | P0 | 语句高亮修复：`DynamicSegmentPainter`（一次性注册 LayerPainter，动态检查当前语句块，消除光标移动时的背景闪烁） |
| 17.7 | ⭐⭐ | ✅ | P0 | 右侧面板折叠整面板：toggle callback → mainSplit 显式 divider |
| 17.8 | ⭐⭐ | ✅ | P0 | 左右标签紧凑化：字体 PLAIN 9→10，padding +6→+2，border 纵向归零 |
| 17.9 | ⭐⭐ | ✅ | P0 | 编辑器字体 Monospaced 14pt（theme 之后设置），高亮透明度 0x30→0x60，左侧 3px 竖线框框效果 |

---

## 四、打包策略

### 4.1 解压即用包（优先测试）
```bash
# 方式一: Maven 构建 (所有模块)
mvn clean package -DskipTests

# 产出物:
kylin-plsql-assembly/target/kylin-plsql-1.0.0-SNAPSHOT.tar.gz
kylin-plsql-assembly/target/kylin-plsql-1.0.0-SNAPSHOT.zip

# 使用 (麒麟):
tar -xzf kylin-plsql-*.tar.gz
cd kylin-plsql-*
./kylin-plsql.sh
```

### 4.2 麒麟原生 deb 包（备选）
```bash
# 需麒麟环境 + JDK 17 + jpackage:
mvn clean package
jpackage --input target/jlink --name kylin-plsql --main-jar kylin-plsql*.jar \
  --main-class com.kylin.plsql.ui.KylinPlSqlApp \
  --type deb --java-options "-Dkylin.plsql.home=."
```

---

## 五、关键技术难点与对策

| 难点 | 对策 |
|------|------|
| **OceanBase Oracle 语法兼容** | 优先 PGJDBC + `ALL_*` 视图；差异部分做适配层 |
| **PL/SQL 解析器精度** | 基于 ANTLR 官方 PL/SQL 语法 + OceanBase 差异扩展 |
| **大文件/大结果集性能** | 编辑器虚拟化、结果集懒加载(50 行/批) |
| **跨包跳转** | 建立内存符号索引 Map<QualifiedName, Location> |
| **麒麟 Wayland/UKUI 渲染** | FlatLaf 原生支持 HiDPI；测试麒麟 V10 SP1 默认桌面 |

---

## 六、测试策略

| 层级 | 覆盖目标 |
|------|----------|
| **单元测试** | 解析器、格式化器、配置管理 ≥ 80% |
| **集成测试** | 真 OceanBase 连接、元数据查询、SQL 执行 |
| **手动测试** | 麒麟 V10 SP1 环境 + 麒麟应用商店安装验证 |

---

## 七、部署要求

| 项目 | 要求 |
|------|------|
| JDK | OpenJDK 17 LTS (麒麟 V10 SP1 软件源: `sudo apt install openjdk-17-jdk`) |
| 屏幕 | 推荐 1920×1080+ |
| 内存 | ≥ 512 MB |
| 存储 | 解压即用包 ~50 MB |
| 配置目录 | `~/.kylin-plsql` |
