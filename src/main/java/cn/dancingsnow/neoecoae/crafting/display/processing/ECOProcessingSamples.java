package cn.dancingsnow.neoecoae.crafting.display.processing;

import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Combines execution lanes without counting one factory as many physical machines. */
public final class ECOProcessingSamples {
    private ECOProcessingSamples() {}
    public static String location(BlockEntity machine) {
        return (machine.getLevel() == null ? "unknown" : machine.getLevel().dimension().location())
            + " [" + machine.getBlockPos().toShortString() + "]";
    }

    public static Machine cycle(BlockEntity machine, String recipe, int progress, int duration,
            long parallel, State state, Component reason) {
        return new Machine(machine.getBlockState().getBlock().getName(), location(machine), recipe,
            progress, duration, parallel, state, reason);
    }

    public static Machine combine(BlockEntity machine, List<Machine> lanes, Component association) {
        double work = 0, done = 0;
        long parallel = 0;
        boolean running = false, waiting = false, paused = false;
        Component reason = association;
        for (var lane : lanes) {
            double weight = Math.max(1, lane.parallel());
            work += lane.durationTicks() * weight;
            done += lane.progressTicks() * weight;
            parallel = cn.dancingsnow.neoecoae.crafting.amount.NEMath.saturatingAdd(parallel, lane.parallel());
            running |= lane.state() == State.RUNNING;
            waiting |= lane.state() == State.WAITING;
            paused |= lane.state() == State.PAUSED;
            if (!lane.reason().getString().isEmpty()) reason = lane.reason().copy().append(" ").append(association);
        }
        State state = waiting ? State.WAITING : running ? State.RUNNING : paused ? State.PAUSED : State.QUEUED;
        int duration = work > 0 ? (int) Math.min(Integer.MAX_VALUE, Math.ceil(work / Math.max(1, parallel))) : 0;
        return cycle(machine, lanes.isEmpty() ? "" : lanes.getFirst().recipe(),
            work > 0 ? (int) Math.round(duration * done / work) : 0, duration,
            parallel, state, reason);
    }
}
