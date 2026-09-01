package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Locale;

/**
 * Semantic boundary for ExtendedAE Plus core recipes. Core stages share the
 * {@code extendedae_plus:basic_core} item id but are distinguished by data
 * components (core_type/core_stage); their AE keys must therefore remain exact
 * and must never be normalized to an item-id-only key.
 */
public final class ExtendedAEPlusPatternSemanticAdapter implements PatternSemanticAdapter {
    private final AE2PatternSemanticAdapter delegate = new AE2PatternSemanticAdapter();

    @Override
    public boolean supports(IPatternDetails pattern) {
        if (pattern == null) return false;
        try {
            for (var input : pattern.getInputs()) {
                if (input == null || input.getPossibleInputs() == null) continue;
                for (var stack : input.getPossibleInputs()) if (isCore(stack == null ? null : stack.what())) return true;
            }
            for (var output : pattern.getOutputs()) if (isCore(output == null ? null : output.what())) return true;
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    @Override
    public PatternSemantics analyze(IPatternDetails pattern) {
        // AEItemKey equality includes its component map. Delegating after the
        // boundary check preserves exact stage/type identity for every core.
        return delegate.analyze(pattern);
    }

    @Override
    public String name() {
        return "ExtendedAEPlus";
    }

    private static boolean isCore(AEKey key) {
        if (!(key instanceof AEItemKey item)) return false;
        try {
            var id = BuiltInRegistries.ITEM.getKey(item.toStack(1).getItem());
            if (id == null || !"extendedae_plus".equals(id.getNamespace())) return false;
            String path = id.getPath().toLowerCase(Locale.ROOT);
            return path.equals("basic_core") || path.equals("storage_core")
                || path.equals("spatial_core") || path.equals("energy_storage_core")
                || path.equals("quantum_storage_core");
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
