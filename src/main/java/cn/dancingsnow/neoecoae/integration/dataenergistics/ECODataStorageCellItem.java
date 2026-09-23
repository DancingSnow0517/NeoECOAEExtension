package cn.dancingsnow.neoecoae.integration.dataenergistics;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class ECODataStorageCellItem extends ECOStorageCellItem {
    private static final ResourceLocation DIGITALIZATION = ResourceLocation.fromNamespaceAndPath(
        "data_energistics", "digitalization");
    private static final ResourceLocation MANIFEST_BINARY = ResourceLocation.fromNamespaceAndPath(
        "data_energistics", "manifest_binary");

    public ECODataStorageCellItem(Properties properties, IECOTier tier) {
        // Both upstream channels store eight units per byte. Resolve after key registration.
        super(properties, tier, () -> AEKeyTypes.get(DIGITALIZATION), NEDataCellTypes.DATA);
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return Set.of(AEKeyTypes.get(DIGITALIZATION), AEKeyTypes.get(MANIFEST_BINARY));
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return !supportsDataKey(what);
    }

    static boolean supportsDataKey(AEKey key) {
        if (key == null) return false;
        AEKeyType type = key.getType();
        return (type == AEKeyTypes.get(DIGITALIZATION) || type == AEKeyTypes.get(MANIFEST_BINARY))
            && type.contains(key);
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return new ECOStorageCell(stack, host) {
            @Override
            protected boolean acceptsKey(AEKey key) {
                return supportsDataKey(key);
            }
        };
    }
}
