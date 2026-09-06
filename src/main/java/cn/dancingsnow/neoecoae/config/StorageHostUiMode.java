package cn.dancingsnow.neoecoae.config;

import java.util.Locale;

public enum StorageHostUiMode {
    MODERN("modern"),
    LEGACY("legacy");

    private final String id;

    StorageHostUiMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static StorageHostUiMode fromConfig(String value) {
        if (value == null) {
            return MODERN;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (StorageHostUiMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        return MODERN;
    }

    public static boolean isConfigValue(Object value) {
        return value instanceof String string
            && fromConfig(string).id.equals(string.trim().toLowerCase(Locale.ROOT));
    }
}
