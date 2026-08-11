package cn.dancingsnow.neoecoae.grid;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.IECOPatternStorage;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public class PatternStorage implements IECOPatternStorageService, IGridServiceProvider {

    private final Map<IGridNode, IECOPatternStorage> patternStorages = new IdentityHashMap<>();
    private final IECOPatternStorage combinedStorage = this::tryInsertPattern;
    @Nullable
    private IECOPatternStorage preferredStorage;

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
        IECOPatternStorage removed = patternStorages.remove(gridNode);
        if (removed == preferredStorage) {
            preferredStorage = null;
        }
    }

    public ECOPatternInsertionResult tryInsertPattern(ItemStack patternItem) {
        if (patternItem.isEmpty()) {
            return ECOPatternInsertionResult.INCOMPATIBLE;
        }

        boolean noSpace = false;
        boolean uniquenessChecked = false;
        if (preferredStorage != null) {
            ECOPatternInsertionResult result = preferredStorage.insertPattern(patternItem);
            switch (result) {
                case INSERTED -> {
                    return ECOPatternInsertionResult.INSERTED;
                }
                case ALREADY_PRESENT -> {
                    return ECOPatternInsertionResult.ALREADY_PRESENT;
                }
                case NO_SPACE -> {
                    noSpace = true;
                    uniquenessChecked = preferredStorage.checksLogicalDomainForDuplicates();
                }
                default -> {
                }
            }
        }
        for (IECOPatternStorage value : patternStorages.values()) {
            if (value == preferredStorage) {
                continue;
            }
            ECOPatternInsertionResult result = uniquenessChecked
                ? value.insertPatternKnownUnique(patternItem)
                : value.insertPattern(patternItem);
            switch (result) {
                case INSERTED -> {
                    preferredStorage = value;
                    return ECOPatternInsertionResult.INSERTED;
                }
                case ALREADY_PRESENT -> {
                    return ECOPatternInsertionResult.ALREADY_PRESENT;
                }
                case NO_SPACE -> {
                    noSpace = true;
                    if (value.checksLogicalDomainForDuplicates()) {
                        // This target checked the entire logical domain before reporting NO_SPACE.
                        // All subsequent targets can therefore use the no-duplicate fast path.
                        uniquenessChecked = true;
                    }
                }
                default -> {
                }
            }
        }
        return noSpace ? ECOPatternInsertionResult.NO_SPACE : ECOPatternInsertionResult.NO_TARGET;
    }

    @Override
    public IECOPatternStorage getPatternStorage() {
        return combinedStorage;
    }
}
