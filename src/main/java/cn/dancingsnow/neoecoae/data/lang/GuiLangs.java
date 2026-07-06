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
        provider.add("gui.neoecoae.multiblock.preview", "Preview");
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
        provider.add("gui.neoecoae.multiblock.status.idle", "Idle");
        provider.add("gui.neoecoae.multiblock.status.length_updated", "Length updated");
        provider.add("gui.neoecoae.multiblock.status.mirror_updated", "Mirror option updated");
        provider.add("gui.neoecoae.multiblock.status.controller_formed", "Controller already formed");
        provider.add("gui.neoecoae.multiblock.status.no_definition", "No structure definition");
        provider.add("gui.neoecoae.multiblock.status.structure_ready", "Structure ready");
        provider.add("gui.neoecoae.multiblock.status.ready_to_build", "Ready to build");
        provider.add("gui.neoecoae.multiblock.status.not_enough_items", "Not enough items");
        provider.add("gui.neoecoae.multiblock.status.conflicts_detected", "Conflicts detected");
        provider.add("gui.neoecoae.multiblock.status.build_in_progress", "Build in progress");
        provider.add("gui.neoecoae.multiblock.status.build_already_in_progress", "Build already in progress");
        provider.add("gui.neoecoae.multiblock.status.build_complete", "Build complete");
        provider.add("gui.neoecoae.multiblock.status.build_interrupted", "Build interrupted");
        provider.add("gui.neoecoae.multiblock.status.builder_unavailable", "Builder unavailable");
        provider.add("gui.neoecoae.multiblock.status.build_failed", "Build failed");
        provider.add("gui.neoecoae.multiblock.status.building", "Building %d/%d");
        provider.add("gui.neoecoae.relative_side.front", "Front");
        provider.add("gui.neoecoae.relative_side.back", "Back");
        provider.add("gui.neoecoae.relative_side.left", "Left");
        provider.add("gui.neoecoae.relative_side.right", "Right");
        provider.add("gui.neoecoae.relative_side.top", "Top");
        provider.add("gui.neoecoae.relative_side.bottom", "Bottom");

        // storage
        provider.add("gui.neoecoae.storage.energy", "Energy Monitoring");
        provider.add("gui.neoecoae.storage.energy_status", "Energy Storage: %sAE / %sAE (%d%%)");
        provider.add("gui.neoecoae.storage.energy_storage", "Energy Storage");
        provider.add("gui.neoecoae.storage.bytes_used", "bytes used");
        provider.add("gui.neoecoae.storage.used_short", "Used");
        provider.add("gui.neoecoae.common.types", "types");
        provider.add("gui.neoecoae.host.status.online", "ONLINE");
        provider.add("gui.neoecoae.host.status.running", "RUNNING");
        provider.add("gui.neoecoae.host.storage.subtitle", "Storage System Host");
        provider.add("gui.neoecoae.host.computation.subtitle", "Computation System Host");
        provider.add("gui.neoecoae.host.crafting.subtitle", "Crafting System Host");
        provider.add("gui.neoecoae.host.storage.type_usage", "Type Usage");
        provider.add("gui.neoecoae.host.storage.storage_usage", "Storage Usage");
        provider.add("gui.neoecoae.host.storage.energy_buffer", "Energy Buffer");
        provider.add("gui.neoecoae.host.storage.channels", "Storage Channels");
        provider.add("gui.neoecoae.host.storage.footer", "Dynamic by ECOCellType registry; storage channels scroll when expanded.");
        provider.add("gui.neoecoae.storage_priority.title", "Priority");
        provider.add("gui.neoecoae.storage_priority.open", "Open priority panel");
        provider.add("gui.neoecoae.storage_priority.close", "Close priority panel");
        provider.add("gui.neoecoae.storage_priority.insert_hint", "When inserting: higher-priority storage is preferred.");
        provider.add("gui.neoecoae.storage_priority.extract_hint", "When extracting: lower-priority storage is preferred.");
        provider.add("gui.neoecoae.host.metric.types", "Types");
        provider.add("gui.neoecoae.host.metric.bytes", "Bytes");

        // computation
        provider.add("gui.neoecoae.computation.thread_info", "Thread Used: %d / %d");
        provider.add("gui.neoecoae.computation.parallel_info", "Parallel Count: %d");
        provider.add("gui.neoecoae.computation.storage_info", "Storage Used: %s / %s");
        provider.add("gui.neoecoae.host.computation.cpu_storage", "CPU Storage");
        provider.add("gui.neoecoae.host.computation.thread_usage", "Thread Usage");
        provider.add("gui.neoecoae.host.computation.parallel_count", "Parallel Count");
        provider.add("gui.neoecoae.host.computation.capacity", "Computation Capacity");
        provider.add("gui.neoecoae.host.computation.active_vcpu", "Active vCPU");
        provider.add("gui.neoecoae.host.computation.max_vcpu", "Max vCPU");
        provider.add("gui.neoecoae.host.computation.accelerators", "Accelerators");
        provider.add("gui.neoecoae.host.computation.free_memory", "Free CPU Memory");
        provider.add("gui.neoecoae.host.computation.cpu_pool", "Crafting CPU Pool");
        provider.add("gui.neoecoae.host.computation.cpu_pool_hint", "Threads expose virtual crafting CPUs to the AE network.");
        provider.add("gui.neoecoae.host.computation.footer", "Single-screen computation status.");

        // crafting
        provider.add("gui.neoecoae.crafting.pattern_bus_count", "Pattern Buses: %d");
        provider.add("gui.neoecoae.crafting.parallel_core_count", "Parallel Cores: %d");
        provider.add("gui.neoecoae.crafting.worker_count", "Worker Cores: %d");
        provider.add("gui.neoecoae.crafting.working_threads", "Working Threads: %d / %d (%d%%)");
        provider.add("gui.neoecoae.crafting.total_parallelism", "Total Parallelism: %d");
        provider.add("gui.neoecoae.crafting.total_parallelism.overflow", "Overflow: %d (%d%%)");
        provider.add("gui.neoecoae.crafting.max_energy_usage", "Max Energy Usage: §b%s AE");
        provider.add("gui.neoecoae.crafting.overclock_status", "Theoretical Overclock: %d, Effective Overclock: %d");
        provider.add("gui.neoecoae.crafting.overclock_status.disabled", "Theoretical Overclock: 0, Effective Overclock: 0");
        provider.add("gui.neoecoae.crafting.enable_overlock", "Enable Overlock: ");
        provider.add("gui.neoecoae.crafting.overclocked.tooltip", "Boosting performance within a limited range while consuming more §cEnergy§f.");
        provider.add("gui.neoecoae.crafting.enable_active_cooling", "Enable Active Cooling: ");
        provider.add("gui.neoecoae.crafting.active_cooling.tooltip", "Consumes coolant from the fluid input hatch to enhance performance and eliminate the additional energy cost of overclocking.\nUsable coolants can be looked up in JEI.\nIf the machine's coolant level is insufficient during operation, it will stop running.\nIf the fluid output hatch is full, coolant cannot be consumed from the fluid input hatch, preventing the machine from replenishing its coolant supply.");
        provider.add("gui.neoecoae.crafting.clear_coolant", "Clear");
        provider.add("gui.neoecoae.crafting.clear_coolant.tooltip", "Clears the cached coolant so you can switch to a different coolant.");
        provider.add("gui.neoecoae.crafting.coolant_max_overclock", "Current Coolant Max Overclock: %d");
        provider.add("gui.neoecoae.crafting.coolant_max_overclock.none", "Current Coolant Max Overclock: None");
        provider.add("gui.neoecoae.host.crafting.working_threads", "Working Threads");
        provider.add("gui.neoecoae.host.crafting.total_parallelism", "Total Parallelism");
        provider.add("gui.neoecoae.host.crafting.max_energy_usage", "Max Energy Usage");
        provider.add("gui.neoecoae.host.crafting.runtime", "Crafting Runtime");
        provider.add("gui.neoecoae.host.crafting.pattern_buses", "Pattern Buses");
        provider.add("gui.neoecoae.host.crafting.parallel_cores", "Parallel Cores");
        provider.add("gui.neoecoae.host.crafting.worker_cores", "Worker Cores");
        provider.add("gui.neoecoae.host.crafting.overflow", "Overflow");
        provider.add("gui.neoecoae.host.crafting.overclock_cooling", "Overclock & Cooling");
        provider.add("gui.neoecoae.host.crafting.overclock_summary", "Theoretical %d, effective %d, coolant max %s.");
        provider.add("gui.neoecoae.host.crafting.coolant", "Coolant");
        provider.add("gui.neoecoae.host.crafting.energy", "Energy");
        provider.add("gui.neoecoae.host.crafting.footer", "Single-screen crafting status and controls.");
    }
}
