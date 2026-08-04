package com.kylin.plsql.core.pojo;

import java.util.List;

/** 数据库对象类型定义，包含类型代码、显示标签、查询 SQL、固定值列表、是否可展开等。 */
public class ObjectType {
    public final String label, typeCode;
    public final String querySql;
    public final List<String> fixedValues;
    public final boolean expandable;

    public ObjectType(String label, String typeCode, String querySql, List<String> fixedValues, boolean expandable) {
        this.label = label;
        this.typeCode = typeCode;
        this.querySql = querySql;
        this.fixedValues = fixedValues;
        this.expandable = expandable;
    }

    public boolean useSchemaService() {
        return querySql == null && fixedValues == null;
    }
}
