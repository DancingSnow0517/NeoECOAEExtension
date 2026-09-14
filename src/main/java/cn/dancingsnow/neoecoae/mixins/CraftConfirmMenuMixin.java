package cn.dancingsnow.neoecoae.mixins;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.ECOCycleItemList;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftConfirmMenu.class, remap = false)
public class CraftConfirmMenuMixin implements ECOCraftConfirmMenuMode {
    @Unique private static final Logger NEOECOAE_LOGGER = LoggerFactory.getLogger("neoecoae");

    @Unique @GuiSync(99)
    private boolean neoecoae$showFastPlannerReport;

    @Unique @GuiSync(104)
    private boolean neoecoae$cyclePlanningEnabled;

    @Unique @GuiSync(100)
    private long neoecoae$calculationNanos;

    @Unique @GuiSync(105)
    private String neoecoae$theoreticalBytes = "0";

    /** Zero means absent; otherwise this is {@code PlanningStatus.ordinal() + 1}. */
    @Unique @GuiSync(101)
    private int neoecoae$planningStatusCode;

    @Unique @GuiSync(102)
    public ECOCycleItemList neoecoae$cycleItems = ECOCycleItemList.EMPTY;

    @Unique @GuiSync(103)
    public CraftingGraphSnapshot neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;

    /** Server-side result paired with the plan whose confirmation page the player actually saw. */
    @Unique private @Nullable ECOPlanningResult neoecoae$confirmedPlanningResult;

    @Shadow
    private ICraftingPlan result;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureFastPlannerMode(int id, Inventory inventory, ISubMenuHost host, CallbackInfo ci) {
        if (inventory.player.level().isClientSide() || !(host instanceof IActionHost actionHost)) {
            return;
        }
        var node = actionHost.getActionableNode();
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(node == null ? null : node.getGrid());
        neoecoae$showFastPlannerReport = settings != null && settings.neoecoae$shouldUseFastPlanner();
        neoecoae$cyclePlanningEnabled = settings != null && settings.neoecoae$isCyclePlanningEnabled();
    }

    @Inject(method = "planJob", at = @At("HEAD"))
    private void resetPlannerDiagnostics(
            AEKey what, int amount, CalculationStrategy strategy, CallbackInfoReturnable<Boolean> cir) {
        neoecoae$calculationNanos = 0;
        neoecoae$theoreticalBytes = "0";
        neoecoae$planningStatusCode = 0;
        neoecoae$cycleItems = ECOCycleItemList.EMPTY;
        neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;
        neoecoae$confirmedPlanningResult = null;
    }

