package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.List;
import java.util.NavigableMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = NetworkStorage.class, remap = false)
public interface NetworkStorageAccessor {
    @Accessor("priorityInventory")
    NavigableMap<Integer, List<MEStorage>> neoecoae$getMountedInventories();
}
