package cn.dancingsnow.neoecoae.mixins.ae2.accessor;

import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CraftingPlanSummary.class)
public interface CraftingPlanSummaryAccessor {
    @Mutable
    @Accessor("entries")
    void neoecoae$setEntries(List<CraftingPlanSummaryEntry> entries);
}
