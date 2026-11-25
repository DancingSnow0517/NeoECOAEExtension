package cn.dancingsnow.neoecoae.integration.ae2;

import appeng.api.networking.IGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Finds grid machines of the requested type, including its subclasses.
 *
 * <p>AE2 files every node under its owner's exact runtime class -- {@code appeng.me.Grid#add} performs a
 * single {@code machines.put(node.getOwner().getClass(), node)} and never walks the class hierarchy -- so
 * {@link IGrid#getMachines(Class)} answers only that one class and nothing below it. An addon that extends
 * one of our block entities is therefore invisible to a plain {@code getMachines} call, and whatever
 * depended on the lookup simply does not happen: no exception, no log line, the machine just behaves as if
 * it were not on the grid.
 *
 * <p>This walks the grid's machine classes and keeps the keys the requested type covers, which gives the
 * same result as an {@code instanceof} filter over every owner without visiting unrelated keys.
 */
public final class ECOGridMachineFinder {
    private ECOGridMachineFinder() {
    }

    public static <T> Collection<T> getMachines(IGrid grid, Class<T> type) {
        return findMachines(grid, type, IGrid::getMachines);
    }

    public static <T> Collection<T> getActiveMachines(IGrid grid, Class<T> type) {
        return findMachines(grid, type, IGrid::getActiveMachines);
    }

    private static <T> Collection<T> findMachines(IGrid grid, Class<T> type,
            BiFunction<IGrid, Class<?>, Collection<?>> lookup) {
        List<T> machines = new ArrayList<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!type.isAssignableFrom(machineClass)) {
                continue;
            }
            for (Object machine : lookup.apply(grid, machineClass)) {
                if (type.isInstance(machine)) {
                    machines.add(type.cast(machine));
                }
            }
        }
        return machines;
    }
}
