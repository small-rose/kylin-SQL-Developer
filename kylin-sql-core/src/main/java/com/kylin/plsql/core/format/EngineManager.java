package com.kylin.plsql.core.format;

import java.util.ArrayList;
import java.util.List;

public class EngineManager {

    private static final List<SqlFormatterEngine> engines = new ArrayList<>();
    private static int currentIndex = 0;

    static {
        engines.add(new JsqlFormatterEngine());
    }

    public static void registerCustomEngine(FormatOptions options) {
        if (engines.size() > 1 && engines.get(1) instanceof CustomFormatterEngine) {
            engines.set(1, new CustomFormatterEngine(options));
        } else {
            engines.add(new CustomFormatterEngine(options));
        }
        if (currentIndex >= engines.size()) currentIndex = 0;
    }

    public static List<SqlFormatterEngine> getEngines() {
        return new ArrayList<>(engines);
    }

    public static SqlFormatterEngine getCurrent() {
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
        return getCurrent().format(sql);
    }
}