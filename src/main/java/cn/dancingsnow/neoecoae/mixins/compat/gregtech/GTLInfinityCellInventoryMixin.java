package cn.dancingsnow.neoecoae.mixins.compat.gregtech;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.SparseExactAmountSource;
import java.math.BigInteger;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Shares GTL's public physical-quantity API with ECO's existing filtered inventory collector. */
@Pseudo
@Mixin(targets = {
    "org.gtlcore.gtlcore.integration.ae2.storage.FastInfinityCellInventory",
    "org.gtlcore.gtlcore.integration.ae2.storage.InfinityCellInventory"
}, remap = false)
public abstract class GTLInfinityCellInventoryMixin implements MEStorage, SparseExactAmountSource {
    @Shadow(remap = false)
    public abstract BigInteger getExactStoredAmount(AEKey key);

    @Override
    public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
        for (var entry : getAvailableStacks()) {
            visitor.accept(entry.getKey(), ExactAmount.finite(getExactStoredAmount(entry.getKey())));
        }
    }

    @Override
    public void neoecoae$visitExceptionalAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
        for (var entry : getAvailableStacks()) {
            // GTL saturates both Int128 and BigInteger at the long boundary. Ordinary values need no
            // exact conversion, and enumerating only the native listing preserves its visible key set.
            if (entry.getLongValue() != Long.MAX_VALUE && entry.getLongValue() >= 0) continue;
            ExactAmount amount = neoecoae$exceptionalAmount(entry.getKey());
            if (amount != null) visitor.accept(entry.getKey(), amount);
        }
    }

    @Override
    public @Nullable ExactAmount neoecoae$exceptionalAmount(AEKey key) {
        BigInteger amount = getExactStoredAmount(key);
        return amount.signum() >= 0 && amount.bitLength() >= Long.SIZE ? ExactAmount.finite(amount) : null;
    }
}
