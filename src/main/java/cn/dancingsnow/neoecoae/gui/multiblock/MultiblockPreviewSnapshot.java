package cn.dancingsnow.neoecoae.gui.multiblock;

import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import java.util.function.Supplier;

/** Immutable, bounded view of one multiblock preview computation. */
public record MultiblockPreviewSnapshot(
    Status status,
    int missing,
    int conflictCount,
    int reused,
    int requiredCount,
    List<BlockPos> conflicts,
    List<Material> materials
) {
    public static final int MAX_CONFLICTS = 8;
    public static final int MAX_MATERIALS = 24;

    public enum Status {
        CONTROLLER_FORMED,
        BUILD_IN_PROGRESS,
        NO_DEFINITION,
        CONFLICTS_DETECTED,
        STRUCTURE_READY,
        READY_TO_BUILD,
        NOT_ENOUGH_ITEMS
    }

    public record Material(ItemStack stack, int required, boolean enough) {
        public Material {
            stack = stack == null ? null : stack.copyWithCount(1);
            required = Math.max(0, required);
        }

        @Override public ItemStack stack() { return stack == null ? ItemStack.EMPTY : stack.copy(); }
    }

    public MultiblockPreviewSnapshot {
        status = status == null ? Status.NO_DEFINITION : status;
        missing = Math.max(0, missing);
        conflictCount = Math.max(0, conflictCount);
        reused = Math.max(0, reused);
        requiredCount = Math.max(0, requiredCount);
        conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
        materials = List.copyOf(materials == null ? List.of() : materials);
    }

    public static MultiblockPreviewSnapshot capture(
        Player player,
        BooleanSupplier formed,
        BooleanSupplier buildInProgress,
        Supplier<MultiBlockPlacementPlan> previewPlan
    ) {
        if (formed.getAsBoolean()) {
            return new MultiblockPreviewSnapshot(Status.CONTROLLER_FORMED, 0, 0, 0, 0, List.of(), List.of());
        }
        MultiBlockPlacementPlan plan = previewPlan.get();
        return fromPlan(buildInProgress.getAsBoolean() ? Status.BUILD_IN_PROGRESS : statusFor(plan), plan, player.isCreative(),
            stack -> MultiBlockPlacementService.countMatchingItems(player, stack));
    }

    static MultiblockPreviewSnapshot captureInput(
        boolean formed,
        boolean buildInProgress,
        boolean creative,
        ToIntFunction<PlannedMaterial> availableItems,
        Supplier<PreviewInput> previewPlan
    ) {
        if (formed) {
            return new MultiblockPreviewSnapshot(Status.CONTROLLER_FORMED, 0, 0, 0, 0, List.of(), List.of());
        }
        PreviewInput plan = previewPlan.get();
        if (buildInProgress) {
            return fromInput(Status.BUILD_IN_PROGRESS, plan, creative, availableItems);
        }
        return fromInput(statusFor(plan), plan, creative, availableItems);
    }

    static Status statusFor(MultiBlockPlacementPlan plan) {
        if (plan == null) return Status.NO_DEFINITION;
        if (!plan.getConflictPositions().isEmpty()) return Status.CONFLICTS_DETECTED;
        return plan.getMissingBlocks().isEmpty() ? Status.STRUCTURE_READY : Status.READY_TO_BUILD;
    }

    static MultiblockPreviewSnapshot fromPlan(
        Status status, MultiBlockPlacementPlan plan, boolean creative, ToIntFunction<ItemStack> availableItems
    ) {
        if (plan == null) return new MultiblockPreviewSnapshot(status, 0, 0, 0, 0, List.of(), List.of());
        List<Material> materials = plan.getRequiredItems().stream().map(item -> {
            ItemStack stack = item.stack();
            return new Material(stack, item.count(), creative || availableItems.applyAsInt(stack) >= item.count());
        }).toList();
        boolean enough = creative || materials.stream().allMatch(Material::enough);
        if (status == Status.READY_TO_BUILD && !enough) status = Status.NOT_ENOUGH_ITEMS;
        return new MultiblockPreviewSnapshot(status, plan.getMissingBlocks().size(), plan.getConflictPositions().size(),
            plan.getReusedBlockCount(), plan.getRequiredItemCount(), plan.getConflictPositions().stream().limit(MAX_CONFLICTS).toList(),
            materials.stream().limit(MAX_MATERIALS).toList());
    }

    static Status statusFor(PreviewInput input) {
        if (input == null) return Status.NO_DEFINITION;
        if (input.conflictCount() > 0) return Status.CONFLICTS_DETECTED;
        return input.missing() == 0 ? Status.STRUCTURE_READY : Status.READY_TO_BUILD;
    }

    static MultiblockPreviewSnapshot fromInput(
        Status status, PreviewInput input, boolean creative, ToIntFunction<PlannedMaterial> availableItems
    ) {
        if (input == null) return new MultiblockPreviewSnapshot(status, 0, 0, 0, 0, List.of(), List.of());
        List<Material> materials = input.materials().stream().map(item -> new Material(item.stack(), item.required(),
            creative || availableItems.applyAsInt(item) >= item.required()))
            .toList();
        boolean enough = creative || materials.stream().allMatch(Material::enough);
        if (status == Status.READY_TO_BUILD && !enough) status = Status.NOT_ENOUGH_ITEMS;
        return new MultiblockPreviewSnapshot(status, input.missing(), input.conflictCount(), input.reused(), input.requiredCount(),
            input.conflicts().stream().limit(MAX_CONFLICTS).toList(),
            materials.stream().limit(MAX_MATERIALS).toList());
    }

    record PlannedMaterial(Object key, ItemStack stack, int required) {
        PlannedMaterial {
            stack = stack == null ? null : stack.copyWithCount(1);
            required = Math.max(0, required);
        }
    }

    record PreviewInput(int missing, int conflictCount, int reused, int requiredCount, List<BlockPos> conflicts, List<PlannedMaterial> materials) {
        PreviewInput {
            missing = Math.max(0, missing);
            conflictCount = Math.max(0, conflictCount);
            reused = Math.max(0, reused);
            requiredCount = Math.max(0, requiredCount);
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            materials = List.copyOf(materials == null ? List.of() : materials);
        }
    }
}
