package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;

/** Preserves component-sensitive identities for ExtendedAE Plus cores. */
public final class ExtendedAEPlusPatternSemanticAdapter implements PatternSemanticAdapter {
    private final AE2PatternSemanticAdapter delegate = new AE2PatternSemanticAdapter();

    @Override
    public boolean supports(IPatternDetails pattern) {
        if (pattern == null) return false;
        try {
            for (var input : pattern.getInputs()) {
                if (input == null || input.getPossibleInputs() == null) continue;
                for (var stack : input.getPossibleInputs()) {
                    if (isCore(stack == null ? null : stack.what())) return true;
                }
            }
            for (var output : pattern.getOutputs()) {
                if (isCore(output == null ? null : output.what())) return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    @Override public PatternSemantics analyze(IPatternDetails pattern) { return delegate.analyze(pattern); }
    @Override public String name() { return "ExtendedAEPlus"; }

    private static boolean isCore(AEKey key) {
        if (!(key instanceof AEItemKey item)) return false;
        try {
            var id = BuiltInRegistries.ITEM.getKey(item.toStack(1).getItem());
            if (id == null || !"extendedae_plus".equals(id.getNamespace())) return false;
            String path = id.getPath().toLowerCase(Locale.ROOT);
            return path.equals("basic_core") || path.equals("storage_core") || path.equals("spatial_core")
                    || path.equals("energy_storage_core") || path.equals("quantum_storage_core");
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
