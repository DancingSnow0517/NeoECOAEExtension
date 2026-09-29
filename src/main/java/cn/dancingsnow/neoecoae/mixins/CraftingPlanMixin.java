package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuSupport;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CraftingPlan.class)
public class CraftingPlanMixin implements ECOCraftingPlanDiagnostics, ECOExternalCpuSupport.OwnedPlan {
    @Unique private ECOPlanningResult neoecoae$planningResult;

    @Unique private boolean neoecoae$ecoOwned;

    @Override
    @Nullable public ECOPlanningResult neoecoae$getPlanningResult() {
        return neoecoae$planningResult;
    }

    @Override
    public void neoecoae$setPlanningResult(@Nullable ECOPlanningResult result) {
        neoecoae$planningResult = result;
        if (result != null
                && result.plan() != null
                && PlanIdentity.matches((ICraftingPlan) (Object) this, result.plan())) {
            neoecoae$ecoOwned = true;
        }
    }

    @Override
    public boolean neoecoae$isECOOwnedPlan() {
        return neoecoae$ecoOwned;
    }
}
