package com.os4.musiccover;

import java.util.LinkedHashMap;
import java.util.Map;

/** A bounded per-scene hint; only sampled foreground choices, never captured pixels. */
final class NavigationContrastCache {
    private static final int MAX_SCENES = 8;
    private static final int BLACK = 0xff000000, WHITE = 0xffffffff;
    private final LinkedHashMap<String, Integer> colors = new LinkedHashMap<>(8, 0.75f, true);

    int get(String scene) {
        if (scene == null) return 0;
        Integer color = colors.get(scene);
        return color == null ? 0 : color;
    }

    void remember(String scene, int color) {
        if (scene == null || scene.isEmpty() || (color != BLACK && color != WHITE)) return;
        colors.put(scene, color);
        if (colors.size() > MAX_SCENES) colors.remove(colors.keySet().iterator().next());
    }

    /** Only framework-owned values can pass across the module's class loaders. */
    Object[] save() {
        Object[] values = new Object[colors.size() * 2];
        int i = 0;
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
            values[i++] = entry.getKey();
            values[i++] = entry.getValue();
        }
        return values;
    }

    void adopt(Object[] values) {
        colors.clear();
        if (values == null) return;
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i] instanceof String && values[i + 1] instanceof Integer)
                remember((String) values[i], (Integer) values[i + 1]);
        }
    }
}
