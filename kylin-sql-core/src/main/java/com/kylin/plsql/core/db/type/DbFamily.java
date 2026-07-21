package com.kylin.plsql.core.db.type;

/**
 * 数据库语系分类 / Database language family classification.
 * <p>
 * 用于 UI 分组和格式化引擎路由，不是行为决策的单位（行为在 {@link JdbcService} 中按类型独立实现）。
 * Used for UI grouping and formatting engine routing, NOT for behavioral decisions.
 */
public enum DbFamily {
    /** Oracle 语系：Oracle, OceanBase-Oracle */
    ORACLE,
    /** MySQL 语系：MySQL, MariaDB, OceanBase-MySQL */
    MYSQL,
    /** 其他：PostgreSQL 及未来扩展类型 */
    OTHER
}
