package cn.dancingsnow.neoecoae.compat.processing;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingReflection.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingSamples;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** ExtendedAE execution cores and EAEP super/ultimate matrix batches, with canonical controller resolution. */
public final class AssemblerMatrixProcessingStatus implements ECOProcessingStatusReader {
    private static final String BASE = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase";
    private static final String CRAFTER = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter";
    @Override public boolean supports(BlockEntity machine) {
        return is(machine, BASE) || is(machine, "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity");
    }

    @Override public Observation read(BlockEntity machine, Context context) {
        Object superCluster = call(machine, "eap$getSuperMatrixCluster");
        if (superCluster != null) {
            Object core = call(superCluster, "getCore");
            if (!(core instanceof BlockEntity controller) || Boolean.TRUE.equals(call(superCluster, "isDestroyed"))) return Observation.EMPTY;
            if (controller != machine) return new Observation(null, List.of(target(controller)));
            return superMatrix(machine, superCluster, context.output());
        }
        if (is(machine, CRAFTER)) return crafter(machine, context.output());
        Object cluster = call(machine, "getCluster");
        Object members = call(cluster, "getBlockEntities");
        var links = new ArrayList<Target>();
        if (members instanceof Iterator<?> iterator) while (iterator.hasNext()) {
            Object part = iterator.next();
            if (part instanceof BlockEntity be && is(be, CRAFTER)) links.add(target(be));
        }
        return new Observation(null, links);
    }

    private static Target target(BlockEntity be) { return new Target(be.getLevel().dimension().location(), be.getBlockPos()); }

    private static Observation crafter(BlockEntity machine, AEKey output) {
        var lanes = new ArrayList<Machine>();
        Object threads = field(machine, "threads");
        if (threads instanceof Object[] values) for (Object thread : values) {
            if (!matches(field(thread, "myPlan"), output) || !Boolean.TRUE.equals(call(thread, "isAwake"))) continue;
            Object node = call(machine, "getMainNode");
            State state = Boolean.TRUE.equals(call(node, "isActive")) ? State.RUNNING : State.PAUSED;
            lanes.add(ECOProcessingSamples.cycle(machine, output.getId().toString(),
                integer(call(thread, "getCraftingProgress")), 100, 1, state,
                state == State.PAUSED ? Component.translatable("gui.neoecoae.processing.paused") : Component.empty()));
        }
        buffered(machine, output, field(field(machine, "outputBuffer"), "buffer"), lanes);
        return result(machine, lanes);
    }

    private static Observation superMatrix(BlockEntity machine, Object cluster, AEKey output) {
        var lanes = new ArrayList<Machine>();
        int duration = integer(call(call(cluster, "getStats"), "singleCraftTicks"));
        Object node = call(machine, "getMainNode");
        State running = node != null && !Boolean.TRUE.equals(call(node, "isActive")) ? State.PAUSED : State.RUNNING;
        Object batch = field(cluster, "activeBatch");
        if (output.equals(field(batch, "outputKey"))) {
            lanes.add(ECOProcessingSamples.cycle(machine, output.getId().toString(), integer(field(batch, "progress")),
                duration, count(field(batch, "batchSize")), running, Component.empty()));
        }
        Object threads = field(cluster, "fallbackThreads");
        if (threads instanceof Iterable<?> values) for (Object thread : values) {
            if (matches(field(thread, "pattern"), output)) lanes.add(ECOProcessingSamples.cycle(machine,
                output.getId().toString(), integer(field(thread, "progress")), duration,
                count(field(thread, "amount")), running, Component.empty()));
        }
        Object queue = field(cluster, "batchQueue");
        if (queue instanceof Map<?, ?> queued) for (Object task : queued.values()) {
            if (output.equals(field(task, "outputKey")) && count(field(task, "pending")) > 0) {
                // A queued batch is observable, but does not inherit the active batch's timing.
                lanes.add(ECOProcessingSamples.cycle(machine, output.getId().toString(), 0, 0, 0,
                    State.QUEUED, Component.empty()));
                break;
            }
        }
        buffered(machine, output, field(cluster, "outputBuffer"), lanes);
        return result(machine, lanes);
    }

    private static boolean matches(Object pattern, AEKey output) {
        if (pattern instanceof IPatternDetails details) for (var stack : details.getOutputs()) {
            if (stack.amount() > 0 && stack.what().equals(output)) return true;
        }
        return false;
    }

    private static void buffered(BlockEntity machine, AEKey output, Object buffer, List<Machine> lanes) {
        if (buffer instanceof Map<?, ?> values && count(values.get(output)) > 0) lanes.add(ECOProcessingSamples.cycle(
            machine, output.getId().toString(), 100, 100, 1, State.WAITING,
            Component.translatable("gui.neoecoae.processing.output_blocked")));
    }

    private static Observation result(BlockEntity machine, List<Machine> lanes) {
        return lanes.isEmpty() ? Observation.EMPTY : Observation.sample(ECOProcessingSamples.combine(machine, lanes,
            Component.translatable("gui.neoecoae.processing.association.recipe")));
    }
}
