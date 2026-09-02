package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;

/** Recognizes remainder/catalyst state transitions before structural graph construction. */
public final class SpecialPatternAnalyzer {
    private final Function<CompiledInput, SpecialPatternAnalysis.Requirement> inspector;

    public SpecialPatternAnalyzer() {
        this(SpecialPatternAnalyzer::classify);
    }

    /** Injectable state inspector for integrations and registry-free planner tests. */
    public SpecialPatternAnalyzer(
            Function<CompiledInput, SpecialPatternAnalysis.Requirement> inspector) {
        this.inspector = Objects.requireNonNull(inspector);
    }

    public SpecialPatternAnalysis analyze(int patternId, IPatternDetails pattern, PatternSemantics semantics,
            List<CompiledInput> inputs) {
        // Normal patterns have no returned stack. This branch is deliberately before all ItemStack work.
        if (semantics.returnedOutputs().isEmpty()) return SpecialPatternAnalysis.NONE;

        List<SpecialPatternAnalysis.Requirement> requirements = new ArrayList<>();
        for (CompiledInput input : inputs) {
            if (input.remainderKey() == null || input.remainderAmountPerPattern().signum() <= 0) continue;
            SpecialPatternAnalysis.Requirement requirement = inspector.apply(input);
            if (requirement != null) requirements.add(requirement);
        }
        if (requirements.isEmpty()) return SpecialPatternAnalysis.NONE;

        return new SpecialPatternAnalysis(requirements);
    }

    private static SpecialPatternAnalysis.Requirement classify(CompiledInput input) {
        if (!(input.key() instanceof AEItemKey sourceKey)
                || !(input.remainderKey() instanceof AEItemKey returnedKey)) return null;
        ItemStack source = sourceKey.toStack(1);
        ItemStack returned = returnedKey.toStack(1);
        if (source.isEmpty() || returned.isEmpty()) return null;

        SpecialPatternAnalysis.Type type;
        int damagePerUse = 0;
        int maxDamage = 0;
        if (ItemStack.isSameItem(source, returned)) {
            if (source.isDamageableItem() && returned.isDamageableItem()) {
                damagePerUse = returned.getDamageValue() - source.getDamageValue();
                if (damagePerUse <= 0) return null;
                maxDamage = source.getMaxDamage();
                type = SpecialPatternAnalysis.Type.DURABILITY;
            } else if (ItemStack.isSameItemSameComponents(source, returned)) {
                type = SpecialPatternAnalysis.Type.REUSABLE;
            } else {
                type = SpecialPatternAnalysis.Type.CATALYST;
            }
        } else {
            type = SpecialPatternAnalysis.Type.CONTAINER;
        }
        return new SpecialPatternAnalysis.Requirement(input, input.remainderKey(), type, damagePerUse, maxDamage);
    }
}
