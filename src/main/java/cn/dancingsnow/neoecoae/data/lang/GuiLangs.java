package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class GuiLangs {
    public static void accept(RegistrateLangProvider provider) {
        // common UI labels
        provider.add("gui.neoecoae.common.input", "Input");
        provider.add("gui.neoecoae.common.output", "Output");
        provider.add("gui.neoecoae.common.upgrades", "Upgrades");
        provider.add("gui.neoecoae.common.status", "Status");
        provider.add("gui.neoecoae.common.enabled", "Enabled");
        provider.add("gui.neoecoae.common.disabled", "Disabled");
        provider.add("gui.neoecoae.common.formed", "Formed");
        provider.add("gui.neoecoae.common.tier", "Tier");
        provider.add("gui.neoecoae.common.bytes", "Bytes");
        provider.add("gui.neoecoae.common.energy", "Energy");
        provider.add("gui.neoecoae.common.threads", "Threads");
        provider.add("gui.neoecoae.common.parallel", "Parallel");
        provider.add("gui.neoecoae.common.types", "Types");
        provider.add("gui.neoecoae.common.progress", "Progress");
        provider.add("gui.neoecoae.common.fluid", "Fluid");
        provider.add("gui.neoecoae.common.amount", "Amount");
        provider.add("gui.neoecoae.common.coolant", "Coolant");
        provider.add("gui.neoecoae.common.overclock", "Overclock");
        provider.add("gui.neoecoae.common.active_cooling", "Active Cooling");
        provider.add("gui.neoecoae.common.input_fluid", "In Fluid");
        provider.add("gui.neoecoae.common.output_fluid", "Out Fluid");
        provider.add("gui.neoecoae.common.multiblock_builder", "Multiblock Builder");
        provider.add("gui.neoecoae.common.show_builder", "Show Builder");
        provider.add("gui.neoecoae.common.hide_builder", "Hide Builder");
        provider.add("gui.neoecoae.common.close", "Close");
        provider.add("gui.neoecoae.pattern_bus.patterns", "Patterns");
        provider.add("gui.neoecoae.pattern_bus.patterns_page", "Patterns %d - %d");

        // short controller titles (LDLib2-style three-zone layout)
        provider.add("gui.neoecoae.ui.storage_system.short", "ECO - %s Storage System");
        provider.add("gui.neoecoae.ui.computation_system.short", "ECO - %s Computation System");
        // legacy keys kept for backward compatibility
        provider.add("gui.neoecoae.ui.storage_subsystem.short", "ECO - %s Storage Subsystem");
        provider.add("gui.neoecoae.ui.computation_subsystem.short", "ECO - %s Computation Subsystem");
        provider.add("gui.neoecoae.ui.crafting_controller.short", "ECO - %s Crafting Controller");

        // ===== Temporary minimal UI =====
        provider.add("gui.neoecoae.ui.rebuilding", "UI rebuilding");

        // ECO CPU
        provider.add("gui.neoecoae.cpu.eco", "%s ECO CPU");
        provider.add("gui.neoecoae.cpu.eco_with_storage", "%s ECO CPU (%s)");
        provider.add("gui.neoecoae.cpu.storage", "%s Storage");
        provider.add("gui.neoecoae.cpu.coprocessors", "%s Co-processors");

        // integrated working station
        provider.add("gui.neoecoae.integrated_working_station.energy", "Required Energy: %s kAE");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs", "Output Sides");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs.enabled", "Enabled");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs.disabled", "Disabled");
        provider.add("gui.neoecoae.migration_ui.no_ldlib1_ui", "No LDLib1 UI is implemented for this machine");
        provider.add("gui.neoecoae.multiblock.builder", "Structure Builder");
        provider.add("gui.neoecoae.multiblock.close_builder", "Close builder");
        provider.add("gui.neoecoae.multiblock.decrease_length", "Decrease length");
        provider.add("gui.neoecoae.multiblock.increase_length", "Increase length");
        provider.add("gui.neoecoae.multiblock.length", "Length: %d");
        provider.add("gui.neoecoae.multiblock.preview", "Preview");
        provider.add("gui.neoecoae.multiblock.build", "Build");
        provider.add("gui.neoecoae.multiblock.reused", "Reused: %d");
        provider.add("gui.neoecoae.multiblock.missing", "Missing: %d");
        provider.add("gui.neoecoae.multiblock.conflicts", "Conflicts: %d");
        provider.add("gui.neoecoae.multiblock.required_items", "Required Items: %d");
        provider.add("emi.neoecoae.multiblock.requirements", "Block Requirements");
        provider.add("emi.neoecoae.multiblock.change_length", "Change structure length");
        provider.add("emi.neoecoae.multiblock.show_all_layers", "Show all layers");
        provider.add("emi.neoecoae.multiblock.show_layer", "Show layer %s");
        provider.add("emi.neoecoae.multiblock.show_formed", "Show formed state");
        provider.add("emi.neoecoae.multiblock.show_unformed", "Show unformed state");
        provider.add("emi.neoecoae.multiblock.previous_page", "Previous page");
        provider.add("emi.neoecoae.multiblock.next_page", "Next page");
        provider.add("emi.neoecoae.multiblock.drag_rotate", "Hold left mouse button to rotate");
        provider.add("emi.neoecoae.multiblock.empty_scene", "No structure data");
        provider.add("gui.neoecoae.structure_terminal.target.crafting", "Craft");
        provider.add("gui.neoecoae.structure_terminal.target.storage", "Storage");
        provider.add("gui.neoecoae.structure_terminal.target.computation", "Compute");
        provider.add("gui.neoecoae.structure_terminal.target.crafting.tooltip", "Crafting Subsystem");
        provider.add("gui.neoecoae.structure_terminal.target.storage.tooltip", "Storage Subsystem");
        provider.add("gui.neoecoae.structure_terminal.target.computation.tooltip", "Computation Subsystem");
        provider.add("gui.neoecoae.multiblock.status.idle", "Idle");
        provider.add("gui.neoecoae.multiblock.status.length_updated", "Length updated");
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
        provider.add("gui.neoecoae.storage.energy_status", "Energy Storage: %s / %s (%s%%)");

        // computation
        provider.add("gui.neoecoae.computation.thread_info", "Thread Used: %s / %s");
        provider.add("gui.neoecoae.computation.parallel_info", "Parallel Count: %s");
        provider.add("gui.neoecoae.computation.storage_info", "Storage Used: %s / %s");

        // crafting
        provider.add("gui.neoecoae.crafting.pattern_bus_count", "Pattern Buses: %d");
        provider.add("gui.neoecoae.crafting.parallel_core_count", "Parallel Cores: %d");
        provider.add("gui.neoecoae.crafting.worker_count", "Worker Cores: %d");
        provider.add("gui.neoecoae.crafting.working_threads", "Working Threads: %d / %d (%d%%)");
        provider.add("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s");
        provider.add("gui.neoecoae.crafting.total_parallelism", "Total Parallelism: %d");
        provider.add("gui.neoecoae.crafting.total_parallelism.overflow", "Overflow: %d (%d%%)");
        provider.add("gui.neoecoae.crafting.max_energy_usage", "Max Energy Usage: 搂b%s AE");
        provider.add("gui.neoecoae.crafting.overclock_status", "Theoretical Overclock: %d, Effective Overclock: %d");
        provider.add("gui.neoecoae.crafting.overclock_status.disabled", "Theoretical Overclock: 0, Effective Overclock: 0");
        provider.add("gui.neoecoae.crafting.enable_overlock", "Enable Overlock: ");
        provider.add("gui.neoecoae.crafting.overclocked.tooltip", "Boosting performance within a limited range while consuming more 搂cEnergy搂f.");
        provider.add("gui.neoecoae.crafting.enable_active_cooling", "Enable Active Cooling: ");
        provider.add("gui.neoecoae.crafting.active_cooling.tooltip", "Consumes coolant from the fluid input hatch to enhance performance and eliminate the additional energy cost of overclocking.\nUsable coolants can be looked up in JEI.\nIf the machine's coolant level is insufficient during operation, it will stop running.\nIf the fluid output hatch is full, coolant cannot be consumed from the fluid input hatch, preventing the machine from replenishing its coolant supply.");
        provider.add("gui.neoecoae.crafting.clear_coolant", "Clear");
        provider.add("gui.neoecoae.crafting.clear_coolant.tooltip", "Clears the cached coolant so you can switch to a different coolant.");
        provider.add("gui.neoecoae.crafting.coolant_max_overclock", "Current Coolant Max Overclock: %d");
        provider.add("gui.neoecoae.crafting.coolant_max_overclock.none", "Current Coolant Max Overclock: None");
    }
}
