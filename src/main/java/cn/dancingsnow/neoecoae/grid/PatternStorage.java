package cn.dancingsnow.neoecoae.grid;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
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

    public ECOPatternInsertionResult tryInsertPattern(ItemStack patternItem) {
        if (patternItem.isEmpty()) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }
        boolean alreadyPresent = false;
        boolean noSpace = false;
        for (IECOPatternStorage value : patternStorages.values()) {
            switch (value.insertPattern(patternItem)) {
                case INSERTED -> {
                    return ECOPatternInsertionResult.INSERTED;
                }
                case ALREADY_PRESENT -> alreadyPresent = true;
                case NO_SPACE -> noSpace = true;
                default -> {}
            }
        }
        if (alreadyPresent) {
            return ECOPatternInsertionResult.ALREADY_PRESENT;
        }
        return noSpace ? ECOPatternInsertionResult.NO_SPACE : ECOPatternInsertionResult.NO_TARGET;
    }

    @Override
    public IECOPatternStorage getPatternStorage() {
        return this::tryInsertPattern;
    }
}
