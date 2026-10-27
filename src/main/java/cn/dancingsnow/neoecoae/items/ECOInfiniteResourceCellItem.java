package cn.dancingsnow.neoecoae.items;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * ECO infinite base resource storage matrix: an unbounded supply of a small fixed set of base
 * resources (water, cobblestone and lava).
 *
 * <p>The cell is hard-locked to those keys: {@link #isBlackListed} rejects every other key, so no
 * partition configuration can widen it. It is also permanently excluded from the ECO infinite
 * storage domain via {@link #isInfiniteStorageEligible()}, because migrating an unbounded supply
 * into the domain would erase the distinction between "stored" and "always available".
 */
public class ECOInfiniteResourceCellItem extends ECOStorageCellItem {

    /** The only keys this cell accepts or supplies. */
    private static final List<AEKey> LOCKED_KEYS = List.of(
        AEFluidKey.of(Fluids.WATER),
        AEItemKey.of(Items.COBBLESTONE),
        AEFluidKey.of(Fluids.LAVA)
    );

    private final List<AEKey> suppliedKeys;
    private final Set<AEKeyType> suppliedKeyTypes;
    private final String tooltipGroup;

    /**
     * Installed upgrade slots. Cards are inert here (nothing is stored, so fuzzy/void/inverter have
     * nothing to act on) but the slots are accepted for parity with the other ECO matrices.
     */
    private static final int UPGRADE_SLOTS = 4;

    public ECOInfiniteResourceCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> cellType) {
        this(properties, tier, cellType, LOCKED_KEYS, "tooltip.neoecoae.infinite_resource");
    }

    /** Each resource matrix has its own immutable supply; partition cards cannot widen it. */
    public ECOInfiniteResourceCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> cellType,
            List<AEKey> suppliedKeys, String tooltipGroup) {
        super(
            properties,
            tier,
            AEKeyType.items(),
            cellType,
            Long.MAX_VALUE,
            bytesPerType(tier),
            (double) tier.getStorageTotalBytes() / (1 << 20)
        );
        this.suppliedKeys = List.copyOf(suppliedKeys);
        this.suppliedKeyTypes = suppliedKeys.stream().map(AEKey::getType)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.tooltipGroup = tooltipGroup;
    }

    /** Matches the base cell's per-type cost (1 GiB per type at L9) without overflow. */
    private static int bytesPerType(IECOTier tier) {
        return 1 << (12 + tier.getTier());
    }

    public static List<AEKey> lockedKeys() {
        return LOCKED_KEYS;
    }

    public static boolean isLockedKey(@Nullable AEKey what) {
        return what != null && LOCKED_KEYS.contains(what);
    }

    public List<AEKey> suppliedKeys() {
        return suppliedKeys;
    }

    public boolean suppliesKey(@Nullable AEKey what) {
        return what != null && suppliedKeys.contains(what);
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return suppliedKeyTypes;
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return !suppliesKey(what);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return ConfigInventory.configTypes(this::suppliesKey, suppliedKeys.size(), null);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, UPGRADE_SLOTS);
    }

    @Override
    protected ECOInfiniteResourceCell createCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return new ECOInfiniteResourceCell(stack, host);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> lines, TooltipFlag tooltipFlag) {
        if (ECOInfiniteStorageMember.isMember(stack)) {
            lines.add(Component.translatable("tooltip.neoecoae.storage.infinite_member")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        lines.add(Component.translatable(tooltipGroup + ".contents")
            .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.neoecoae.infinite_resource.unbounded")
            .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(tooltipGroup + ".sink")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
