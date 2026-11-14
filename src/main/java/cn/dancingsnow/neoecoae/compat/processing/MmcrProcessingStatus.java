package cn.dancingsnow.neoecoae.compat.processing;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingReflection.*;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader;
import cn.dancingsnow.neoecoae.compat.mmcr.ECOMmcrPatternDispatch;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingSamples;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** MMCR's published normal/factory cycles, reached through the actual bound ME input port. */
public final class MmcrProcessingStatus implements ECOProcessingStatusReader {
    @Override public boolean supports(BlockEntity machine) {
        return is(machine, ECOMmcrPatternDispatch.PORT) || is(machine, ECOMmcrPatternDispatch.CONTROLLER);
    }

    @Override public Observation read(BlockEntity machine, Context context) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide || machine.getLevel().getServer() == null
                || !machine.getLevel().getServer().isSameThread()) return Observation.EMPTY;
        if (is(machine, ECOMmcrPatternDispatch.PORT)) {
            return new Observation(null, ECOMmcrPatternDispatch.linkedTargets(machine));
        }
        Object snapshot = ECOMmcrPatternDispatch.publishedSnapshot(machine);
        if (snapshot == null) return Observation.EMPTY;
        var lanes = new ArrayList<Machine>();
        Object structure = call(snapshot, "structure");
        boolean formed = Boolean.TRUE.equals(call(structure, "formed"))
                && Boolean.TRUE.equals(call(structure, "structureAreaLoaded"));
        if (Boolean.TRUE.equals(call(snapshot, "factoryControllerPresent"))) {
            Object factory = call(snapshot, "factory");
            boolean paused = Boolean.TRUE.equals(call(factory, "paused"));
            Object presentations = call(factory, "presentationLanes");
            if (presentations instanceof Iterable<?> values) for (Object lane : values) {
                if (!Boolean.TRUE.equals(call(lane, "active"))
                        || !ECOMmcrPatternDispatch.matchesOutput(machine, call(lane, "presentation"), context.output())) continue;
                Object failure = call(lane, "failure");
                if (failure == null) failure = call(factory, "failure");
                add(machine, lane, formed, paused, null, failure, lanes);
            }
        } else {
            Object cycle = call(snapshot, "crafting");
            if (call(cycle, "recipeId") != null
                    && ECOMmcrPatternDispatch.matchesOutput(machine, call(snapshot, "recipePresentation"), context.output())) {
                Object status = call(cycle, "status");
                String name = String.valueOf(call(status, "getStatus"));
                add(machine, cycle, formed, "PAUSED".equals(name), status, call(cycle, "failure"), lanes);
            }
        }
        // MMCR has no AE2 job UUID. This is an explicit recipe/output association, not invented ownership.
        return lanes.isEmpty() ? Observation.EMPTY : Observation.sample(ECOProcessingSamples.combine(machine, lanes,
                Component.translatable("gui.neoecoae.processing.association.recipe")));
    }

    private static void add(BlockEntity machine, Object cycle, boolean formed, boolean paused,
            Object status, Object failure, ArrayList<Machine> lanes) {
        String failureId = String.valueOf(call(failure, "id"));
        String statusName = String.valueOf(call(status, "getStatus"));
        State state = !formed ? State.UNAVAILABLE
                : failure != null ? failureId.contains("output") ? State.WAITING : State.PAUSED
                : paused ? State.PAUSED : "NO_RECIPE".equals(statusName) ? State.WAITING : State.RUNNING;
        Component reason;
        Object translation = call(call(failure, "reason"), "translationKey");
        if (translation instanceof String key && !key.isBlank()) reason = Component.translatable(key);
        else if (!formed) reason = Component.translatable("gui.neoecoae.processing.paused");
        else if (paused) reason = Component.translatable("gui.mmcr.controller.redstone_stopped");
        else if (status != null && state != State.RUNNING) {
            Object message = call(status, "getUnlocMessage");
            reason = message instanceof String key && !key.isBlank() ? Component.translatable(key)
                    : Component.translatable("gui.neoecoae.processing.paused");
        } else reason = failure == null ? Component.empty() : Component.translatable("gui.neoecoae.processing.paused");
        lanes.add(ECOProcessingSamples.cycle(machine, String.valueOf(call(cycle, "recipeId")),
                integer(call(cycle, "tick")), integer(call(cycle, "totalTick")), count(call(cycle, "parallelism")), state, reason));
    }
}