    // This Minecraft override uses its SRG name in production; AE2-owned methods remain unmapped.
    @Inject(
            method = {"broadcastChanges()V", "m_38946_()V"},
            at = @At("TAIL"),
            require = 1)
    private void capturePlannerDiagnostics(CallbackInfo ci) {
        ECOPlanningResult planningResult = result instanceof ECOCraftingPlanDiagnostics diagnostics
                ? diagnostics.neoecoae$getPlanningResult()
                : null;
        if (planningResult == null) {
            planningResult = ECOPlanningResultRegistry.find(result);
        }
        if (planningResult == null || planningResult == neoecoae$confirmedPlanningResult) {
            return;
        }

        neoecoae$confirmedPlanningResult = planningResult;
        neoecoae$calculationNanos = planningResult.calculationNanos();
        neoecoae$theoreticalBytes = planningResult.theoreticalBytes().toString();
        neoecoae$planningStatusCode = planningResult.status().ordinal() + 1;
        CraftingGraphSnapshot snapshot = CraftingGraphSnapshotFactory.create(planningResult);
        neoecoae$craftingGraph = snapshot;
        LinkedHashMap<AEKey, ECOCycleItemList.Entry> cycleItems = new LinkedHashMap<>();
        for (var cycle : snapshot.cycleGroups()) {
            LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
            cycle.exactSingleNetOutputs().forEach(value -> keys.add(value.key()));
            cycle.exactTotalNetOutputs().forEach(value -> keys.add(value.key()));
            cycle.availableAmounts().forEach(value -> keys.add(value.key()));
            cycle.externalInputs().forEach(value -> keys.add(value.key()));
            cycle.requiredOutputs().forEach(value -> keys.add(value.key()));
            for (int memberId : cycle.memberNodeIds()) {
                snapshot.nodes().stream()
                        .filter(node -> node.nodeId() == memberId)
                        .findFirst()
                        .ifPresent(node -> keys.add(node.key()));
            }
            snapshot.patterns().stream()
                    .filter(pattern -> pattern.componentId() == cycle.componentId() && pattern.firingCount() > 0L)
                    .flatMap(pattern ->
                            java.util.stream.Stream.concat(pattern.inputs().stream(), pattern.outputs().stream()))
                    .map(CraftingGraphSnapshot.Relationship::materialNodeId)
                    .forEach(materialId -> snapshot.nodes().stream()
                            .filter(node -> node.nodeId() == materialId)
                            .findFirst()
                            .ifPresent(node -> keys.add(node.key())));
            // A legacy/partially populated diagnostic may not have net-output entries yet. The member list
            // still needs a selectable row so every unresolved SCC remains openable from the report.
            for (AEKey key : keys) {
                // MaterialNode is built from the final executable plan. PatternNode firing counts are only
                // structural cycle metadata and can legitimately be zero for a populated plan.
                CraftingGraphSnapshot.MaterialNode material = neoecoae$materialFor(snapshot, key);
                cycleItems.putIfAbsent(
                        key,
                        new ECOCycleItemList.Entry(
                                key,
                                material == null ? java.math.BigInteger.ZERO : material.consumedBigInteger(),
                                material == null ? java.math.BigInteger.ZERO : material.producedBigInteger(),
                                neoecoae$exactAmountFor(cycle.exactSingleNetOutputs(), key),
                                neoecoae$exactAmountFor(cycle.exactTotalNetOutputs(), key),
                                cycle.executionCountKnowledge(),
                                cycle.solveStatus(),
                                cycle.componentId()));
            }
            // A cycle can be unresolved before it produces any output. Keep its required startup seeds in
            // the left-hand list so the report remains actionable instead of showing an empty plan.
            for (var seed : cycle.requiredSeed()) {
                CraftingGraphSnapshot.MaterialNode material = neoecoae$materialFor(snapshot, seed.key());
                cycleItems.putIfAbsent(
                        seed.key(),
                        new ECOCycleItemList.Entry(
                                seed.key(),
                                material == null ? java.math.BigInteger.ZERO : material.consumedBigInteger(),
                                material == null ? java.math.BigInteger.ZERO : material.producedBigInteger(),
                                java.math.BigInteger.ZERO,
                                java.math.BigInteger.ZERO,
                                cycle.executionCountKnowledge(),
                                cycle.solveStatus(),
                                cycle.componentId()));
            }
        }
        neoecoae$cycleItems = new ECOCycleItemList(List.copyOf(cycleItems.values()));
    }

    @Unique private static long neoecoae$amountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream()
                .filter(value -> value.key().equals(key))
                .mapToLong(CraftingGraphSnapshot.KeyAmount::amount)
                .findFirst()
                .orElse(0L);
    }

    @Unique private static boolean neoecoae$hasAmountFor(List<CraftingGraphSnapshot.KeyAmount> values, AEKey key) {
        return values.stream().anyMatch(value -> value.key().equals(key));
    }

    @Unique private static java.math.BigInteger neoecoae$exactAmountFor(
            List<cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.ExactKeyAmount> values, AEKey key) {
        return values.stream()
                .filter(value -> value.key().equals(key))
                .map(value -> value.amount().value())
                .findFirst()
                .orElse(java.math.BigInteger.ZERO);
    }

    @Unique private static @Nullable CraftingGraphSnapshot.MaterialNode neoecoae$materialFor(
            CraftingGraphSnapshot snapshot, AEKey key) {
        return snapshot.nodes().stream()
                .filter(node -> node.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean neoecoae$shouldShowFastPlannerReport() {
        return neoecoae$showFastPlannerReport;
    }

    @Override
    public boolean neoecoae$isCyclePlanningEnabled() {
        return neoecoae$cyclePlanningEnabled;
    }

    @Override
    public long neoecoae$getCalculationNanos() {
        return neoecoae$calculationNanos;
    }

    @Override
    public BigInteger neoecoae$getTheoreticalBytes() {
        try {
            return new BigInteger(neoecoae$theoreticalBytes);
        } catch (NumberFormatException ignored) {
            return BigInteger.ZERO;
        }
    }

    @Override
    public @Nullable PlanningStatus neoecoae$getPlanningStatus() {
        int ordinal = neoecoae$planningStatusCode - 1;
        PlanningStatus[] values = PlanningStatus.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    public List<ECOCycleItemList.Entry> neoecoae$getCycleItems() {
        return neoecoae$cycleItems.items();
    }

    @Override
    public CraftingGraphSnapshot neoecoae$getCraftingGraphSnapshot() {
        return neoecoae$craftingGraph;
    }
}
