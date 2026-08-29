package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class GuiLangs {
    public static void accept(RegistrateLangProvider provider) {
        // integrated working station
        provider.add("gui.neoecoae.integrated_working_station.energy", "Used Energy: %dk FE");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs", "Output Sides");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs.enabled", "Enabled");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs.disabled", "Disabled");
        provider.add("gui.neoecoae.multiblock.builder", "Structure Builder");
        provider.add("gui.neoecoae.multiblock.close_builder", "Close builder");
        provider.add("gui.neoecoae.multiblock.decrease_length", "Decrease length");
        provider.add("gui.neoecoae.multiblock.increase_length", "Increase length");
        provider.add("gui.neoecoae.multiblock.length", "Length: %d");
        provider.add("gui.neoecoae.multiblock.mirror", "Mirror");
        provider.add("gui.neoecoae.multiblock.mirror.off", "Off");
        provider.add("gui.neoecoae.multiblock.mirror.on", "On");
        provider.add("gui.neoecoae.multiblock.mirror.off.tooltip", "Build without mirroring");
        provider.add("gui.neoecoae.multiblock.mirror.on.tooltip", "Build mirrored structure");
        provider.add("gui.neoecoae.multiblock.build", "Build");
        provider.add("gui.neoecoae.multiblock.reused", "Reused: %d");
        provider.add("gui.neoecoae.multiblock.missing", "Missing: %d");
        provider.add("gui.neoecoae.multiblock.conflicts", "Conflicts: %d");
        provider.add("gui.neoecoae.multiblock.required_items", "Required Items: %d");
        provider.add("gui.neoecoae.multiblock.parameters", "Build Parameters");
        provider.add("gui.neoecoae.multiblock.live_result", "Live Result");
        provider.add("gui.neoecoae.multiblock.actions", "Actions");
        provider.add("gui.neoecoae.multiblock.auto_preview_hint", "Changes refresh automatically.");
        provider.add("gui.neoecoae.multiblock.materials", "Materials");
        provider.add("gui.neoecoae.multiblock.material_enough", "Enough materials");
        provider.add("gui.neoecoae.multiblock.material_missing", "Not enough materials");
        provider.add("gui.neoecoae.multiblock.item_required", "Required: %d");
        provider.add("gui.neoecoae.multiblock.conflict_preview", "Conflict Preview");
        provider.add("gui.neoecoae.multiblock.no_conflicts", "No conflicts");
        provider.add("gui.neoecoae.multiblock.conflict_positions", "Conflict positions");
        provider.add("gui.neoecoae.multiblock.more_conflicts", "...and %d more");
        provider.add("gui.neoecoae.multiblock.status.controller_formed", "Controller already formed");
        provider.add("gui.neoecoae.multiblock.status.no_definition", "No structure definition");
        provider.add("gui.neoecoae.multiblock.status.structure_ready", "Structure ready");
        provider.add("gui.neoecoae.multiblock.status.ready_to_build", "Ready to build");
        provider.add("gui.neoecoae.multiblock.status.not_enough_items", "Not enough items");
        provider.add("gui.neoecoae.multiblock.status.conflicts_detected", "Conflicts detected");
        provider.add("gui.neoecoae.multiblock.status.build_in_progress", "Build in progress");
        provider.add("gui.neoecoae.relative_side.front", "Front");
        provider.add("gui.neoecoae.relative_side.back", "Back");
        provider.add("gui.neoecoae.relative_side.left", "Left");
        provider.add("gui.neoecoae.relative_side.right", "Right");
        provider.add("gui.neoecoae.relative_side.top", "Top");
        provider.add("gui.neoecoae.relative_side.bottom", "Bottom");
        provider.add("gui.neoecoae.common.yes", "Yes");
        provider.add("gui.neoecoae.common.no", "No");
        provider.add("gui.neoecoae.common.on", "On");
        provider.add("gui.neoecoae.common.off", "Off");
        provider.add("gui.neoecoae.machine.formed", "Formed");

        // storage
        provider.add("gui.neoecoae.storage.energy", "Energy Monitoring");
        provider.add("gui.neoecoae.storage.energy_storage", "Energy Storage");
        provider.add("gui.neoecoae.storage.bytes_used", "bytes used");
        provider.add("gui.neoecoae.storage.system_load", "System Load");
        provider.add("gui.neoecoae.storage.current_load", "Current Load");
        provider.add("gui.neoecoae.storage.max_load", "Max Load");
        provider.add("gui.neoecoae.storage.status", "Status");
        provider.add("gui.neoecoae.storage.status.full", "%s capacity full");
        provider.add("gui.neoecoae.storage.status.high", "%s near capacity");
        provider.add("gui.neoecoae.storage.status.warning", "%s pressure rising");
        provider.add("gui.neoecoae.storage.status.stable", "Stable");
        provider.add("gui.neoecoae.storage.idle_matrices", "Idle Matrices");
        provider.add("gui.neoecoae.storage.infinite_value", "infinite");
        provider.add("gui.neoecoae.common.types", "types");
        provider.add("gui.neoecoae.host.crafting.subtitle", "Crafting System Host");
        provider.add("gui.neoecoae.storage_priority.open", "Open priority panel");
        provider.add("gui.neoecoae.storage_priority.close", "Close priority panel");
        provider.add("gui.neoecoae.host.metric.types", "Types");
        provider.add("gui.neoecoae.host.metric.bytes", "Bytes");

        // computation
        provider.add("gui.neoecoae.host.computation.cpu_storage", "CPU Storage");
        provider.add("gui.neoecoae.host.computation.thread_usage", "Thread Usage");
        provider.add("gui.neoecoae.host.computation.parallel_count", "Parallel Count");
        provider.add("gui.neoecoae.host.computation.capacity", "Computation Capacity");
        provider.add("gui.neoecoae.host.computation.free_memory", "Free CPU Memory");

        // crafting
        provider.add("gui.neoecoae.crafting.tasks", "Crafting Tasks");
        provider.add("gui.neoecoae.crafting.no_tasks", "No active tasks");
        provider.add("gui.neoecoae.crafting.ui.status", "Status");
        provider.add("gui.neoecoae.crafting.ui.stats", "Stats");
        provider.add("gui.neoecoae.crafting.ui.energy_cooling", "Energy / Cooling");
        provider.add("gui.neoecoae.crafting.ui.overclock_short", "OC");
        provider.add("gui.neoecoae.crafting.ui.cooling_short", "Cool");
        provider.add("gui.neoecoae.crafting.ui.fx_cores", "FX Cores");
        provider.add("gui.neoecoae.crafting.ui.single_core_capacity", "Single-Core Capacity");
        provider.add("gui.neoecoae.crafting.ui.recipe_time_ratio", "Recipe Time");
        provider.add("gui.neoecoae.crafting.ui.stats.tooltip.intro.0", "FX cores store and execute independent crafting tasks.");
        provider.add("gui.neoecoae.crafting.ui.stats.tooltip.intro.1", "Each standard FX core stores up to 32 tasks.");
        provider.add("gui.neoecoae.crafting.ui.stats.tooltip.host_details", "Per-Host Batch Details");
        provider.add("gui.neoecoae.crafting.ui.stats.tooltip.fx_cores", "Active FX cores: %d / %d");
        provider.add("gui.neoecoae.crafting.ui.stats.tooltip.tasks", "Task usage: %d / %d");
        provider.add("gui.neoecoae.crafting.ui.stats.tooltip.total", "Maximum crafting efficiency: %d");
        provider.add("gui.neoecoae.crafting.ui.stats.tooltip.host", "Host %d: %d FX cores, single-core batch %d");
        provider.add("gui.neoecoae.crafting.ui.energy_usage", "Energy Usage");
        provider.add("gui.neoecoae.crafting.performance", "Performance");
        provider.add("gui.neoecoae.crafting.task.status.running", "Running");
        provider.add("gui.neoecoae.crafting.task.status.queued", "Queued");
        provider.add("gui.neoecoae.crafting.task.status.waiting_output", "Waiting for output");
        provider.add("gui.neoecoae.crafting.overclock.on", "Disable Overclock");
        provider.add("gui.neoecoae.crafting.overclock.off", "Enable Overclock");
        provider.add("gui.neoecoae.crafting.active_cooling.on", "Disable Active Cooling");
        provider.add("gui.neoecoae.crafting.active_cooling.off", "Enable Active Cooling");
        provider.add("gui.neoecoae.host.network_frequency.cycle", "Cycle Network Frequency (Current: %d)");
        provider.add("gui.neoecoae.host.network_frequency.cycle.unassigned", "Cycle Network Frequency (Unassigned)");
        provider.add("gui.neoecoae.host.network.mode.local", "LOCAL x1");
        provider.add("gui.neoecoae.host.network.mode.normal", "NETWORK SWITCH x2");
        provider.add("gui.neoecoae.host.network.mode.high_energy", "HIGH-ENERGY x8");
        provider.add("gui.neoecoae.host.network.connected", "NETWORK ONLINE");
        provider.add("gui.neoecoae.host.network.disconnected", "NETWORK OFFLINE");
        provider.add("gui.neoecoae.crafting.coolant_max_overclock", "Current Coolant Max Overclock: %d");
        provider.add("gui.neoecoae.host.crafting.overflow", "Overflow");
        provider.add("gui.neoecoae.host.crafting.coolant", "Coolant");
        provider.add("gui.neoecoae.storage_interface.title", "Storage Interface");
        provider.add("gui.neoecoae.storage_interface.mode.storage", "Storage");
        provider.add("gui.neoecoae.storage_interface.mode.input", "Input");
        provider.add("gui.neoecoae.storage_interface.mode.output", "Output");
        provider.add("gui.neoecoae.storage_interface.structure", "Infinite Storage");
        provider.add("gui.neoecoae.storage_interface.infinite_ready", "Ready");
        provider.add("gui.neoecoae.storage_interface.infinite_unavailable", "Unavailable");
        provider.add("gui.neoecoae.storage_interface.network", "Network");
        provider.add("gui.neoecoae.storage_interface.connected", "Connected");
        provider.add("gui.neoecoae.storage_interface.disconnected", "Disconnected");
        provider.add("gui.neoecoae.storage_interface.transfer", "Transferred: %s / tick");
        provider.add("gui.neoecoae.storage_interface.transfer_prefix", "Transferred: ");
        provider.add("gui.neoecoae.storage_interface.transfer_suffix", " / tick");

        // crafting interface
        provider.add("gui.neoecoae.crafting_interface.title", "Crafting Interface");
        provider.add("gui.neoecoae.crafting_interface.preview.search", "Search ingredients or outputs");
        provider.add("gui.neoecoae.crafting_interface.preview.search.tooltip",
            "Searches pattern ingredients and outputs. Separate terms with spaces; right-click to clear.");
        provider.add("gui.neoecoae.crafting_interface.preview.filter_substitutions", "Show/hide substitution patterns");
        provider.add("gui.neoecoae.crafting_interface.preview.filter_fluid_substitutions", "Show/hide fluid substitution patterns");
        provider.add("gui.neoecoae.crafting_interface.preview.organize", "Organize pattern buses");
        provider.add("gui.neoecoae.crafting_interface.preview.organizing", "Organizing pattern buses %d%%");
        provider.add("gui.neoecoae.crafting_interface.preview.organize.result_primary",
            "Organization complete: recovered %d invalid patterns, %d duplicate patterns");
        provider.add("gui.neoecoae.crafting_interface.preview.organize.result_secondary",
            "Inventory space insufficient, %d patterns remain to be recovered");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer", "Transfer Network Patterns");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.indexing", "Indexing %d%%");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.progress", "Transferring %d%%");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.ready", "Ready to transfer");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.unavailable",
            "Crafting subsystem is not connected to an available network");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.no_target", "No available pattern bus found");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.result_primary", "Transferred %d; Already present %d");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.result_secondary", "No space %d; Incompatible %d");

        // computation interface
        provider.add("gui.neoecoae.computation_interface.hint", "Mark items to ignore component differences when planning");
    }
}
