package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.crafting.IPatternDetails;
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
        tasks.forEach((pattern, count) -> {
            if (count <= 0) return;
            BigInteger copies = BigInteger.valueOf(count);
            Set<IPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            while (TYPES.contains(pattern.getClass().getName())) {
                if (!seen.add(pattern)) throw new IllegalArgumentException("Cyclic EAEP wrapper");
                try {
                    var original = (IPatternDetails) pattern.getClass().getMethod("getOriginal").invoke(pattern);
                    long multiplier = multiplier(pattern, original);
                    copies = copies.multiply(BigInteger.valueOf(multiplier));
                    pattern = original;
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalArgumentException("Unsupported EAEP execution wrapper", failure);
                }
            }
            result.merge(pattern, copies, BigInteger::add);
        });
        return result;
    }

    private static long multiplier(IPatternDetails scaled, IPatternDetails original) {
        var outputs = scaled.getOutputs();
        var base = original.getOutputs();
        if (outputs.isEmpty() || outputs.size() != base.size() || base.getFirst().amount() <= 0)
            throw new IllegalArgumentException("Invalid EAEP output shape");
        long n = outputs.getFirst().amount() / base.getFirst().amount();
        if (n <= 0) throw new IllegalArgumentException("Invalid EAEP multiplier");
        for (int i = 0; i < outputs.size(); i++) {
            if (!outputs.get(i).what().equals(base.get(i).what())
                    || outputs.get(i).amount() != Math.multiplyExact(base.get(i).amount(), n))
                throw new IllegalArgumentException("Non-linear EAEP outputs");
        }
        var inputs = scaled.getInputs();
        var originals = original.getInputs();
        if (inputs.length != originals.length) throw new IllegalArgumentException("Invalid EAEP input shape");
        for (int i = 0; i < inputs.length; i++) {
            if (!Arrays.equals(inputs[i].getPossibleInputs(), originals[i].getPossibleInputs())
                    || inputs[i].getMultiplier() != Math.multiplyExact(originals[i].getMultiplier(), n))
                throw new IllegalArgumentException("Non-linear EAEP inputs");
        }
        return n;
    }
}
