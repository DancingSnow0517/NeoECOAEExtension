package cn.dancingsnow.neoecoae.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runtime-only recipe contract. Never registered in the single-block recipe type. */
public record LargeWorkstationRecipe(ResourceLocation id, IntegratedWorkingStationRecipe display,
                                     long energy, List<GenericStack> extraInputs, ResourceLocation sourceType,
                                     List<SizedFluidIngredient> additionalFluidInputs, List<GenericStack> additionalOutputs,
                                     int processingTicks, long feEnergy, boolean feAlternative) {
    public static final ResourceLocation NATIVE_TYPE = ResourceLocation.parse("neoecoae:integrated_working_station");
    public static final int MAX_PROCESSING_TICKS = 200;

    public LargeWorkstationRecipe(ResourceLocation id, IntegratedWorkingStationRecipe display,
                                  long energy, List<GenericStack> extraInputs) {
        this(id, display, energy, extraInputs, NATIVE_TYPE);
    }

    public LargeWorkstationRecipe(ResourceLocation id, IntegratedWorkingStationRecipe display,
                                  long energy, List<GenericStack> extraInputs, ResourceLocation sourceType) {
        this(id, display, energy, extraInputs, sourceType, List.of(), List.of());
    }

    public LargeWorkstationRecipe(ResourceLocation id, IntegratedWorkingStationRecipe display,
                                  long energy, List<GenericStack> extraInputs, ResourceLocation sourceType,
                                  List<SizedFluidIngredient> additionalFluidInputs, List<GenericStack> additionalOutputs) {
        this(id, display, energy, extraInputs, sourceType, additionalFluidInputs, additionalOutputs,
            referenceTicks(sourceType, display, energy), 0, false);
    }

    /** AE and FE are separate budgets. Alternative means either complete budget, never both. */
    public LargeWorkstationRecipe processing(int ticks, long fe, boolean alternative) {
        return new LargeWorkstationRecipe(id, display, energy, extraInputs, sourceType,
            additionalFluidInputs, additionalOutputs, ticks, fe, alternative);
    }

    /** FE can always be paid with equivalent AE, so Applied Flux is optional. */
    public boolean requiresAppliedFlux() { return false; }

    private static int referenceTicks(ResourceLocation type, IntegratedWorkingStationRecipe display, long energy) {
        if (type.equals(NATIVE_TYPE)) return display.processingTicks();
        if (type.getNamespace().equals("ae2cs")) return (int) Math.max(1, Math.min(Integer.MAX_VALUE, energy / 200 + (energy % 200 == 0 ? 0 : 1)));
        return switch (type.toString()) {
            case "advanced_ae:reaction", "appgen:synthesizing", "extendedae:crystal_assembler", "extendedae_plus:crystal_assembler_plus" -> 100;
            case "ae2lt:overload_processing", "ae2lt:lightning_assembly", "ae2lt:lightning_simulation" -> 4;
            case "data_energistics:data_charger" -> 1;
            default -> 200;
        };
    }

    public LargeWorkstationRecipe {
        if (energy < 0) throw new IllegalArgumentException("Negative recipe energy");
        if (processingTicks <= 0 || feEnergy < 0 || feAlternative && (energy <= 0 || feEnergy <= 0)) {
            throw new IllegalArgumentException("Invalid processing duration or FE budget");
        }
        processingTicks = Math.min(processingTicks, MAX_PROCESSING_TICKS);
        extraInputs = List.copyOf(extraInputs);
        additionalFluidInputs = List.copyOf(additionalFluidInputs);
        additionalOutputs = List.copyOf(additionalOutputs);
        for (var input : additionalFluidInputs) {
            if (input.ingredient().isEmpty() || input.amount() <= 0) throw new IllegalArgumentException("Empty fluid input");
        }
        for (var output : additionalOutputs) {
            if (output.what() == null || output.amount() <= 0) throw new IllegalArgumentException("Empty output");
        }
    }

    /** Complete virtual contract; the single-block display only holds the first fluid and outputs. */
    public List<SizedFluidIngredient> inputFluids() {
        var fluids = new ArrayList<SizedFluidIngredient>();
        if (!display.inputFluid().ingredient().isEmpty()) fluids.add(display.inputFluid());
        fluids.addAll(additionalFluidInputs);
        return List.copyOf(fluids);
    }

    public List<GenericStack> outputs() {
        var outputs = new ArrayList<GenericStack>();
        if (display.hasItemOutput()) outputs.add(GenericStack.fromItemStack(display.itemOutput()));
        if (display.hasFluidOutput()) outputs.add(GenericStack.fromFluidStack(display.fluidOutput()));
        outputs.addAll(additionalOutputs);
        return List.copyOf(outputs);
    }

    public Component sourceTypeDescription() {
        return Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_type",
            Component.translatable("recipe_type.neoecoae." + sourceType.getNamespace() + "."
                + sourceType.getPath().replace('/', '.')));
    }

    public boolean matches(KeyCounter inputs, KeyCounter outputs) {
        return matchesOutputs(outputs) && matchesInputs(inputs);
    }

    public boolean matchesOutputs(KeyCounter outputs) {
        KeyCounter expected = outputAmounts();
        expected.removeAll(outputs);
        for (var entry : expected) if (entry.getLongValue() != 0) return false;
        return true;
    }

    /** Returns the common positive integer scale of the complete pattern contract, or zero if it differs. */
    public long matchingMultiplier(KeyCounter inputs, KeyCounter outputs) {
        KeyCounter expected = outputAmounts();
        long multiplier = 0;
        for (var entry : expected) {
            long amount = entry.getLongValue();
            long supplied = outputs.get(entry.getKey());
            if (amount <= 0 || supplied < amount || supplied % amount != 0) return 0;
            long scale = supplied / amount;
            if (multiplier != 0 && multiplier != scale) return 0;
            multiplier = scale;
        }
        for (var entry : outputs) {
            if (entry.getLongValue() != 0 && expected.get(entry.getKey()) == 0) return 0;
        }
        if (multiplier == 0) return 0;
        try {
            return matchesInputs(inputs, multiplier) ? multiplier : 0;
        } catch (ArithmeticException overflow) {
            return 0;
        }
    }

    private KeyCounter outputAmounts() {
        KeyCounter expected = new KeyCounter();
        for (var output : outputs()) expected.add(output.what(), output.amount());
        return expected;
    }

    public boolean matchesInputs(KeyCounter inputs) {
        return matchesInputs(inputs, 1);
    }

    private boolean matchesInputs(KeyCounter inputs, long multiplier) {
        KeyCounter remaining = new KeyCounter();
        remaining.addAll(inputs);
        for (var extra : extraInputs) {
            long supplied = remaining.get(extra.what());
            // Processing patterns may omit network resources; missing costs are acquired from ME.
            long required = Math.multiplyExact(extra.amount(), multiplier);
            if (supplied != 0 && supplied != required) return false;
            remaining.remove(extra.what(), supplied);
        }
        List<GenericStack> items = new ArrayList<>();
        List<GenericStack> fluids = new ArrayList<>();
        for (var entry : remaining) {
            long amount = entry.getLongValue();
            if (amount == 0) continue;
            if (amount < 0) return false;
            if (entry.getKey() instanceof AEItemKey) {
                items.add(new GenericStack(entry.getKey(), amount));
            } else if (entry.getKey() instanceof AEFluidKey) {
                fluids.add(new GenericStack(entry.getKey(), amount));
            } else return false;
        }
        var requiredFluids = inputFluids();
        long[] fluidSupplied = fluids.stream().mapToLong(GenericStack::amount).toArray();
        long[] fluidNeeded = requiredFluids.stream().mapToLong(r -> Math.multiplyExact((long) r.amount(), multiplier)).toArray();
        boolean[][] fluidAccepts = new boolean[fluids.size()][requiredFluids.size()];
        for (int i = 0; i < fluids.size(); i++) {
            var stack = ((AEFluidKey) fluids.get(i).what()).toStack(1);
            for (int j = 0; j < requiredFluids.size(); j++) {
                fluidAccepts[i][j] = requiredFluids.get(j).ingredient().test(stack);
            }
        }
        if (!matchesQuantities(fluidSupplied, fluidNeeded, fluidAccepts)) return false;

        // Integral max flow handles overlapping tags without greedy allocation failures, and
        // keeps large ingredient quantities compact instead of expanding them into 64-item stacks.
        var required = display.inputItems();
        long[] supplied = items.stream().mapToLong(GenericStack::amount).toArray();
        long[] needed = required.stream().mapToLong(r -> Math.multiplyExact((long) r.count(), multiplier)).toArray();
        boolean[][] accepts = new boolean[items.size()][required.size()];
        for (int i = 0; i < items.size(); i++) {
            var stack = ((AEItemKey) items.get(i).what()).toStack();
            for (int j = 0; j < required.size(); j++) accepts[i][j] = required.get(j).ingredient().test(stack);
        }
        return matchesQuantities(supplied, needed, accepts);
    }

    static boolean matchesQuantities(long[] supplied, long[] needed, boolean[][] accepts) {
        long supply = 0, demand = 0;
        for (long amount : supplied) { if (amount <= 0) return false; supply = Math.addExact(supply, amount); }
        for (long amount : needed) { if (amount <= 0) return false; demand = Math.addExact(demand, amount); }
        if (supply != demand) return false;
        int sink = supplied.length + needed.length + 1;
        long[][] residual = new long[sink + 1][sink + 1];
        for (int i = 0; i < supplied.length; i++) {
            residual[0][i + 1] = supplied[i];
            for (int j = 0; j < needed.length; j++) {
                if (accepts[i][j]) residual[i + 1][supplied.length + j + 1] = supplied[i];
            }
        }
        for (int j = 0; j < needed.length; j++) residual[supplied.length + j + 1][sink] = needed[j];
        long matched = 0;
        while (matched < demand) {
            int[] parent = new int[sink + 1];
            Arrays.fill(parent, -1);
            parent[0] = 0;
            var queue = new ArrayDeque<Integer>();
            queue.add(0);
            while (!queue.isEmpty() && parent[sink] == -1) {
                int from = queue.removeFirst();
                for (int to = 1; to <= sink; to++) {
                    if (parent[to] == -1 && residual[from][to] > 0) {
                        parent[to] = from;
                        queue.add(to);
                    }
                }
            }
            if (parent[sink] == -1) return false;
            long amount = demand - matched;
            for (int to = sink; to != 0; to = parent[to]) amount = Math.min(amount, residual[parent[to]][to]);
            for (int to = sink; to != 0; to = parent[to]) {
                residual[parent[to]][to] -= amount;
                residual[to][parent[to]] += amount;
            }
            matched += amount;
        }
        return true;
    }
}
