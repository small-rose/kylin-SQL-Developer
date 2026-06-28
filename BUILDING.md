# Kylin SQL Developer - 构建指南

## 前置要求

- JDK 17+
- Maven 3.8+
- 麒麟 V10 SP1 (构建 deb 包需要)

## 构建解压即用包

```bash
# 在项目根目录下
mvn clean package -DskipTests
```

产物位于 `kylin-sql-assembly/target/`：
- `kylin-sql-1.0.0.tar.gz`
- `kylin-sql-1.0.0.zip`

解压后运行 `kylin-sql.sh`（Linux）或 `kylin-sql.bat`（Windows）。

## 构建麒麟原生 deb 包

在 **麒麟 V10 SP1** 环境中执行：

```bash
# 1. 安装依赖
sudo yum install -y java-17-openjdk-devel maven rpm-build

# 2. 构建应用镜像 + deb 包
mvn clean package -DskipTests -Pdeb
```

> **说明**：`-Pdeb` profile 会激活 `maven-jlink-plugin` 和 `jpackage`，将 JRE + 应用打包为原生 deb 包。
> 由于 jpackage 依赖平台原生打包工具，此步骤 **必须** 在麒麟系统上执行。

## 常见问题

**Q: 在 Windows 上构建 deb 包？**  
A: 不支持。deb 包需在 Linux/麒麟系统上通过 `jpackage` 生成。

**Q: 如何验证 deb 包？**  
```bash
sudo dpkg -i target/kylin-sql-*.deb
kylin-sql
```
