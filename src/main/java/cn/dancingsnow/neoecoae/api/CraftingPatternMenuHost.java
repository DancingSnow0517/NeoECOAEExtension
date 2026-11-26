package cn.dancingsnow.neoecoae.api;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import org.jetbrains.annotations.Nullable;

/** Implemented by a world block entity that hosts a separate crafting interface at its position. */
public interface CraftingPatternMenuHost {
    /** Returns the same interface instance for the lifetime of its menus, on either logical side. */
    @Nullable ECOMachineInterfaceBlockEntity<?> getCraftingPatternMenuInterface();

    /** Server-side snapshot compared by value. Change it when existing slot addresses become stale. */
    default @Nullable Object getCraftingPatternMenuLayoutKey() {
        return null;
    }
}
