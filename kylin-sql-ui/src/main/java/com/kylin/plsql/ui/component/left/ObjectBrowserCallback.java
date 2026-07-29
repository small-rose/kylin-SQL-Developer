package com.kylin.plsql.ui.component.left;

/** 对象浏览器回调接口，定义对象操作、SQL 编辑器、连接管理、源对象查看、同步进度等回调。 */
public interface ObjectBrowserCallback {
    void onObjectAction(String connName, String schema, String objectType, String objectName, String action);
    void onNewSqlEditor(String connName);
    void onNewSqlEditor(String connName, String schema);
    void onOpenConnections();
    void onConnectionProperties(String connName);
    void onOpenSourceObject(String connName, String schema, String objectType, String objectName);
    void onSyncProgress(String connName, int percent);
    void onSyncComplete(String connName);
    void onSyncError(String connName, String message);
    void onExecuteScript(String connName, String schema);
}
