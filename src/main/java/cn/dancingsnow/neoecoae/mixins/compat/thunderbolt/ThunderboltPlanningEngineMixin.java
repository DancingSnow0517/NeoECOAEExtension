package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ECOThunderboltPlanningBridge;
import com.moakiee.thunderbolt.api.crafting.CraftingPlanningEngine;
import com.moakiee.thunderbolt.api.crafting.PlanningAttemptContext;
import com.moakiee.thunderbolt.api.crafting.PlanningRequest;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = {
    "com.moakiee.thunderbolt.core.crafting.planner.ThunderboltV2PlanningEngine",
    "com.moakiee.thunderbolt.core.crafting.planner.CpSatPlanningEngine"
}, remap = false)
public abstract class ThunderboltPlanningEngineMixin implements CraftingPlanningEngine {
    @Override
    public Object capture(IGrid grid, PlanningRequest request) {
        return ECOThunderboltPlanningBridge.capture(request.craftingService());
    }

    @ModifyVariable(method = "createSession", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private PlanningRequest neoecoae$useCapturedComponents(PlanningRequest value, PlanningRequest request,
            Object capturedInput, PlanningAttemptContext context) {
        if (!(capturedInput instanceof Set<?> captured)) return value;
        Set<ResourceLocation> items = captured.stream().map(ResourceLocation.class::cast)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var service = ECOThunderboltPlanningBridge.forCalculation(value.craftingService(), items);
        return service == value.craftingService() ? value : new PlanningRequest(value.level(), service,
            value.networkInventory(), value.output(), value.requestedAmount(), value.strategy(), value.requester());
    }
}
