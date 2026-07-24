package cn.dancingsnow.neoecoae.impl.crafting.planner.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** An immutable weighted hyperedge in the ECO crafting graph. */
public record ECOPlanningOperation<K, R>(
    R reference,
    Map<K, Long> inputs,
    Map<K, Long> outputs,
    Set<K> selectableOutputs
) {
    public ECOPlanningOperation(R reference, Map<K, Long> inputs, Map<K, Long> outputs) {
        this(reference, inputs, outputs, outputs.keySet());
    }

    public ECOPlanningOperation {
        Objects.requireNonNull(reference, "reference");
        inputs = copyAmounts(inputs, "inputs");
        outputs = copyAmounts(outputs, "outputs");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("A planning operation must have at least one output");
        }
        selectableOutputs = Set.copyOf(Objects.requireNonNull(selectableOutputs, "selectableOutputs"));
        if (selectableOutputs.isEmpty() || !outputs.keySet().containsAll(selectableOutputs)) {
            throw new IllegalArgumentException("Selectable outputs must be a non-empty subset of outputs");
        }
    }

    public long inputAmount(K material) {
        return inputs.getOrDefault(material, 0L);
    }

    public long outputAmount(K material) {
        return outputs.getOrDefault(material, 0L);
    }

    public long netOutput(K material) {
        return Math.subtractExact(outputAmount(material), inputAmount(material));
    }

    private static <K> Map<K, Long> copyAmounts(Map<K, Long> source, String name) {
        Objects.requireNonNull(source, name);
        Map<K, Long> copy = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            K material = Objects.requireNonNull(entry.getKey(), name + " material");
            long amount = Objects.requireNonNull(entry.getValue(), name + " amount");
            if (amount <= 0) {
                throw new IllegalArgumentException(name + " amounts must be positive");
            }
            copy.merge(material, amount, Math::addExact);
        }
        return Map.copyOf(copy);
    }
}
