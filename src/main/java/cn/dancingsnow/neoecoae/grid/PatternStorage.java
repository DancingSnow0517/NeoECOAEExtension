package cn.dancingsnow.neoecoae.grid;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class PatternStorage implements IECOPatternStorageService, IGridServiceProvider {

    private final Map<IGridNode, IECOPatternStorage> patternStorages = new IdentityHashMap<>();

    public PatternStorage() {}

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

    public boolean tryInsertPattern(ItemStack patternItem) {
        for (IECOPatternStorage value : patternStorages.values()) {
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
