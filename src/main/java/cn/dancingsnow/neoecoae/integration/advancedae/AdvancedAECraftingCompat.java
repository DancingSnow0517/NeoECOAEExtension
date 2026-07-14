package cn.dancingsnow.neoecoae.integration.advancedae;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Method;

public final class AdvancedAECraftingCompat {
    private static final String ADV_CRAFTING_BLOCK_ENTITY =
            "net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity";

    private static final Class<?> ADV_CRAFTING_BLOCK_ENTITY_CLASS;
    private static final Method GET_CLUSTER;
    private static final Method GET_ACTIVE_CPUS;
    private static final Method GET_REMAINING_CAPACITY_CPU;

    static {
        Class<?> blockEntityClass = null;
        Method getCluster = null;
        Method getActiveCpus = null;
        Method getRemainingCapacityCpu = null;

        try {
            blockEntityClass = Class.forName(ADV_CRAFTING_BLOCK_ENTITY);
            getCluster = blockEntityClass.getMethod("getCluster");
            Class<?> clusterClass = getCluster.getReturnType();
            getActiveCpus = clusterClass.getMethod("getActiveCPUs");
            getRemainingCapacityCpu = clusterClass.getMethod("getRemainingCapacityCPU");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            blockEntityClass = null;
            getCluster = null;
            getActiveCpus = null;
            getRemainingCapacityCpu = null;
        }

        ADV_CRAFTING_BLOCK_ENTITY_CLASS = blockEntityClass;
        GET_CLUSTER = getCluster;
        GET_ACTIVE_CPUS = getActiveCpus;
        GET_REMAINING_CAPACITY_CPU = getRemainingCapacityCpu;
    }

    private AdvancedAECraftingCompat() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addCpus(IGrid grid, ImmutableSet.Builder<ICraftingCPU> cpus) {
        if (ADV_CRAFTING_BLOCK_ENTITY_CLASS == null) {
            return;
        }

        try {
            for (Object blockEntity : grid.getMachines((Class) ADV_CRAFTING_BLOCK_ENTITY_CLASS)) {
                Object cluster = GET_CLUSTER.invoke(blockEntity);
                if (cluster == null) {
                    continue;
                }

                Object activeCpus = GET_ACTIVE_CPUS.invoke(cluster);
                if (activeCpus instanceof Iterable<?> iterable) {
                    for (Object cpu : iterable) {
                        addCpu(cpus, cpu);
                    }
                }

                addCpu(cpus, GET_REMAINING_CAPACITY_CPU.invoke(cluster));
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional compatibility: if Advanced AE changes internals, leave AE2's normal list untouched.
        }
    }

    private static void addCpu(ImmutableSet.Builder<ICraftingCPU> cpus, Object cpu) {
        if (cpu instanceof ICraftingCPU craftingCPU) {
            cpus.add(craftingCPU);
        }
    }
}
