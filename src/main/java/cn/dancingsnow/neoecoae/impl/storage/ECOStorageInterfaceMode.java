package cn.dancingsnow.neoecoae.impl.storage;

public enum ECOStorageInterfaceMode {
    STORAGE,
    OUTPUT;

    public ECOStorageInterfaceMode next() {
        return this == STORAGE ? OUTPUT : STORAGE;
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
