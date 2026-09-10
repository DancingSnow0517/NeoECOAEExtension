package cn.dancingsnow.neoecoae.mixins;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftAmountMenu;
import org.gtlcore.gtlcore.integration.ae2.common.ILongCraftAmountMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets GTLCore's quantity screen hand a request to an AE2 menu from this mod. */
@Mixin(value = CraftAmountMenu.class, priority = 1100, remap = false)
public abstract class GTLCoreCraftAmountMenuMixin implements ILongCraftAmountMenu {
    @Shadow private AEKey whatToCraft;

    @Override
    public void gtlcore$confirmLongAmount(long amount, boolean craftMissingAmount, boolean startImmediately) {
        if (amount <= 0L) return;
        // AE2's native menu stores an int amount. GTLCore's transfinite CPU
        // handles true long requests in its own menu mixin; this fallback keeps
        // normal requests functional when that optional mixin is absent.
        int legacyAmount = amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        ((CraftAmountMenu) (Object) this).confirm(legacyAmount, craftMissingAmount, startImmediately);
    }

    @Override
    public void gtlcore$setLongWhatToCraft(AEKey whatToCraft, long amount) {
        this.whatToCraft = whatToCraft;
    }
}
