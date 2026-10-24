package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ISaveProvider;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.List;

final class BiggerAE2StorageCellItem extends ECOStorageCellItem {
    static final long QUANTUM_BYTES = 1L << 27;
    private final BiggerAE2Channel channel;
    private final boolean singularity;

    BiggerAE2StorageCellItem(Properties properties, BiggerAE2Channel channel, boolean singularity,
            Supplier<ECOCellType> cellType) {
        super(properties.rarity(singularity ? Rarity.EPIC : Rarity.RARE),
            singularity ? ECOTier.L9 : ECOTier.L6, channel.keyType, cellType,
            singularity ? Long.MAX_VALUE : QUANTUM_BYTES, 65_536,
            singularity ? BiggerAE2SingularityPowerService.BASE_IDLE_DRAIN : 20);
        this.channel = channel;
        this.singularity = singularity;
    }

    boolean isSingularity() {
        return singularity;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(key -> getKeyTypes().contains(key.getType()), stack, channel.typeLimit);
    }

    @Override
    public boolean isBlackListed(ItemStack stack, AEKey what) {
        return channel == BiggerAE2Channel.CHEMICAL && !BiggerAE2ChemicalValidation.accepts(what);
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return singularity ? new BiggerAE2SingularityStorageCell(stack, host) : super.createCellInventory(stack, host);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        if (singularity) {
            for (var entry : BiggerAE2SingularityContents.read(stack).entries()) {
                if (entry.amount().bitLength() > 63) {
                    lines.add(entry.key().getDisplayName().copy().append(": " + entry.amount()));
                }
            }
        }
    }
}
