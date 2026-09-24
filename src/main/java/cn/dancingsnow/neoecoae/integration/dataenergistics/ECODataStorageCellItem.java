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
    private static final long LE4_CAPACITY = 1L << 28;
    private static final long LE6_CAPACITY = 1L << 30;
    private static final long LE9_CAPACITY = 1L << 32;

    private static final ResourceLocation DIGITALIZATION = ResourceLocation.fromNamespaceAndPath(
        "data_energistics", "digitalization");
    private static final ResourceLocation MANIFEST_BINARY = ResourceLocation.fromNamespaceAndPath(
        "data_energistics", "manifest_binary");

    public ECODataStorageCellItem(Properties properties, IECOTier tier) {
        this(properties, tier, capacityFor(tier));
    }

    private ECODataStorageCellItem(Properties properties, IECOTier tier, long totalBytes) {
        // Both upstream channels store eight units per byte. Resolve after key registration.
        super(
            properties,
            tier,
            () -> AEKeyTypes.get(DIGITALIZATION),
            NEDataCellTypes.DATA,
            totalBytes,
            1 << (12 + tier.getTier()),
            (double) tier.getStorageTotalBytes() / (1 << 20)
        );
    }

    private static long capacityFor(IECOTier tier) {
        return switch (tier.getTier()) {
            case 1 -> LE4_CAPACITY;
            case 2 -> LE6_CAPACITY;
            case 3 -> LE9_CAPACITY;
            default -> throw new IllegalArgumentException("Unsupported ECO data cell tier: " + tier.getTier());
        };
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
