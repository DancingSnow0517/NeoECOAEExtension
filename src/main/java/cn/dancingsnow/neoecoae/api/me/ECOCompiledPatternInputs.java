package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECORecipeClassifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Pattern-invariant input semantics shared by previews and the exact resolver. */
public final class ECOCompiledPatternInputs {
    public enum ResolutionMode { DIRECT_EXACT, AE2_GENERIC }

    private static final WeakHashMap<IPatternDetails, CacheEntry> CACHE = new WeakHashMap<>();

    private final Set<AEKey> primaryInputs;
    private final Set<AEKey> possibleInputs;
    private final Set<AEKey> reusableTemplates;
    private final List<CompiledSlot> slots;
    private final List<GenericStack> outputs;
    private final ResolutionMode resolutionMode;
    private final boolean resolvedLeaseSafe;

    private ECOCompiledPatternInputs(Set<AEKey> primaryInputs, Set<AEKey> possibleInputs,
            Set<AEKey> reusableTemplates, List<CompiledSlot> slots, List<GenericStack> outputs,
            ResolutionMode resolutionMode, boolean resolvedLeaseSafe) {
        this.primaryInputs = Set.copyOf(primaryInputs);
        this.possibleInputs = Set.copyOf(possibleInputs);
        this.reusableTemplates = Set.copyOf(reusableTemplates);
        this.slots = List.copyOf(slots);
        this.outputs = List.copyOf(outputs);
        this.resolutionMode = resolutionMode;
        this.resolvedLeaseSafe = resolvedLeaseSafe;
    }

    public static synchronized ECOCompiledPatternInputs get(IPatternDetails pattern) {
        long generation = AE2PatternIntrospection.reloadGeneration();
        CacheEntry cached = CACHE.get(pattern);
        if (cached != null && cached.generation == generation) return cached.compiled;
        ECOCompiledPatternInputs compiled = compile(pattern);
        CACHE.put(pattern, new CacheEntry(generation, compiled));
        return compiled;
    }

    public static synchronized void clearCache() {
        CACHE.clear();
    }

    Set<AEKey> primaryInputs() { return primaryInputs; }
    Set<AEKey> possibleInputs() { return possibleInputs; }
    Set<AEKey> reusableTemplates() { return reusableTemplates; }
    public ResolutionMode resolutionMode() { return resolutionMode; }
    public boolean resolvedLeaseSafe() { return resolvedLeaseSafe; }

    @Nullable
    KeyCounter[] resolveDirect(ECOCraftingInputPreview inventory, Level level,
            KeyCounter expectedOutputs, KeyCounter expectedContainers) {
        if (resolutionMode != ResolutionMode.DIRECT_EXACT) return null;
        KeyCounter[] result = new KeyCounter[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            CompiledSlot slot = slots.get(index);
            if (!slot.input.isValid(slot.key, level)
                    || inventory.extract(slot.key, slot.amount, Actionable.SIMULATE) < slot.amount) {
                reinject(inventory, result);
                return null;
            }
            long extracted = inventory.extract(slot.key, slot.amount, Actionable.MODULATE);
            if (extracted != slot.amount) {
                reinject(inventory, result);
                throw new IllegalStateException("Compiled ECO input simulation changed during resolution");
            }
            KeyCounter counter = new KeyCounter();
            counter.add(slot.key, slot.amount);
            result[index] = counter;
        }
        for (GenericStack output : outputs) expectedOutputs.add(output.what(), output.amount());
        return result;
    }

    private static void reinject(ECOCraftingInputPreview inventory, KeyCounter[] inputs) {
        for (KeyCounter input : inputs) {
            if (input == null) continue;
            for (var entry : input) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
    }

    private static ECOCompiledPatternInputs compile(IPatternDetails pattern) {
        Set<AEKey> primary = new HashSet<>();
        Set<AEKey> possibleKeys = new HashSet<>();
        Set<AEKey> reusable = new HashSet<>();
        List<CompiledSlot> directSlots = new ArrayList<>();
        List<GenericStack> compiledOutputs = new ArrayList<>();
        boolean direct = true;
        boolean resolvedLeaseSafe = false;
        try {
            ECORecipeClassifier.Classification classification = ECORecipeClassifier.classify(pattern);
            resolvedLeaseSafe = classification.supported()
                && classification.type() == ECORecipeClassifier.Type.NORMAL
                && "STATIC_ITEM_CONTRACT".equals(classification.reason());
            direct = resolvedLeaseSafe;
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                GenericStack[] possible = input == null ? null : input.getPossibleInputs();
                if (possible == null || possible.length == 0) {
                    direct = false;
                    continue;
                }
                GenericStack first = possible[0];
                if (first != null && first.what() != null) primary.add(first.what());
                for (GenericStack candidate : possible) {
                    if (candidate == null || candidate.what() == null) continue;
                    possibleKeys.add(candidate.what());
                    if (isReusableTemplate(input, candidate.what())) reusable.add(candidate.what());
                }
                if (!direct) continue;
                if (possible.length != 1 || first == null || first.what() == null
                        || first.amount() <= 0L || input.getMultiplier() <= 0L
                        || input.getRemainingKey(first.what()) != null) {
                    direct = false;
                    continue;
                }
                try {
                    directSlots.add(new CompiledSlot(input, first.what(),
                        Math.multiplyExact(first.amount(), input.getMultiplier())));
                } catch (ArithmeticException overflow) {
                    direct = false;
                    directSlots.clear();
                }
            }
            for (GenericStack output : pattern.getOutputs()) {
                if (output == null || output.what() == null || output.amount() <= 0L) direct = false;
                else compiledOutputs.add(output);
            }
            if (directSlots.size() != pattern.getInputs().length) direct = false;
        } catch (RuntimeException unavailable) {
            direct = false;
            resolvedLeaseSafe = false;
            directSlots.clear();
        }
        return new ECOCompiledPatternInputs(primary, possibleKeys, reusable, directSlots, compiledOutputs,
            direct ? ResolutionMode.DIRECT_EXACT : ResolutionMode.AE2_GENERIC, resolvedLeaseSafe);
    }

    private static boolean isReusableTemplate(IPatternDetails.IInput input, AEKey key) {
        try {
            AEKey remainder = input.getRemainingKey(key);
            if (key.equals(remainder)) return true;
            if (!(key instanceof AEItemKey item) || !(remainder instanceof AEItemKey returned)
                    || item.getItem() != returned.getItem()) return false;
            var before = item.toStack(1);
            var after = returned.toStack(1);
            return before.isDamageableItem() && after.isDamageableItem()
                && after.getDamageValue() > before.getDamageValue();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private record CompiledSlot(IPatternDetails.IInput input, AEKey key, long amount) {}
    private record CacheEntry(long generation, ECOCompiledPatternInputs compiled) {}
}
