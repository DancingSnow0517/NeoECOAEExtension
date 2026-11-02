package cn.dancingsnow.neoecoae.grid;

import appeng.api.networking.IGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Grid machine lookups that also find subclasses of the requested type.
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
public final class NEMachineLookup {
    private NEMachineLookup() {
    }

    public static <T> Collection<T> getMachines(IGrid grid, Class<T> type) {
        List<T> machines = new ArrayList<>();
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!type.isAssignableFrom(machineClass)) {
                continue;
            }
            for (Object machine : grid.getMachines(machineClass)) {
                if (type.isInstance(machine)) {
                    machines.add(type.cast(machine));
                }
            }
        }
        return machines;
    }
}
