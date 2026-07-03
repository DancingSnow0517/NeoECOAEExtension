package cn.dancingsnow.neoecoae.impl.storage.infinite;

public enum ECOStorageHostMode {
    UNFORMED("unformed"),
    FORMED_NORMAL("formed_normal"),
    MIGRATING_TO_INFINITE("migrating_to_infinite"),
    FORMED_INFINITE("formed_infinite");

    private final String id;

    ECOStorageHostMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ECOStorageHostMode fromId(String id) {
        for (ECOStorageHostMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return UNFORMED;
    }

    public boolean isInfiniteState() {
        return this == MIGRATING_TO_INFINITE || this == FORMED_INFINITE;
    }
}
