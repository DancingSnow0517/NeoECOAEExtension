package cn.dancingsnow.neoecoae.mixins;

import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CraftingPlan.class)
public class CraftingPlanMixin implements ECOCraftingPlanDiagnostics {
    @Unique private ECOPlanningResult neoecoae$planningResult;
    @Override @Nullable public ECOPlanningResult neoecoae$getPlanningResult() { return neoecoae$planningResult; }
    @Override public void neoecoae$setPlanningResult(@Nullable ECOPlanningResult result) { neoecoae$planningResult = result; }
}
