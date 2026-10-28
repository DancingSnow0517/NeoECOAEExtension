package cn.dancingsnow.neoecoae.storage;

public enum ECOStorageInterfaceMode implements net.minecraft.util.StringRepresentable {
    STORAGE("storage"),
    INPUT("input"),
    OUTPUT("output");

    private final String serializedName;

    ECOStorageInterfaceMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
