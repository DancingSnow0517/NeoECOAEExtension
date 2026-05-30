package cn.dancingsnow.neoecoae.compat.appmek.item;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.compat.appmek.AppMekCompat;
import cn.dancingsnow.neoecoae.compat.appmek.NEAppMekCellTypes;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import net.minecraft.world.item.ItemStack;

/**
 * ECO storage cell for Applied Mekanistics chemical types
 * (gases, infuse types, pigments, slurries).
 */
public class ECOChemicalStorageCellItem extends ECOStorageCellItem {

    public ECOChemicalStorageCellItem(Properties properties, IECOTier tier) {
        super(properties, tier, AppMekCompat.getChemicalKeyType(), NEAppMekCellTypes.CHEMICAL);
    }

    @Override
    public boolean isBlackListed(ItemStack cellStack, AEKey what) {
        if (what instanceof MekanismKey key) {
            return !ChemicalAttributeValidator.DEFAULT.process(key.getStack());
        }
        return true;
    }
}
