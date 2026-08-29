package cn.dancingsnow.neoecoae.mixins;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCalculationSettings;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CraftingTreeNode.class)
public abstract class CraftingTreeNodeMixin {
    @Shadow
    @Final
    private CraftingCalculation job;

    @Shadow
    @Final
    @Nullable
    IPatternDetails.IInput parentInput;

    @Inject(method = "findCraftedStack", at = @At("HEAD"), cancellable = true)
    private void keepPrimaryCraftedStack(
        ICraftingService craftingService,
        AEKey requestedKey,
        CallbackInfoReturnable<AEKey> cir
    ) {
        if (neoecoae$ignoresPatternSubstitutions()) {
            cir.setReturnValue(requestedKey);
        }
    }

    @Inject(method = "getValidItemTemplates", at = @At("HEAD"), cancellable = true)
    private void useOnlyPrimaryInput(
        ICraftingInventory inventory,
        CallbackInfoReturnable<Iterable<InputTemplate>> cir
    ) {
        if (parentInput == null || !neoecoae$ignoresPatternSubstitutions()) {
            return;
        }
        var primaryInput = parentInput.getPossibleInputs()[0];
        cir.setReturnValue(List.of(new InputTemplate(primaryInput.what(), primaryInput.amount())));
    }

    @Unique
    private boolean neoecoae$ignoresPatternSubstitutions() {
        return ((ECOCraftingCalculationSettings) (Object) job).neoecoae$isIgnoringPatternSubstitutions();
    }
}
