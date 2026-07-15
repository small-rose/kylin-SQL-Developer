package com.kylin.plsql.core.format;

import java.util.ArrayList;
import java.util.List;

public class EngineManager {

    private static final List<SqlFormatterEngine> engines = new ArrayList<>();
    private static int currentIndex = 0;

    public static void initEngines(FormatOptions options) {
        engines.clear();
        engines.add(new JsqlFormatterEngine());
        engines.add(new SimpleFormatterEngine(options));
        engines.add(new AdvancedFormatterEngine(options));
        currentIndex = 0;
    }

    public static List<SqlFormatterEngine> getEngines() {
        return new ArrayList<>(engines);
    }

    public static SqlFormatterEngine getCurrent() {
        if (engines.isEmpty()) return null;
        return engines.get(currentIndex);
    }

    public static void setCurrent(int index) {
        if (index >= 0 && index < engines.size()) currentIndex = index;
    }

    public static void setCurrentByName(String name) {
        for (int i = 0; i < engines.size(); i++) {
            if (engines.get(i).getName().equals(name)) {
                currentIndex = i;
                return;
            }
        }
    }

    public static String format(String sql) throws Exception {
        SqlFormatterEngine engine = getCurrent();
        if (engine == null) throw new Exception("No available formatting engine");
        return engine.format(sql);
    }
}
