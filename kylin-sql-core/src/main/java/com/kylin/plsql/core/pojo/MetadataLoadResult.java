package com.kylin.plsql.core.pojo;

import java.util.List;

/** 元数据加载结果，封装数据库产品类型、全量 Schema、隐藏 Schema 等信息。 */

import java.util.Map;
import java.util.Set;

public class MetadataLoadResult {
    public final String dbProduct;
    public final List<String> schemas;
    public final Map<String, Set<String>> hiddenSchemas;

    public MetadataLoadResult(String dbProduct, List<String> schemas, Map<String, Set<String>> hiddenSchemas) {
        this.dbProduct = dbProduct;
        this.schemas = schemas;
        this.hiddenSchemas = hiddenSchemas;
    }
}
