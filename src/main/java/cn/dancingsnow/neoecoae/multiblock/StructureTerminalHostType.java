package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public enum StructureTerminalHostType {
    CRAFTING,
    STORAGE,
    COMPUTATION;

    public static final StructureTerminalHostType DEFAULT = CRAFTING;
    public static final int DEFAULT_TIER = 1;
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 3;

    public MultiBlockDefinition definitionForTier(int tier) {
        int clampedTier = clampTier(tier);
        return switch (this) {
            case CRAFTING -> switch (clampedTier) {
                case 2 -> NEMultiBlocks.CRAFTING_SYSTEM_L6;
                case 3 -> NEMultiBlocks.CRAFTING_SYSTEM_L9;
                default -> NEMultiBlocks.CRAFTING_SYSTEM_L4;
            };
            case STORAGE -> switch (clampedTier) {
                case 2 -> NEMultiBlocks.STORAGE_SYSTEM_L6;
                case 3 -> NEMultiBlocks.STORAGE_SYSTEM_L9;
                default -> NEMultiBlocks.STORAGE_SYSTEM_L4;
            };
            case COMPUTATION -> switch (clampedTier) {
                case 2 -> NEMultiBlocks.COMPUTATION_SYSTEM_L6;
                case 3 -> NEMultiBlocks.COMPUTATION_SYSTEM_L9;
                default -> NEMultiBlocks.COMPUTATION_SYSTEM_L4;
            };
        };
    }

    public int maxBuildLength(int tier) {
        MultiBlockDefinition definition = definitionForTier(tier);
        return definition == null ? 1 : definition.getExpandMax();
    }

    public static int clampTier(int tier) {
        return Mth.clamp(tier, MIN_TIER, MAX_TIER);
    }

    public static StructureTerminalHostType fromOrdinal(int ordinal) {
        StructureTerminalHostType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DEFAULT;
        }
        return values[ordinal];
    }

    public static StructureTerminalHostType fromName(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        try {
            return StructureTerminalHostType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT;
        }
    }

    public static @Nullable StructureTerminalHostType fromDefinition(@Nullable MultiBlockDefinition definition) {
        if (definition == null) {
            return null;
        }
        if (definition == NEMultiBlocks.CRAFTING_SYSTEM_L4
                || definition == NEMultiBlocks.CRAFTING_SYSTEM_L6
                || definition == NEMultiBlocks.CRAFTING_SYSTEM_L9) {
            return CRAFTING;
        }
        if (definition == NEMultiBlocks.STORAGE_SYSTEM_L4
                || definition == NEMultiBlocks.STORAGE_SYSTEM_L6
                || definition == NEMultiBlocks.STORAGE_SYSTEM_L9) {
            return STORAGE;
        }
        if (definition == NEMultiBlocks.COMPUTATION_SYSTEM_L4
                || definition == NEMultiBlocks.COMPUTATION_SYSTEM_L6
                || definition == NEMultiBlocks.COMPUTATION_SYSTEM_L9) {
            return COMPUTATION;
        }
        return null;
    }

    public static int tierFromDefinition(@Nullable MultiBlockDefinition definition) {
        if (definition == NEMultiBlocks.CRAFTING_SYSTEM_L6
                || definition == NEMultiBlocks.STORAGE_SYSTEM_L6
                || definition == NEMultiBlocks.COMPUTATION_SYSTEM_L6) {
            return 2;
        }
        if (definition == NEMultiBlocks.CRAFTING_SYSTEM_L9
                || definition == NEMultiBlocks.STORAGE_SYSTEM_L9
                || definition == NEMultiBlocks.COMPUTATION_SYSTEM_L9) {
            return 3;
        }
        return DEFAULT_TIER;
    }
}
