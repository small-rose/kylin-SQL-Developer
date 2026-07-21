package com.kylin.plsql.core.db.type;

import java.util.List;

/**
 * 数据库类型数据描述接口 / Data-only descriptor for a database type.
 * <p>
 * 纯数据（POJO 风格），不含任何业务逻辑。每个数据库类型对应一个不可变单例实现。
 * 行为层在 {@code services/JdbcService} 中。
 * Immutable data singleton — no business logic. Behavior is in JdbcService.
 */
public interface DbTypeSpec {

    /** 唯一标识键，如 "oracle" / "oceanbase-oracle" / "mysql" */
    String getKey();

    /** UI 显示名称，如 "Oracle" / "OceanBase-Oracle" / "MySQL" */
    String getDisplayName();

    /** 默认端口号 */
    int getDefaultPort();

    /** JDBC 驱动全限定类名 */
    String getDriverClassName();

    /** JDBC URL 前缀列表（用于反向检测），长前缀在前 */
    List<String> getUrlPrefixes();

    /** 语系分类 */
    DbFamily getFamily();
}
