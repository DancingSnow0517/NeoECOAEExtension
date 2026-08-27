package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.Supplier;

public final class ECOMegaEnergyStorageCellItem extends ECOStorageCellItem {
    public ECOMegaEnergyStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(properties, tier, FluxKeyType.TYPE, type, capacity,
            MegaCellCapacities.bytesPerType(capacity), MegaCellCapacities.idleDrain(capacity));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        MegaCellTooltips.append(this, lines);
        var inventory = getCellInventory(stack);
        if (inventory == null) return;
        KeyCounter available = inventory.getAvailableStacks();
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (entry.getKey() instanceof FluxKey fluxKey) {
                lines.add(Component.translatable("appflux.cell.storage", Tooltips.ofNumber(entry.getLongValue()),
                    fluxKey.getEnergyType().translate()));
            }
        }
    }
}
