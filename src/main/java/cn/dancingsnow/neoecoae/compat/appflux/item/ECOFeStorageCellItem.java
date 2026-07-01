package cn.dancingsnow.neoecoae.compat.appflux.item;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.compat.appflux.AppFluxCompat;
import cn.dancingsnow.neoecoae.compat.appflux.NEAppFluxCellTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ECOFeStorageCellItem extends ECOStorageCellItem {
    public ECOFeStorageCellItem(Properties properties, IECOTier tier) {
        super(properties, tier, AppFluxCompat.getFluxKeyType(), NEAppFluxCellTypes.FE);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
            List<Component> lines,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, lines, tooltipFlag);
        var handler = getCellInventory(stack);
        if (handler == null) {
            return;
        }
        for (Object2LongMap.Entry<AEKey> entry : handler.getAvailableStacks()) {
            if (entry.getKey() instanceof FluxKey fluxKey) {
                EnergyType type = fluxKey.getEnergyType();
                lines.add(Component.translatable(
                        "appflux.cell.storage",
                        appeng.core.localization.Tooltips.ofNumber(entry.getLongValue()),
                        type.translate()));
            }
        }
    }
}
