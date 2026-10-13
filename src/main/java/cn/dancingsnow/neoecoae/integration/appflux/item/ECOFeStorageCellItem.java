package cn.dancingsnow.neoecoae.integration.appflux.item;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.FluxKeyType;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class ECOFeStorageCellItem extends ECOStorageCellItem {
    public ECOFeStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> cellType) {
        super(properties, tier, FluxKeyType.TYPE, cellType);
    }

    @Override
    protected void appendCellLines(ItemStack stack, @Nullable ECOStorageCell handler, List<Component> lines) {
        super.appendCellLines(stack, handler, lines);
        if (handler == null) {
            return;
        }
        KeyCounter availableStacks = handler.getAvailableStacks();
        for (Object2LongMap.Entry<AEKey> entry : availableStacks) {
            if (entry.getKey() instanceof FluxKey fluxKey) {
                lines.add(Component.translatable("appflux.cell.storage", Tooltips.ofNumber(entry.getLongValue()), fluxKey.getEnergyType().translate()));
            }
        }
    }
}
