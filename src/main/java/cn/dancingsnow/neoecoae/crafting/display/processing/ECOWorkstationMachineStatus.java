package cn.dancingsnow.neoecoae.crafting.display.processing;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Native workstation observation only; no third-party machine adapters or chunk loading. */
public final class ECOWorkstationMachineStatus {
    private ECOWorkstationMachineStatus() {}

    public static List<Target> targets(ICraftingProvider provider) {
        if (provider instanceof LargeWorkstationPatternProvider workstation) {
            var target = workstation.neoecoae$getProcessingTarget();
            return target == null ? List.of() : List.of(target);
        }
        return List.of();
    }

    public static final class Scan {
        private final ArrayDeque<Target> pending = new ArrayDeque<>();
        private final LinkedHashSet<Target> visited = new LinkedHashSet<>();
        private final LinkedHashMap<Target, Machine> machines = new LinkedHashMap<>();
        private final AEKey output;

        public Scan(Iterable<Target> roots, AEKey output) {
            for (var target : roots) pending.add(target);
            this.output = output;
        }

        public boolean complete() { return pending.isEmpty(); }
        public List<Machine> machines() { return List.copyOf(machines.values()); }
        public List<Target> machineTargets() { return List.copyOf(machines.keySet()); }
        public void addRoot(Target target) { if (!visited.contains(target)) pending.add(target); }

        public void step(Level context) {
            if (pending.isEmpty() || context == null || context.isClientSide || context.getServer() == null) return;
            var target = pending.removeFirst();
            if (!visited.add(target)) return;
            var level = context.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, target.dimension()));
            if (level == null || !level.hasChunkAt(target.position())) return;
            if (level.getBlockEntity(target.position()) instanceof ECOLargeIntegratedWorkingStationBlockEntity workstation
                    && !workstation.isRemoved()) {
                machines.put(target, workstation.getProcessingStatus(output));
            }
        }
    }
}
