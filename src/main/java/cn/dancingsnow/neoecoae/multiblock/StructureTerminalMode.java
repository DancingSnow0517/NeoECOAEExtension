package cn.dancingsnow.neoecoae.multiblock;

public enum StructureTerminalMode {
    BUILD,
    MIRRORED_BUILD,
    DISMANTLE;

    public static StructureTerminalMode fromName(String name) {
        for (StructureTerminalMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return BUILD;
    }
}
