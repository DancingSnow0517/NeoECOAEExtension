package com.lowdragmc.lowdraglib2.syncdata.holder;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public interface ISyncMangedHolder {
    default String getSyncTag() {
        return "ldlib2_sync";
    }

    default CompoundTag serializeInitialData(HolderLookup.Provider registries) {
        return new CompoundTag();
    }
}
