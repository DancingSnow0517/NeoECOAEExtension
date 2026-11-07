package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.math.BigInteger;
import java.util.*;

/** Removes only EAEP's automatic execution wrappers, never encoded amounts or other mods' semantics. */
public final class ECOExternalPatternNormalization {
    private static final Set<String> TYPES = Set.of(
            "com.extendedae_plus.api.crafting.ScaledProcessingPattern",
            "com.extendedae_plus.api.crafting.ScaledProcessingPatternAdv",
            "com.extendedae_plus.api.crafting.ScaledMolecularAssemblerPattern");
    private ECOExternalPatternNormalization() {}

    public static Map<IPatternDetails, BigInteger> normalize(Map<IPatternDetails, Long> tasks) {
        Map<IPatternDetails, BigInteger> result = new LinkedHashMap<>();
        normalizeQuantities(tasks).forEach((pattern, count) -> result.put(pattern, count.toBigInteger()));
        return result;
    }

    public static Map<IPatternDetails, PlannerAmount> normalizeQuantities(Map<IPatternDetails, Long> tasks) {
        Map<IPatternDetails, PlannerAmount> result = new LinkedHashMap<>();
        tasks.forEach((pattern, count) -> {
            if (count <= 0) return;
            PlannerAmount copies = PlannerAmount.of(count);
            Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            while (TYPES.contains(pattern.getClass().getName())) {
                if (!seen.add(pattern)) throw new IllegalArgumentException("Cyclic EAEP wrapper");
                try {
                    var original = (IPatternDetails) pattern.getClass().getMethod("getOriginal").invoke(pattern);
                    long multiplier = multiplier(pattern, original);
                    copies = copies.multiply(multiplier);
                    pattern = original;
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalArgumentException("Unsupported EAEP execution wrapper", failure);
                }
            }
            result.merge(pattern, copies, PlannerAmount::add);
        });
        return result;
    }

    private static long multiplier(IPatternDetails scaled, IPatternDetails original) {
        var outputs = java.util.Arrays.asList(scaled.getOutputs());
        var base = java.util.Arrays.asList(original.getOutputs());
        if (outputs.isEmpty() || outputs.size() != base.size() || cn.dancingsnow.neoecoae.platform.JdkCompat.first(base).amount() <= 0)
            throw new IllegalArgumentException("Invalid EAEP output shape");
        long n = cn.dancingsnow.neoecoae.platform.JdkCompat.first(outputs).amount() / cn.dancingsnow.neoecoae.platform.JdkCompat.first(base).amount();
        if (n <= 0) throw new IllegalArgumentException("Invalid EAEP multiplier");
        for (int i = 0; i < outputs.size(); i++) {
            if (!outputs.get(i).what().equals(base.get(i).what())
                    || !PlannerAmount.of(outputs.get(i).amount())
                        .equals(PlannerAmount.of(base.get(i).amount()).multiply(n)))
                throw new IllegalArgumentException("Non-linear EAEP outputs");
        }
        var inputs = scaled.getInputs();
        var originals = original.getInputs();
        if (inputs.length != originals.length) throw new IllegalArgumentException("Invalid EAEP input shape");
        for (int i = 0; i < inputs.length; i++) {
            if (!Arrays.equals(inputs[i].getPossibleInputs(), originals[i].getPossibleInputs())
                    || !PlannerAmount.of(inputs[i].getMultiplier())
                        .equals(PlannerAmount.of(originals[i].getMultiplier()).multiply(n)))
                throw new IllegalArgumentException("Non-linear EAEP inputs");
        }
        return n;
    }
}
