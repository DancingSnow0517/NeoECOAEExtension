package cn.dancingsnow.neoecoae.compat.processing;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingReflection.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingSamples;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/** Covers inherited Mekanism machines and the independently implemented Extras/MoreMachine factories. */
public final class MekanismProcessingStatus implements ECOProcessingStatusReader {
    @Override public boolean supports(BlockEntity machine) {
        return is(machine, "mekanism.common.tile.prefab.TileEntityRecipeMachine")
            || is(machine, "mekanism.common.tile.factory.TileEntityFactory")
            || is(machine, "com.jerry.mekextras.common.tile.factory.TileEntityExtraFactory")
            || is(machine, "com.jerry.mekmm.common.tile.factory.TileEntityMoreMachineFactory");
    }

    @Override public Observation read(BlockEntity machine, Context context) {
        var lanes = new ArrayList<Machine>();
        Object monitors = field(machine, "recipeCacheLookupMonitors");
        int duration = integer(call(machine, "getTicksRequired"));
        if (monitors instanceof Object[] factories) {
            Object states = field(machine, "activeStates");
            for (int i = 0; i < factories.length; i++) {
                Object cached = call(factories[i], "getCachedRecipe", i);
                boolean active = states instanceof boolean[] flags && i < flags.length && flags[i];
                add(machine, context.output(), cached, integer(call(machine, "getProgress", i)), duration, active, lanes);
            }
        } else {
            Object cached = call(field(machine, "recipeCacheLookupMonitor"), "getCachedRecipe", 0);
            add(machine, context.output(), cached, integer(call(machine, "getOperatingTicks")), duration,
                Boolean.TRUE.equals(call(machine, "getActive")), lanes);
        }
        return lanes.isEmpty() ? Observation.EMPTY : Observation.sample(ECOProcessingSamples.combine(machine, lanes,
            Component.translatable("gui.neoecoae.processing.association.recipe")));
    }

    private static void add(BlockEntity machine, AEKey output, Object cached, int progress, int duration,
            boolean active, List<Machine> lanes) {
        Object recipe = call(cached, "getRecipe");
        if (recipe == null || Boolean.FALSE.equals(call(cached, "isInputValid")) || !matchesRecipe(recipe, output)) return;
        // CachedRecipe errors are maintained on the server even while no machine GUI is open.
        Object errors = field(cached, "errors");
        boolean blockedOutput = containsError(errors, "NOT_ENOUGH_OUTPUT_SPACE")
            || containsError(errors, "NOT_ENOUGH_SECONDARY_OUTPUT_SPACE");
        boolean energy = containsError(errors, "NOT_ENOUGH_ENERGY")
            || containsError(errors, "NOT_ENOUGH_ENERGY_REDUCED_RATE");
        State state = blockedOutput ? State.WAITING : active ? State.RUNNING : State.PAUSED;
        Component reason = blockedOutput ? Component.translatable("gui.neoecoae.processing.output_blocked")
            : energy ? Component.translatable("gui.neoecoae.processing.energy")
            : active ? Component.empty() : Component.translatable("gui.neoecoae.processing.paused");
        lanes.add(ECOProcessingSamples.cycle(machine, output.getId().toString(), progress, duration, 1, state, reason));
    }

    private static boolean containsError(Object errors, String name) {
        if (errors instanceof Iterable<?> values) for (Object error : values) {
            // Mekanism RecipeError is a named singleton, not an enum.
            Object expected = field(error, name);
            if (expected == error) return true;
        }
        return false;
    }

    static boolean matchesRecipe(Object recipe, AEKey output) {
        for (String getter : List.of("getOutputDefinition", "getMainOutputDefinition", "getSecondaryOutputDefinition")) {
            if (matchesOutput(call(recipe, getter), output, 0)) return true;
        }
        return false;
    }

    private static boolean matchesOutput(Object value, AEKey output, int depth) {
        if (value == null || depth > 4) return false;
        if (value instanceof ItemStack stack) return !stack.isEmpty() && output.equals(AEItemKey.of(stack));
        if (value instanceof FluidStack stack) return !stack.isEmpty() && output.equals(AEFluidKey.of(stack));
        if (is(value, "mekanism.api.chemical.ChemicalStack")) {
            return !Boolean.TRUE.equals(call(value, "isEmpty")) && output.getPrimaryKey().equals(call(value, "getChemical"));
        }
        if (value instanceof Iterable<?> values) {
            for (Object child : values) if (matchesOutput(child, output, depth + 1)) return true;
        } else if (value.getClass().isRecord()) {
            for (var component : value.getClass().getRecordComponents()) {
                if (matchesOutput(call(value, component.getName()), output, depth + 1)) return true;
            }
        }
        return false;
    }
}
