package cn.dancingsnow.neoecoae.crafting.display.processing;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusRegistry;
import cn.dancingsnow.neoecoae.compat.processing.AssemblerMatrixProcessingStatus;
import cn.dancingsnow.neoecoae.compat.processing.MekanismProcessingStatus;
import cn.dancingsnow.neoecoae.compat.processing.ProcessingReflection;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Budgeted destination traversal shared by native machines and optional readers. Never loads chunks. */
public final class ECOWorkstationMachineStatus {
    private ECOWorkstationMachineStatus() {}
    private static final List<ECOProcessingStatusReader> BUILTINS = List.of(
        new ECOFxProcessingStatus(), new AssemblerMatrixProcessingStatus(), new MekanismProcessingStatus());
    private static final java.util.Set<Class<?>> FAILED_READERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static List<Target> targets(ICraftingProvider provider) {
        if (provider instanceof ECOProcessingTargetProvider observable) {
            var target = observable.neoecoae$getProcessingTarget();
            return target == null ? List.of() : List.of(target);
        }
        Object logic = ProcessingReflection.call(provider, "getLogic");
        if (logic instanceof ECOProcessingTargetProvider observable) {
            var target = observable.neoecoae$getProcessingTarget();
            return target == null ? List.of() : List.of(target);
        }
        // Matrix providers own their executor cluster, rather than pushing to an adjacent inventory.
        if (provider instanceof net.minecraft.world.level.block.entity.BlockEntity machine
                && machine.getLevel() != null && BUILTINS.get(1).supports(machine)) {
            return List.of(new Target(machine.getLevel().dimension().location(), machine.getBlockPos()));
        }
        return List.of();
    }

    public static final class Scan {
        private final ArrayDeque<Target> pending = new ArrayDeque<>();
        private final LinkedHashSet<Target> visited = new LinkedHashSet<>();
        private final LinkedHashMap<Target, Machine> machines = new LinkedHashMap<>();
        private final AEKey output;
        private final java.util.UUID jobId;

        public Scan(Iterable<Target> roots, AEKey output) {
            this(roots, output, null);
        }

        public Scan(Iterable<Target> roots, AEKey output, java.util.UUID jobId) {
            for (var target : roots) addRoot(target);
            this.output = output;
            this.jobId = jobId;
        }

        public boolean complete() { return pending.isEmpty(); }
        public List<Machine> machines() { return List.copyOf(machines.values()); }
        public List<Target> machineTargets() { return List.copyOf(machines.keySet()); }
        public void addRoot(Target target) { if (visited.add(target)) pending.add(target); }

        public void step(Level context) {
            if (pending.isEmpty() || context == null || context.isClientSide || context.getServer() == null) return;
            var target = pending.removeFirst();
            var level = context.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, target.dimension()));
            if (level == null || !level.hasChunkAt(target.position())) return;
            var machine = level.getBlockEntity(target.position());
            if (machine == null || machine.isRemoved()) return;
            var contextInfo = new ECOProcessingStatusReader.Context(output, jobId);
            for (var reader : ECOProcessingStatusRegistry.readers()) {
                if (read(reader, machine, target, contextInfo)) return;
            }
            if (machine instanceof ECOLargeIntegratedWorkingStationBlockEntity workstation) {
                machines.put(target, workstation.getProcessingStatus(output));
                return;
            }
            for (var reader : BUILTINS) {
                if (read(reader, machine, target, contextInfo)) return;
            }
        }

        private boolean read(ECOProcessingStatusReader reader, net.minecraft.world.level.block.entity.BlockEntity machine,
                Target target, ECOProcessingStatusReader.Context context) {
            try {
                if (!reader.supports(machine)) return false;
                accept(target, reader.read(machine, context));
                return true;
            } catch (RuntimeException | LinkageError incompatible) {
                if (FAILED_READERS.add(reader.getClass())) org.slf4j.LoggerFactory.getLogger("neoecoae").warn(
                    "Processing reader {} could not observe {}; crafting is unaffected", reader.getClass().getName(), target, incompatible);
                return false;
            }
        }

        private void accept(Target target, ECOProcessingStatusReader.Observation observation) {
            if (observation.sample() != null) machines.put(target, observation.sample());
            for (var link : observation.links()) addRoot(link);
        }
    }
}
