<div align="center">
  <img src="kylin-sql-ui/src/main/resources/logo/kylin_192x192.png" alt="Kylin SQL Developer Logo" width="96" height="96">
  <h1>Kylin SQL Developer</h1>
  <p><strong>轻量级 · 跨平台 · PL/SQL 集成开发工具</strong></p>

  <p>
    <img src="https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk&logoColor=white" alt="Java 17+">
    <img src="https://img.shields.io/badge/License-Apache%202.0-green?logo=apache" alt="License">
    <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey?logo=windows" alt="Platform">
    <img src="https://img.shields.io/badge/Kylin_OS-✅-brightgreen?logo=linux" alt="Kylin OS">
    <img src="https://img.shields.io/github/stars/small-rose/kylin-SQL-Developer?style=social" alt="GitHub Stars">
    <img src="https://img.shields.io/github/forks/small-rose/kylin-SQL-Developer?style=social" alt="GitHub Forks">
    <br>
    <img src="https://img.shields.io/badge/UI-FlatLaf%203.5-8A2BE2?logo=swing" alt="FlatLaf">
    <img src="https://img.shields.io/badge/Parser-ANTLR%204.13-red?logo=antlr" alt="ANTLR4">
    <img src="https://img.shields.io/badge/Connection_Pool-HikariCP%206.2-00BFFF?logo=java" alt="HikariCP">
    <img src="https://img.shields.io/badge/Editor-RSyntaxTextArea%203.6-orange" alt="RSyntaxTextArea">
    <img src="https://img.shields.io/badge/Databases-6%20supported-006400?logo=database" alt="Databases">
    <img src="https://img.shields.io/badge/Free_%26_Open_Source-❤-red?logo=github" alt="Free">
    <br>
    <img src="https://img.shields.io/github/last-commit/small-rose/kylin-SQL-Developer?logo=git" alt="Last Commit">
    <img src="https://img.shields.io/github/repo-size/small-rose/kylin-SQL-Developer" alt="Repo Size">
    <img src="https://img.shields.io/github/issues/small-rose/kylin-SQL-Developer" alt="Issues">
    <img src="https://img.shields.io/github/v/release/small-rose/kylin-SQL-Developer?include_prereleases&logo=github" alt="Release">
    <img src="https://img.shields.io/github/downloads/small-rose/kylin-SQL-Developer/total?logo=github" alt="Downloads">
    <img src="https://img.shields.io/badge/Made%20with-%E2%99%A5-red" alt="Made with love">
  </p>
</div>

---

## 目录

