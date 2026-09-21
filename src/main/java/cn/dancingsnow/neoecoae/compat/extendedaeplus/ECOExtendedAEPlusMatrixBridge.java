package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.lang.reflect.Constructor;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import appeng.menu.AutoCraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Optional reflection boundary for ExtendedAE Plus' super and ultimate assembler matrices.
 *
 * <p>The normal AE2 provider contract only accepts one logical pattern at a time. ExtendedAE Plus adds a
 * scaled molecular-assembler pattern so its matrices can queue an entire C-series task in one provider call.
 * Keeping both the matrix type and the scaled-pattern type behind reflection lets NeoECO load without EAP.</p>
 */
public final class ECOExtendedAEPlusMatrixBridge {
    private static final ReflectionApi API = ReflectionApi.load();

    private ECOExtendedAEPlusMatrixBridge() {
    }

    public static boolean supportsProvider(ICraftingProvider provider) {
        // EAP represents both normal and ultimate structures with this same provider superclass.
        return API != null && API.matrixProviderType().isInstance(provider);
    }

    @Nullable
    public static ECOFastPathDispatchProvider adapt(ICraftingProvider provider) {
        if (!supportsProvider(provider)) return null;
        return context -> {
            if (!supports(provider, context.pattern()) || provider.isBusy()
                    || !context.containerItems().isEmpty() || !verify(context)) return null;
            return new ECOFastPathDispatchProvider.Preparation(Long.MAX_VALUE, null, false, batch -> {
                IPatternDetails scaled = batch.craftCount() == 1 ? context.pattern()
                    : scale(context.pattern(), batch.craftCount());
                KeyCounter[] inputs = multiplyInputHolder(context.inputCounters(), batch.craftCount());
                if (scaled == null || inputs == null) return false;
                try {
                    return provider.pushPattern(scaled, inputs);
                } catch (RuntimeException failure) {
                    throw new ECOIndeterminateBatchException("EAP matrix acceptance is unknown", failure);
                }
            });
        };
    }

    static boolean verify(ECOBatchDispatchContext context) {
        var pattern = (IMolecularAssemblerSupportedPattern) context.pattern();
        var grid = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        var counters = context.inputCounters();
        pattern.fillCraftingGrid(counters, grid::setItem);
        for (var counter : counters) {
            counter.removeZeros();
            if (!counter.isEmpty()) return false;
        }
        var materialized = new ArrayList<ItemStack>();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            if (!grid.getItem(slot).isEmpty()) materialized.add(grid.getItem(slot).copy());
        }
        var input = grid.asPositionedCraftInput().input();
        var output = ECOFastPathStacks.fromItemStack(pattern.assemble(input, context.level()));
        var consumed = ECOFastPathStacks.fromItemStacks(materialized);
        return output.isPresent() && output.get().equals(context.outputs())
            && consumed.isPresent() && consumed.get().equals(context.inputItems())
            && pattern.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty);
    }

    /** Returns whether this provider/pattern pair can use the EAP counted dispatch contract. */
    public static boolean supports(ICraftingProvider provider, IPatternDetails pattern) {
        if (API == null || provider == null || pattern == null
                || !API.matrixProviderType().isInstance(provider)
                || !(pattern instanceof IMolecularAssemblerSupportedPattern)
                || API.scaledPatternType().isInstance(pattern)) {
            return false;
        }
        return hasNoReturnedInputs(pattern);
    }

    /** Creates EAP's scaled wrapper without linking NeoECO to EAP at class-load time. */
    @Nullable
    public static IPatternDetails scale(IPatternDetails pattern, long multiplier) {
        if (API == null || multiplier <= 1L || !(pattern instanceof IMolecularAssemblerSupportedPattern)) {
            return null;
        }
        try {
            return (IPatternDetails) API.scaledPatternConstructor().newInstance(pattern, multiplier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** Copies one extracted AE2 input table and multiplies every slot for a scaled provider call. */
    @Nullable
    public static KeyCounter[] multiplyInputHolder(@Nullable KeyCounter[] source, long multiplier) {
        if (source == null || multiplier <= 0L) {
            return null;
        }
        try {
            KeyCounter[] result = new KeyCounter[source.length];
            for (int slot = 0; slot < source.length; slot++) {
                KeyCounter target = new KeyCounter();
                KeyCounter original = source[slot];
                if (original != null) {
                    for (var entry : original) {
                        if (entry.getKey() == null || entry.getLongValue() <= 0L) {
                            continue;
                        }
                        target.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
                    }
                }
                result[slot] = target;
            }
            return result;
        } catch (ArithmeticException failure) {
            return null;
        }
    }

    private static boolean hasNoReturnedInputs(IPatternDetails pattern) {
        try {
            IPatternDetails.IInput[] inputs = pattern.getInputs();
            if (inputs == null || inputs.length == 0) {
                return false;
            }
            for (IPatternDetails.IInput input : inputs) {
                if (input == null || input.getPossibleInputs() == null) {
                    return false;
                }
                for (var candidate : input.getPossibleInputs()) {
                    if (candidate == null || candidate.what() == null
                            || input.getRemainingKey(candidate.what()) != null) {
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    private record ReflectionApi(
            Class<?> matrixProviderType,
            Class<?> scaledPatternType,
            Constructor<?> scaledPatternConstructor) {
        @Nullable
        private static ReflectionApi load() {
            try {
                ClassLoader loader = ECOExtendedAEPlusMatrixBridge.class.getClassLoader();
                Class<?> molecularPattern = IMolecularAssemblerSupportedPattern.class;
                Class<?> matrixProvider = Class.forName(
                    "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity",
                    false, loader);
                Class<?> scaledPattern = Class.forName(
                    "com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern",
                    false, loader);
                Constructor<?> constructor = scaledPattern.getConstructor(molecularPattern, long.class);
                return new ReflectionApi(matrixProvider, scaledPattern, constructor);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
