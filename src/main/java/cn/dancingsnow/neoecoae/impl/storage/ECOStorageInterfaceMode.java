package cn.dancingsnow.neoecoae.impl.storage;

public enum ECOStorageInterfaceMode {
    STORAGE,
    INPUT,
    OUTPUT;

    public ECOStorageInterfaceMode next() {
        return switch (this) {
            case STORAGE -> INPUT;
            case INPUT -> OUTPUT;
            case OUTPUT -> STORAGE;
        };
    }

    public static ECOStorageInterfaceMode byName(String name) {
        for (ECOStorageInterfaceMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return STORAGE;
    }
}
