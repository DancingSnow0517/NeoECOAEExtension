package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.PlayerSource;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.compat.extendedae.ExtendedAEPlusCraftingPlanCompat;
import cn.dancingsnow.neoecoae.impl.crafting.execution.ECOFuzzyCraftingInventory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOPlannedInputs;
import cn.dancingsnow.neoecoae.network.ECOSubmissionMissingPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftConfirmMenu.class)
public abstract class CraftConfirmMenuMixin {
    @Shadow
    @Nullable
    private ICraftingPlan result;

    @Inject(method = "planJob", at = @At("HEAD"))
    private void neoecoae$clearSubmissionAudit(CallbackInfoReturnable<Boolean> cir) {
        neoecoae$sendMissing(Map.of());
    }

    @Inject(method = "startJob", at = @At("TAIL"))
    private void neoecoae$syncSubmissionAudit(CallbackInfo ci) {
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        var submitResult = menu.submitError.result();
        if (result == null
            || submitResult == null
            || submitResult.errorCode() != CraftingSubmitErrorCode.MISSING_INGREDIENT
            || !(menu.getHost() instanceof IActionHost actionHost)
            || actionHost.getActionableNode() == null
            || actionHost.getActionableNode().getGrid() == null) {
            neoecoae$sendMissing(Map.of());
            return;
        }

        var grid = actionHost.getActionableNode().getGrid();
        var source = new PlayerSource(menu.getPlayer(), actionHost);
        ICraftingPlan plannedInputPlan = ExtendedAEPlusCraftingPlanCompat.unwrap(result);
        Map<AEKey, Long> missing = new LinkedHashMap<>(ECOFuzzyCraftingInventory.auditMissingInitialItems(
            result,
            grid,
            source,
            ECOPlannedInputs.peekFuzzyItemIds(plannedInputPlan)
        ));
        if (submitResult.errorDetail() instanceof GenericStack firstMissing) {
            missing.merge(firstMissing.what(), firstMissing.amount(), Math::max);
        }
        neoecoae$sendMissing(missing);
    }

    private void neoecoae$sendMissing(Map<AEKey, Long> missing) {
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        if (menu.getPlayer() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(
                player,
                new ECOSubmissionMissingPayload(menu.containerId, missing)
            );
        }
    }
}
