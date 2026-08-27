package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.Supplier;

public final class ECOMegaChemicalStorageCellItem extends ECOStorageCellItem {
    public ECOMegaChemicalStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, long capacity) {
        super(properties, tier, MekanismKeyType.TYPE, type, capacity,
            MegaCellCapacities.bytesPerType(capacity), MegaCellCapacities.idleDrain(capacity));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        MegaCellTooltips.append(this, lines);
    }

    @Override
    public int getTotalTypes() {
        return MegaCellCapacities.typeLimit(getBytes(), super.getTotalTypes());
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        return !(what instanceof MekanismKey key) || !ChemicalAttributeValidator.DEFAULT.process(key.getStack());
    }
}
