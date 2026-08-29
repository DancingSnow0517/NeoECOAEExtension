package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCalculationSettings;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingCalculation.class)
public class CraftingCalculationMixin implements ECOCraftingCalculationSettings {
    @Unique
    private boolean neoecoae$ignorePatternSubstitutions;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureNetworkPlanningMode(
        Level level,
        IGrid grid,
        ICraftingSimulationRequester simRequester,
        GenericStack output,
        CalculationStrategy strategy,
        CallbackInfo ci
    ) {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(grid);
        this.neoecoae$ignorePatternSubstitutions = settings != null
            && settings.neoecoae$isIgnoringPatternSubstitutions();
    }

    @Override
    public boolean neoecoae$isIgnoringPatternSubstitutions() {
        return neoecoae$ignorePatternSubstitutions;
    }
}
