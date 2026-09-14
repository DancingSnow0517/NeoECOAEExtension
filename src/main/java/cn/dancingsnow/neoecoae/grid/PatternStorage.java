package cn.dancingsnow.neoecoae.grid;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import com.google.gson.stream.JsonWriter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

public class PatternStorage implements IECOPatternStorageService, IGridServiceProvider {

    private final Map<IGridNode, IECOPatternStorage> patternStorages = new IdentityHashMap<>();

    public PatternStorage() {

    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        IGridServiceProvider.super.addNode(gridNode, savedData);
        IECOPatternStorage service = gridNode.getService(IECOPatternStorage.class);
        if (service == null) return;
        patternStorages.put(gridNode, service);
    }

    @Override
    public void removeNode(IGridNode gridNode) {
        IGridServiceProvider.super.removeNode(gridNode);
        patternStorages.remove(gridNode);
    }

    @Override
    public void debugDump(JsonWriter writer, HolderLookup.Provider registries) throws IOException {
        IGridServiceProvider.super.debugDump(writer, registries);
    }

    public boolean tryInsertPattern(ItemStack patternItem) {
        // Pattern disks take priority across the whole grid. The storage map is an IdentityHashMap
        // (unordered), so a single pass would let an arbitrary disk-less storage swallow the pattern
        // into a slot before a storage that owns a disk is ever asked.
        //
        // A storage that reported disk space is dropped from the second pass: insertPattern returns
        // false both for "nothing inserted" and for "partially inserted", so asking again could
        // push the remainder of a stack that pass one already consumed.
        List<IECOPatternStorage> attemptedDisks = null;
        for (IECOPatternStorage value : patternStorages.values()) {
            if (!value.canInsertIntoDisk(patternItem)) {
                continue;
            }
            if (value.insertPattern(patternItem)) {
                return true;
            }
            if (attemptedDisks == null) {
                attemptedDisks = new ArrayList<>();
            }
            attemptedDisks.add(value);
        }
        for (IECOPatternStorage value : patternStorages.values()) {
            if (attemptedDisks != null && attemptedDisks.contains(value)) {
                continue;
            }
            if (value.insertPattern(patternItem)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IECOPatternStorage getPatternStorage() {
        return this::tryInsertPattern;
    }
}
