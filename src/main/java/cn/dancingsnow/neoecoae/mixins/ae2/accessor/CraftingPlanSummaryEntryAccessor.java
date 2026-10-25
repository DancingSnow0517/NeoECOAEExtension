package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Updates shared quantities without reconstructing a fork's additional summary fields. */
@Mixin(value = CraftingPlanSummaryEntry.class, remap = false)
public interface CraftingPlanSummaryEntryAccessor {
    @Mutable
    @Accessor("missingAmount")
    void neoecoae$setMissingAmount(long amount);

    @Mutable
    @Accessor("storedAmount")
    void neoecoae$setStoredAmount(long amount);
}
