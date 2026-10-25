package cn.dancingsnow.neoecoae.crafting.display.processing;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Summary of every associated machine cycle, with a separately paged tooltip detail list. */
public record ECOProcessingProgress(float progress, boolean hasProgress, State state, int machineCount,
        int detailPage, boolean refreshing, List<Machine> machines) {
    public static final int DETAIL_PAGE_SIZE = 3;
    public static final ECOProcessingProgress EMPTY = new ECOProcessingProgress(0, false, State.QUEUED, 0, -1, false, List.of());

    public ECOProcessingProgress {
        progress = Float.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
        machines = List.copyOf(machines);
    }

    public static ECOProcessingProgress summarize(List<Machine> machines) {
        double progress = 0, duration = 0;
        boolean running = false, waiting = false, paused = false;
        for (var machine : machines) {
            progress += machine.progressTicks();
            duration += machine.durationTicks();
            running |= machine.state() == State.RUNNING;
            waiting |= machine.state() == State.WAITING;
            paused |= machine.state() == State.PAUSED;
        }
        State state = waiting ? State.WAITING : running ? State.RUNNING : paused ? State.PAUSED : State.QUEUED;
        return new ECOProcessingProgress(duration == 0 ? 0 : (float) (progress / duration), duration > 0,
            state, machines.size(), 0, false, machines);
    }

    public ECOProcessingProgress refreshing(boolean value) {
        return value == refreshing ? this
            : new ECOProcessingProgress(progress, hasProgress, state, machineCount, detailPage, value, machines);
    }

    /** Negative page means a summary only. Paging never changes the all-machine aggregate. */
    public ECOProcessingProgress page(int requested) {
        int page = requested < 0 || machines.isEmpty() ? -1 : Math.min(requested, (machines.size() - 1) / DETAIL_PAGE_SIZE);
        int start = page < 0 ? 0 : page * DETAIL_PAGE_SIZE;
        return new ECOProcessingProgress(progress, hasProgress, state, machineCount, page, refreshing,
            page < 0 ? List.of() : machines.subList(start, Math.min(machines.size(), start + DETAIL_PAGE_SIZE)));
    }

    public int pages() { return Math.max(1, (machineCount + DETAIL_PAGE_SIZE - 1) / DETAIL_PAGE_SIZE); }

    public CompoundTag write(net.minecraft.core.HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putFloat("progress", progress);
        tag.putBoolean("hasProgress", hasProgress);
        tag.putString("state", state.name());
        tag.putInt("count", machineCount);
        tag.putInt("page", detailPage);
        tag.putBoolean("refreshing", refreshing);
        var list = new ListTag();
        for (var machine : machines) {
            var entry = new CompoundTag();
            entry.putString("name", Component.Serializer.toJson(machine.name(), registries));
            entry.putString("location", machine.location());
            entry.putString("recipe", machine.recipe());
            entry.putInt("progress", machine.progressTicks());
            entry.putInt("duration", machine.durationTicks());
            entry.putLong("parallel", machine.parallel());
            entry.putString("state", machine.state().name());
            entry.putString("reason", Component.Serializer.toJson(machine.reason(), registries));
            list.add(entry);
        }
        tag.put("machines", list);
        return tag;
    }

    public static ECOProcessingProgress read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (tag.isEmpty()) return EMPTY;
        var list = tag.getList("machines", Tag.TAG_COMPOUND);
        var machines = new java.util.ArrayList<Machine>();
        for (int i = 0; i < Math.min(DETAIL_PAGE_SIZE, list.size()); i++) {
            var entry = list.getCompound(i);
            machines.add(new Machine(component(entry.getString("name"), registries), entry.getString("location"),
                entry.getString("recipe"), entry.getInt("progress"), entry.getInt("duration"),
                entry.getLong("parallel"), state(entry.getString("state")), component(entry.getString("reason"), registries)));
        }
        return new ECOProcessingProgress(tag.getFloat("progress"), tag.getBoolean("hasProgress"),
            state(tag.getString("state")), Math.max(0, tag.getInt("count")), tag.getInt("page"),
            tag.getBoolean("refreshing"), machines);
    }

    private static State state(String value) {
        try { return State.valueOf(value); }
        catch (IllegalArgumentException invalid) { return State.UNAVAILABLE; }
    }

    private static Component component(String json, net.minecraft.core.HolderLookup.Provider registries) {
        if (json.isEmpty()) return Component.empty();
        Component value = Component.Serializer.fromJson(json, registries);
        return value == null ? Component.empty() : value;
    }

    public enum State { RUNNING, WAITING, PAUSED, QUEUED, UNAVAILABLE }

    public record Machine(Component name, String location, String recipe, int progressTicks,
            int durationTicks, long parallel, State state, Component reason) {
        public Machine {
            durationTicks = Math.max(0, durationTicks);
            progressTicks = Math.max(0, Math.min(durationTicks, progressTicks));
            parallel = Math.max(0, parallel);
            name = name.copy();
            reason = reason.copy();
        }
    }
}
