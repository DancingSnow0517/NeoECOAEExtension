package com.lowdragmc.lowdraglib2.syncdata.holder.blockentity;

import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;

public interface ISyncPersistRPCBlockEntity extends ISyncMangedHolder {
    default IManagedStorage getRootStorage() {
        return null;
    }
}
