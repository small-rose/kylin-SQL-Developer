package com.kylin.plsql.ui.component.left;

import com.kylin.plsql.core.db.ConnectionInfo;

/** 连接树节点持有者，封装 ConnectionInfo 和数据库类型。 */
public class ConnHolder {
    public final ConnectionInfo info;
    public final boolean expanded;
    public String dbType;

    public ConnHolder(ConnectionInfo info, boolean expanded) {
        this.info = info;
        this.expanded = expanded;
    }

    @Override
    public String toString() {
        return info.getName();
    }
}