- [项目简介](#项目简介)
- [核心功能](#核心功能)
- [界面预览](#界面预览)
- [支持的数据库](#支持的数据库)
- [功能对比](#功能对比)
- [快速开始](#快速开始)
- [编译构建](#编译构建)
- [快捷键](#快捷键)
- [技术栈](#技术栈)
- [项目架构](#项目架构)
- [许可证](#许可证)

---

## 项目简介

**Kylin SQL Developer** 是一款面向 **Oracle / OceanBase / MySQL / PostgreSQL** 数据库的轻量级 PL/SQL 开发工具，基于 Java Swing 构建。它集语法高亮编辑器、SQL 格式化引擎、智能自动补全、数据导出、对象浏览器于一体，特别适配国产**麒麟操作系统**。

> 本项目源于对 OceanBase Oracle 模式下 PL/SQL 开发的需求，在国产化替代的大背景下，希望能为广大开发者提供一个免费、开源、好用的数据库开发工具。

---

## 核心功能

### 编辑器

| 功能 | 说明 |
|------|------|
| 语法高亮 | 基于 RSyntaxTextArea，自定义 PL/SQL TokenMaker |
| 代码折叠 | 折叠函数/过程/包/IF/LOOP 等结构 |
| 智能自动补全 | 表名、视图名、列名、关键字（MetadataCache 驱动） |
| 多标签页 | 同时编辑多个 SQL 文件 |
| 行号/错误标记 | 执行结果用 ✓/❗ 图标标记在行号区 |
| 搜索替换 | Ctrl+F 查找 / Ctrl+H 替换 |

### SQL 格式化引擎

- **4 种数据库方言**：Oracle、OceanBase、MySQL、PostgreSQL
- **40+ 可配置参数**：关键字大小写、缩进、逗号位置、WHERE 对齐等
- **3 套预设配置**：默认 / 展开 / 紧凑
- **模板驱动架构**：4 阶段约束求解器（结构解析 → 行宽 → 对齐 → 合并）
- **支持复杂 PL/SQL**：存储过程、函数、包、触发器、游标、异常处理
- **内置第三方引擎**：JSQLFormatter（JSQLParser 驱动）

### 数据库连接

- 支持 6 种数据库：Oracle、OceanBase（Oracle 模式）、OceanBase（MySQL 模式）、MySQL、MariaDB、PostgreSQL
- 连接池：HikariCP
- 每个标签页独立连接 + Schema 选择
- 连接颜色标记区分
- 自动重连监控
- 查询超时设置

### 数据导出

- **5 种导出格式**：INSERT / CSV / JSON / XML / Markdown
- **3 种导出模式**：结果集 / 表模式（Schema→Table 级联选择）/ 自定义 SQL
- 后台批量导出任务管理
- INSERT 语句支持 4 种方言：Oracle / MySQL / PostgreSQL / ANSI

### 对象浏览器

- 数据库树：连接 → Schema → 表/视图/函数/过程/包
- 双击 → 数据预览
- 右键菜单 → DDL / DML / 展开包
- 展开路径持久化（重启自动恢复）
- 本地文件浏览器

### 内置工具

| 工具 | 快捷键 | 说明 |
|------|--------|------|
| 全局对象搜索 | `Ctrl+P` | 跨数据库搜索对象名称 |
| 调用层次 | `Ctrl+Alt+H` | 分析 PL/SQL 调用关系 |
| 数据生成器 | - | 生成测试数据 |
| 文本对比 | - | 对比两个 SQL 文本差异 |
| 正则测试器 | - | 实时正则表达式调试 |
| SQL 历史 | `Ctrl+Shift+H` | 持久化历史记录 |
| 执行计划 | `Ctrl+E` | 查看 SQL 执行计划 |

### 主题

- **Darcula**（深色模式）
- **Light**（浅色模式）
- **豆沙绿**（护眼模式）
- 完全自定义主题（XML 配置文件）

---

## 界面预览

> 截图待补充

| 模块 | 预览 |
|------|------|
| 主界面 | `[TODO: 主窗口截图]` |
| SQL 编辑器 | `[TODO: 编辑器截图]` |
| 格式化设置 | `[TODO: 设置对话框截图]` |
| 数据导出 | `[TODO: 导出对话框截图]` |
| 对象浏览器 | `[TODO: 对象树截图]` |

---

## 支持的数据库

| 数据库 | 标识 Key | 默认端口 | 驱动类 | 协议族 |
|--------|----------|---------|--------|--------|
| Oracle | `oracle` | 1521 | oracle.jdbc.OracleDriver | ORACLE |
| OceanBase (Oracle 模式) | `oceanbase-oracle` | 2883 | com.oceanbase.jdbc.Driver | ORACLE |
| OceanBase (MySQL 模式) | `oceanbase-mysql` | 2883 | com.oceanbase.jdbc.Driver | MYSQL |
| MySQL | `mysql` | 3306 | com.mysql.cj.jdbc.Driver | MYSQL |
| MariaDB | `mariadb` | 3306 | org.mariadb.jdbc.Driver | MYSQL |
| PostgreSQL | `postgresql` | 5432 | org.postgresql.Driver | OTHER |

---

## 功能对比

| 功能 | **Kylin SQL Developer** | **PL/SQL Developer** | **DBeaver** | **Navicat** | **DataGrip** |
|------|:---:|:---:|:---:|:---:|:---:|
| 跨平台 | ✅ | ❌ 仅 Windows | ✅ | ❌ Win/Mac | ✅ |
| 国产 OS 适配 | ✅ 麒麟 Kylin | ❌ | ⚠️ 需手动 | ❌ | ❌ |
| 免费开源 | ✅ Apache 2.0 | ❌ 商业付费 | ✅ | ❌ 商业付费 | ❌ 商业付费 |
| Oracle PL/SQL 格式化 | ✅ 40+ 参数模板 | ✅ | ❌ 基础 | ✅ 基础 | ✅ 中等 |
| 智能自动补全 | ✅ MetadataCache | ✅ | ✅ | ✅ | ✅ |
| 数据导出 | ✅ 5 格式 | ✅ 有限 | ✅ | ✅ | ✅ |
| 连接池 | ✅ HikariCP | ❌ | ✅ | ❌ | ❌ |
| 轻量级 (~15MB) | ✅ | ✅ | ❌ (~200MB) | ❌ (~100MB) | ❌ (~400MB) |
| JDK 17+ | ✅ | N/A | ✅ | N/A | ✅ |
| 启动速度 | ⚡ <3s | ⚡ <2s | 🐢 ~8s | ⚡ <3s | 🐢 ~10s |

---

## 快速开始

### 下载

从 [Releases](https://github.com/small-rose/kylin-SQL-Developer/releases) 下载最新版本：

| 包格式 | 适用平台 | 使用方法 |
|--------|---------|---------|
| `.zip` | Windows | 解压后运行 `kylin-sql.bat` |
| `.tar.gz` | Linux / macOS | 解压后运行 `kylin-sql.sh` |
| `.deb` | 麒麟 V10（原生包） | `sudo dpkg -i kylin-sql-*.deb`，然后运行 `kylin-sql` |

### 系统要求

| 要求 | 最小 | 推荐 |
|------|-----|------|
| JDK | 17+ | 17 LTS 或更高 |
| 内存 | 256 MB | 1 GB+ |
| 磁盘 | 100 MB | 500 MB |
| 操作系统 | Windows 10 / Linux (Kylin) / macOS 12+ | - |
| 屏幕分辨率 | 1280×720 | 1920×1080+ |

### Windows 启动

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-17
kylin-sql.bat
```

### Linux / macOS 启动

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./kylin-sql.sh
```

### 首次使用

1. 启动后点击工具栏 **连接管理** 按钮（🔗 图标）
2. 填写连接信息：主机、端口、数据库名、用户名、密码
3. 点击 **测试连接** 确认连通性
4. 保存连接，在左侧对象浏览器中浏览数据库对象
5. 在编辑器中输入 SQL，按 `F8` 执行
6. 使用 `Ctrl+Shift+F` 格式化 SQL

---

## 编译构建

### 前置条件

- JDK 17+
- Maven 3.8+
- Git

### 快速构建

```bash
# 克隆项目
git clone https://github.com/small-rose/kylin-SQL-Developer.git
cd kylin-SQL-Developer

# 编译并打包（跳过测试）
mvn clean package -DskipTests

# 产物位于：
#   kylin-sql-assembly/target/kylin-sql-1.0.0.zip
#   kylin-sql-assembly/target/kylin-sql-1.0.0.tar.gz
```

### 构建并启动（一步到位）

```bash
mvn package -pl kylin-sql-assembly -am -DskipTests
cd dist/kylin-sql-1.0.0-SNAPSHOT
kylin-sql.bat        # Windows
# 或 ./kylin-sql.sh  # Linux / macOS
```

> `package` 阶段会自动将 zip 解压到项目根目录的 `dist/` 目录，无需手动解压。

### 构建麒麟原生 DEB 包

> 需在 **麒麟 V10 SP1** 环境执行

```bash
sudo yum install -y java-17-openjdk-devel maven rpm-build
mvn clean package -DskipTests -Pdeb
sudo dpkg -i target/kylin-sql-*.deb
kylin-sql
```

### 模块说明

| 模块 | 说明 |
|------|------|
| `kylin-sql-parser` | ANTLR4 PL/SQL 解析器 |
| `kylin-sql-formatter` | 模板驱动的 SQL 格式化引擎（4 阶段约束求解） |
| `kylin-sql-core` | 业务逻辑层：连接管理、服务、导出、缓存、配置 |
| `kylin-sql-ui` | Swing UI 层：主窗口、对话框、编辑器组件 |
| `kylin-sql-assembly` | 分发打包：zip/tar.gz/deb + 启动脚本 |

---

## 快捷键

### 文件操作

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+N` | 新建 SQL 文件 |
| `Ctrl+O` | 打开 SQL 文件 |
| `Ctrl+S` | 保存 |
| `Ctrl+Shift+S` | 另存为 |
| `Ctrl+W` | 关闭当前标签 |

### 编辑

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+Z` | 撤销 |
| `Ctrl+Y` | 重做 |
| `Ctrl+F` | 查找 |
| `Ctrl+H` | 替换 |

### SQL 执行

| 快捷键 | 功能 |
|--------|------|
| `F8` / `Ctrl+Shift+F10` | 执行当前 SQL |
| `F9` | 追加执行（保留之前的结果） |
| `Ctrl+E` | 执行计划 (Explain Plan) |
| `Ctrl+Shift+F` | 格式化 SQL |

### 导航

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+P` | 全局对象搜索 |
| `Ctrl+Shift+H` | SQL 历史 |
| `Ctrl+Alt+H` | 调用层次 |
| `Ctrl+Alt+S` | 设置 |

---

## 技术栈

| 技术 | 用途 |
|------|------|
| **Java 17** | 开发语言 |
| **Swing / FlatLaf 3.5** | UI 框架 & 主题 |
| **RSyntaxTextArea 3.6** | 代码编辑器组件 |
| **AutoComplete 3.3** | 自动补全 |
| **ANTLR4 4.13** | SQL 解析器生成器 |
| **HikariCP 6.2** | 数据库连接池 |
| **Gson 2.11** | JSON 序列化 |
| **SVGSalamander 1.1** | SVG 图标渲染 |
| **Logback 1.5** | 日志框架 |
| **Maven 3.8+** | 构建工具 |
| **JUnit 5** | 单元测试 |

---

## 项目架构

### 分层架构图

```
┌─────────────────────────────────────────┐
│           UI 层 (Swing)                 │  kylin-sql-ui
│  MainFrame · Dialogs · Editors · Panels │
├─────────────────────────────────────────┤
│           服务层                         │  kylin-sql-core
│  Schema / DataQuery / DataChange /      │
│  Export / Import / ServiceFactory       │
├─────────────────────────────────────────┤
│           数据库层                       │  kylin-sql-core
│  ConnectionManager · SqlExecutor ·      │
│  ConnectionPool (HikariCP) · History    │
├─────────────────────────────────────────┤
│         格式化引擎                       │  kylin-sql-formatter
│  4 阶段约束求解器:                       │
│  StructuralResolver → LineWidthResolver │
│  → AlignmentResolver → LayoutMerger     │
├─────────────────────────────────────────┤
│         解析器 (ANTLR4)                 │  kylin-sql-parser
│  PL/SQL Lexer · Parser · SymbolIndex   │
└─────────────────────────────────────────┘
```

### 格式化引擎核心流程

```
源 SQL
    ↓ Tokenizer
Token 流
    ↓ ConstraintGenerator (40+ 参数)
约束规约 (Constraint Spec)
    ↓ Stage 1: StructuralResolver → 缩进骨架
    ↓ Stage 2: LineWidthResolver  → 断行决策
    ↓ Stage 3: AlignmentResolver  → 列对齐
    ↓ Stage 4: LayoutMerger       → 输出合并
格式化后的 SQL
```

### 文件统计

| 模块 | 源文件数 |
|------|---------|
| kylin-sql-parser | ANTLR4 语法文件 |
| kylin-sql-formatter | ~70+ |
| kylin-sql-core | 40 |
| kylin-sql-ui | 39 |
| kylin-sql-assembly | 9 脚本 + 配置 |
| **总计** | **~160+** |

---

## 许可证

本项目基于 **Apache License 2.0** 开源。

```
Copyright © 2026 Kylin Team

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

<div align="center">
  <p>⭐ 如果这个项目对你有帮助，请给它一个 Star！ ⭐</p>
  <p>
    <a href="https://github.com/small-rose/kylin-SQL-Developer/issues">报告问题</a>
    ·
    <a href="https://github.com/small-rose/kylin-SQL-Developer/issues">功能建议</a>
  </p>
</div>
