package com.kylin.plsql.core.format.dialect;

/** OceanBase-specific SQL dialect (extends Oracle). */
public class OceanBaseDialect extends OracleDialect {

    @Override
    public String getName() { return "OceanBase"; }
}
