package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.backend.MegaCellsBackend;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public final class ECORadioactiveChemicalStorageCellItem extends ECOStorageCellItem {
    public ECORadioactiveChemicalStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type) {
        super(properties, tier, MekanismKeyType.TYPE, type, tier.getStorageTotalBytes(),
            1 << (12 + tier.getTier()), 250.0 * tier.getStorageTotalBytes() / (1L << 24));
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return !MegaCellsBackend.acceptsRadioactiveChemical(what);
    }
}
