package cn.dancingsnow.neoecoae.integration.appmek.item;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.integration.appmek.NEAppMekCellTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import net.minecraft.world.item.ItemStack;

public class ECOChemicalStorageCellItem extends ECOStorageCellItem {
    public ECOChemicalStorageCellItem(Properties properties, IECOTier tier) {
        super(properties, tier, MekanismKeyType.TYPE, NEAppMekCellTypes.MEKANISM);
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        if (what instanceof MekanismKey key) {
            return !ChemicalAttributeValidator.DEFAULT.process(key.getStack());
        }
        return true;
    }
}
