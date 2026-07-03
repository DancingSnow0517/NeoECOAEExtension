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
        provider.add("gui.neoecoae.common.yes", "Yes");
        provider.add("gui.neoecoae.common.no", "No");
        provider.add("gui.neoecoae.common.on", "On");
        provider.add("gui.neoecoae.common.off", "Off");
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
        provider.add("gui.neoecoae.common.inventory", "Inventory");
        provider.add("gui.neoecoae.common.overclock", "Overclock");
        provider.add("gui.neoecoae.common.active_cooling", "Active Cooling");
        provider.add("gui.neoecoae.common.input_fluid", "In Fluid");
        provider.add("gui.neoecoae.common.output_fluid", "Out Fluid");
        provider.add("gui.neoecoae.fluid_tank.empty", "Empty");
        provider.add("gui.neoecoae.fluid_tank.amount", "%s / %s mB");
        provider.add("gui.neoecoae.common.multiblock_builder", "Multiblock Builder");
        provider.add("gui.neoecoae.common.show_builder", "Show Builder");
        provider.add("gui.neoecoae.common.hide_builder", "Hide Builder");
        provider.add("gui.neoecoae.common.close", "Close");
        provider.add("gui.neoecoae.pattern_bus.patterns", "Patterns");
        provider.add("gui.neoecoae.pattern_bus.patterns_page", "Patterns %s - %s");
        provider.add("gui.neoecoae.pattern_bus.previous_page", "Previous page");
        provider.add("gui.neoecoae.pattern_bus.next_page", "Next page");
        provider.add("gui.neoecoae.pattern_bus.page", "Page %s / %s");

        // short controller titles for the compact three-zone layout
        provider.add("gui.neoecoae.ui.storage_system.short", "ECO - %s Storage System");
        provider.add("gui.neoecoae.ui.computation_system.short", "ECO - %s Computation System");
        // legacy keys kept for backward compatibility
        provider.add("gui.neoecoae.ui.storage_subsystem.short", "ECO - %s Storage Subsystem");
        provider.add("gui.neoecoae.ui.computation_subsystem.short", "ECO - %s Computation Subsystem");
        provider.add("gui.neoecoae.ui.crafting_controller.short", "ECO - %s Crafting Controller");

        // ECO CPU
        provider.add("gui.neoecoae.cpu.eco", "%s ECO CPU");
        provider.add("gui.neoecoae.cpu.eco_with_storage", "%s ECO CPU (%s)");
        provider.add("gui.neoecoae.cpu.storage", "%s Storage");
        provider.add("gui.neoecoae.cpu.coprocessors", "%s Co-processors");

        // integrated working station
        provider.add("gui.neoecoae.integrated_working_station.energy", "Required Energy: %s kAE");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs", "Allow Output Sides");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs.enabled", "Enabled");
        provider.add("gui.neoecoae.integrated_working_station.allow_outputs.disabled", "Disabled");
        provider.add("gui.neoecoae.integrated_working_station.auto_export.on", "Auto Export: On");
        provider.add("gui.neoecoae.integrated_working_station.auto_export.off", "Auto Export: Off");
        provider.add("gui.neoecoae.integrated_working_station.auto_io.on", "Auto I/O: On");
        provider.add("gui.neoecoae.integrated_working_station.auto_io.off", "Auto I/O: Off");
        provider.add("gui.neoecoae.integrated_working_station.available_upgrades", "Available Upgrades:");
        provider.add("gui.neoecoae.integrated_working_station.clear_input_fluid", "Clear Input Fluid");
        provider.add("gui.neoecoae.integrated_working_station.clear_output_fluid", "Clear Output Fluid");
        provider.add("gui.neoecoae.integrated_working_station.energy_label", "Required Energy:");
        provider.add("gui.neoecoae.integrated_working_station.not_implemented", "Not implemented");
        provider.add("gui.neoecoae.integrated_working_station.progress_percent", "Progress: %s%%");
        provider.add("gui.neoecoae.integrated_working_station.speed_card_upgrade", "Speed Card (%s)");
        provider.add("gui.neoecoae.integrated_working_station.work_progress", "Work Progress: %s / %s");
        provider.add("gui.neoecoae.multiblock.builder", "Multiblock Builder");
        provider.add("gui.neoecoae.multiblock.close_builder", "Close Builder");
        provider.add("gui.neoecoae.multiblock.decrease_length", "Decrease Length");
        provider.add("gui.neoecoae.multiblock.increase_length", "Increase Length");
        provider.add("gui.neoecoae.multiblock.length", "Length: %s");
        provider.add("gui.neoecoae.multiblock.preview", "Preview");
        provider.add("gui.neoecoae.multiblock.pattern", "Pattern");
        provider.add("gui.neoecoae.multiblock.layer", "Layer");
        provider.add("gui.neoecoae.multiblock.layer_all", "All");
        provider.add("gui.neoecoae.multiblock.layer_value", "Y %s");
        provider.add("gui.neoecoae.multiblock.size", "Size: %s × %s × %s");
        provider.add("gui.neoecoae.multiblock.controller", "Controller: %s, %s, %s");
        provider.add("gui.neoecoae.multiblock.material_summary", "Materials Summary");
        provider.add("gui.neoecoae.multiblock.open_build_assist", "Open Build Assist");
        provider.add("gui.neoecoae.multiblock.close_build_assist", "Close Build Assist");
        provider.add("gui.neoecoae.multiblock.build_assist", "On-site Build Assist");
        provider.add("gui.neoecoae.multiblock.mirror", "Mirror");
        provider.add(
                "gui.neoecoae.multiblock.preview_only_hint",
                "This only shows the standard pattern; world blocks are not checked.");
        provider.add("gui.neoecoae.multiblock.linked_host", "Linked Host");
        provider.add("gui.neoecoae.multiblock.inventory_materials", "Inventory Materials");
        provider.add(
                "gui.neoecoae.multiblock.build_assist_hint",
                "Preview and check the linked Controller before building.");
        provider.add(
                "gui.neoecoae.multiblock.no_linked_host_hint",
                "Open the terminal on a nearby controller to link on-site checks.");
        provider.add("gui.neoecoae.multiblock.build", "Build");
        provider.add("gui.neoecoae.multiblock.reused", "Reused: %s");
        provider.add("gui.neoecoae.multiblock.missing", "Missing: %s");
        provider.add("gui.neoecoae.multiblock.conflicts", "Conflicts: %s");
        provider.add("gui.neoecoae.multiblock.required_items", "Required Items: %s");
        provider.add("emi.neoecoae.multiblock.requirements", "Block count requirements");
        provider.add("emi.neoecoae.multiblock.change_length", "Change structure length");
        provider.add("emi.neoecoae.multiblock.show_all_layers", "Show all layers");
        provider.add("emi.neoecoae.multiblock.show_layer", "Show layer %s");
        provider.add("emi.neoecoae.multiblock.show_formed", "Show formed state");
        provider.add("emi.neoecoae.multiblock.show_unformed", "Show unformed state");
        provider.add("emi.neoecoae.multiblock.previous_page", "Previous page");
        provider.add("emi.neoecoae.multiblock.next_page", "Next page");
        provider.add("emi.neoecoae.multiblock.empty_scene", "No structure data");
        provider.add("gui.neoecoae.structure_terminal.target.crafting", "Crafting");
        provider.add("gui.neoecoae.structure_terminal.target.storage", "Storage");
        provider.add("gui.neoecoae.structure_terminal.target.computation", "Computation");
        provider.add("gui.neoecoae.structure_terminal.target.crafting.short", "Craft");
        provider.add("gui.neoecoae.structure_terminal.target.storage.short", "Store");
        provider.add("gui.neoecoae.structure_terminal.target.computation.short", "Comp");
        provider.add("gui.neoecoae.structure_terminal.target.crafting.tooltip", "Crafting Subsystem");
        provider.add("gui.neoecoae.structure_terminal.target.storage.tooltip", "Storage Subsystem");
        provider.add("gui.neoecoae.structure_terminal.target.computation.tooltip", "Computation Subsystem");
        provider.add("gui.neoecoae.structure_terminal.mode.build", "Build");
        provider.add("gui.neoecoae.structure_terminal.mode.mirrored_build", "Mirrored");
        provider.add("gui.neoecoae.structure_terminal.mode.dismantle", "Dismantle");
        provider.add("gui.neoecoae.structure_terminal.mode.build.short", "Build");
        provider.add("gui.neoecoae.structure_terminal.mode.mirrored_build.short", "Mirror");
        provider.add("gui.neoecoae.structure_terminal.mode.dismantle.short", "Dism.");
        provider.add("gui.neoecoae.structure_terminal.mode.build.tooltip", "Build the standard structure");
        provider.add("gui.neoecoae.structure_terminal.mode.mirrored_build.tooltip", "Build a mirrored structure");
        provider.add("gui.neoecoae.structure_terminal.mode.dismantle.tooltip", "Dismantle the current structure");
        provider.add("gui.neoecoae.structure_terminal.preview_formed", "Form Preview");
        provider.add("gui.neoecoae.structure_terminal.preview_mirrored", "Mirrored Preview");
        provider.add("gui.neoecoae.structure_terminal.preview_formed.short", "Form");
        provider.add("gui.neoecoae.structure_terminal.preview_mirrored.short", "Mirror");
        provider.add("gui.neoecoae.structure_terminal.preview_unformed", "Prototype");
        provider.add("gui.neoecoae.structure_terminal.reset", "Reset");
        provider.add("gui.neoecoae.structure_terminal.variable_sections", "Variable Sections: %s [%s-%s]");
        provider.add("gui.neoecoae.structure_terminal.available", "Owned: %s");
        provider.add("gui.neoecoae.structure_terminal.required", "Required: %s");
        provider.add("gui.neoecoae.structure_terminal.missing", "Missing: %s");
        provider.add("gui.neoecoae.structure_terminal.length", "Variable Length: %s");
        provider.add("gui.neoecoae.structure_terminal.length_range", "Min: %s  Max: %s");
        provider.add("gui.neoecoae.structure_terminal.host_selection", "Controller Selection");
        provider.add("gui.neoecoae.structure_terminal.required_materials", "Required Blocks");
        provider.add("gui.neoecoae.structure_terminal.no_materials", "No required materials");
        provider.add("gui.neoecoae.structure_terminal.unknown_material", "Unknown material");
        provider.add("gui.neoecoae.structure_terminal.hint_shift_build", "Shift+Right-click the Controller to build");
        provider.add("gui.neoecoae.terminal.not_a_host", "This block is not a valid build target");
        provider.add("gui.neoecoae.multiblock.status.idle", "Idle");
        provider.add("gui.neoecoae.multiblock.status.length_updated", "Length updated");
        provider.add("gui.neoecoae.multiblock.status.controller_formed", "Controller formed");
        provider.add("gui.neoecoae.multiblock.status.no_definition", "No structure definition");
        provider.add("gui.neoecoae.multiblock.status.structure_ready", "Structure ready");
        provider.add("gui.neoecoae.multiblock.status.ready_to_build", "Ready to build");
        provider.add("gui.neoecoae.multiblock.status.not_enough_items", "Not enough materials");
        provider.add("gui.neoecoae.multiblock.status.conflicts_detected", "Conflicts detected");
        provider.add("gui.neoecoae.multiblock.status.build_in_progress", "Building in progress");
        provider.add("gui.neoecoae.multiblock.status.build_already_in_progress", "Build already in progress");
        provider.add("gui.neoecoae.multiblock.status.build_complete", "Build complete");
        provider.add("gui.neoecoae.multiblock.status.build_interrupted", "Build interrupted");
        provider.add("gui.neoecoae.multiblock.status.builder_unavailable", "Builder unavailable");
        provider.add("gui.neoecoae.multiblock.status.build_failed", "Build failed");
        provider.add("gui.neoecoae.multiblock.status.dismantled", "Dismantled");
        provider.add("gui.neoecoae.multiblock.status.dismantle_failed", "Dismantle failed");
        provider.add("gui.neoecoae.multiblock.status.building", "Building %s/%s");
        provider.add("gui.neoecoae.relative_side.front", "Front");
        provider.add("gui.neoecoae.relative_side.back", "Back");
        provider.add("gui.neoecoae.relative_side.left", "Left");
        provider.add("gui.neoecoae.relative_side.right", "Right");
        provider.add("gui.neoecoae.relative_side.top", "Top");
        provider.add("gui.neoecoae.relative_side.bottom", "Bottom");

        // storage
        provider.add("gui.neoecoae.storage.energy", "Energy Monitor");
        provider.add("gui.neoecoae.storage.energy_status", "Energy Storage: %sAE / %sAE (%s%%)");
        provider.add("gui.neoecoae.storage.matrix_card.title", "%s Storage Matrix");
        provider.add("gui.neoecoae.storage.matrix_card.types", "%s / %s types used");
        provider.add("gui.neoecoae.storage.matrix_card.bytes", "%s / %s bytes used");
        provider.add("gui.neoecoae.storage.tooltip.type_used", "%s storage used %s");
        provider.add("gui.neoecoae.storage.items", "Item");
        provider.add("gui.neoecoae.storage.fluids", "Fluid");
        provider.add("gui.neoecoae.storage.chemicals", "Chemical");
        provider.add("gui.neoecoae.storage.infinite", "Infinite");
        provider.add("gui.neoecoae.storage.infinite_value", "infinite");
        provider.add("gui.neoecoae.storage.infinite_domain", "Infinite Domain");
        provider.add("gui.neoecoae.storage.infinite_component", "Infinite Storage Component");
        provider.add("gui.neoecoae.storage.infinite_extract_blocked", "Cannot remove storage matrices in infinite storage mode");
        provider.add("gui.neoecoae.storage.used_short", "Used");
        provider.add("gui.neoecoae.storage.bytes_used", "Bytes Used");
        provider.add("gui.neoecoae.storage.energy_storage", "Energy Storage");
        provider.add("gui.neoecoae.storage.usage", "Usage");
        provider.add("gui.neoecoae.storage.system_load", "System Load");
        provider.add("gui.neoecoae.storage.current_load", "Current Load");
        provider.add("gui.neoecoae.storage.max_load", "Max Load");
        provider.add("gui.neoecoae.storage.avg_load", "Avg Load");
        provider.add("gui.neoecoae.storage.status", "Status");
        provider.add("gui.neoecoae.storage.status.ok", "Normal");
        provider.add("gui.neoecoae.storage.status.capacity_full", "%s capacity full");
        provider.add("gui.neoecoae.storage.idle_matrices", "Idle");
        provider.add("gui.neoecoae.storage.matrices", "Storage Matrices");
        provider.add("gui.neoecoae.storage.matrix", "Storage Matrix");
        provider.add("gui.neoecoae.storage.no_matrix_installed", "No storage matrix installed");
        provider.add("gui.neoecoae.storage.load_distribution", "Load Distribution");
        provider.add("gui.neoecoae.storage.legend.empty", "Empty");
        provider.add("gui.neoecoae.storage.tooltip.items_used", "Item storage used %s");
        provider.add("gui.neoecoae.storage.tooltip.fluids_used", "Fluid storage used %s");
        provider.add("gui.neoecoae.storage.tooltip.chemicals_used", "Chemical storage used %s");
        provider.add("gui.neoecoae.storage.tooltip.used_total", "Used: %s / %s");
        provider.add("tooltip.neoecoae.storage.infinite_member", "Managed by the storage controller");
        provider.add(
                "tooltip.neoecoae.infinite_component.unlock",
                "Insert 64 components and install 16 L9 storage matrices to enable infinite storage");
        provider.add("gui.neoecoae.storage_interface.title", "Storage Interface");
        provider.add("gui.neoecoae.storage_interface.network", "Network");
        provider.add("gui.neoecoae.storage_interface.structure", "Structure");
        provider.add("gui.neoecoae.storage_interface.connected", "Connected");
        provider.add("gui.neoecoae.storage_interface.disconnected", "Disconnected");
        provider.add("gui.neoecoae.storage_interface.formed", "Formed");
        provider.add("gui.neoecoae.storage_interface.unformed", "Unformed");
        provider.add("gui.neoecoae.storage_interface.mode.storage", "Storage");
        provider.add("gui.neoecoae.storage_interface.mode.output", "Output");
        provider.add("gui.neoecoae.storage_interface.storage_mode", "Mode: Mounted as ECO storage");
        provider.add("gui.neoecoae.storage_interface.export", "Export: %s / tick");
        provider.add(
                "gui.neoecoae.storage_interface.output_tooltip",
                "Output mode pauses L-series storage mounting and exports contents to the external ME network.");

        // computation
        provider.add("gui.neoecoae.computation.thread_info", "Used Threads: %s / %s");
        provider.add("gui.neoecoae.computation.parallel_info", "Parallel: %s");
        provider.add("gui.neoecoae.computation.storage_info", "Used Storage: %s / %s");
        provider.add("gui.neoecoae.computation.threads", "Threads");
        provider.add("gui.neoecoae.computation.accelerators", "Accelerators: %s");
        provider.add("gui.neoecoae.computation.available_storage", "Available Storage");
        provider.add("gui.neoecoae.computation.storage_used", "Storage Used");
        provider.add("gui.neoecoae.computation.parallel_count", "Parallel Count: %s");
        provider.add("gui.neoecoae.computation.cpu_selection_mode", "CPU Auto-Selection Mode");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.click", "Click to cycle");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.any", "CPU Auto-Selection: Any Source");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.machine_only", "CPU Auto-Selection: Machine Only");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.player_only", "CPU Auto-Selection: Player Only");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.short", "CPU Mode");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.short.any", "Any");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.short.machine", "Machine");
        provider.add("gui.neoecoae.computation.cpu_selection_mode.short.player", "Player");
        provider.add(
                "gui.neoecoae.computation.cell_locked_active_job",
                "This computation cell cannot be removed while crafting jobs are active.");

        // crafting
        provider.add("gui.neoecoae.crafting.pattern_bus_count", "Pattern Bus Count: %s");
        provider.add("gui.neoecoae.crafting.parallel_core_count", "Parallel Core Count: %s");
        provider.add("gui.neoecoae.crafting.worker_count", "Worker Core Count: %s");
        provider.add("gui.neoecoae.crafting.working_threads", "Working Threads: %s / %s (%s%%)");
        provider.add("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s");
        provider.add("gui.neoecoae.crafting.total_parallelism", "Total Parallelism: %s");
        provider.add("gui.neoecoae.crafting.recipe_slots", "Recipe Slots");
        provider.add("gui.neoecoae.crafting.batch_parallel", "Throughput");
        provider.add("gui.neoecoae.crafting.ft_cores_short", "FT Cores");
        provider.add("gui.neoecoae.crafting.status", "Status");
        provider.add("gui.neoecoae.crafting.stats", "Crafting Stats");
        provider.add("gui.neoecoae.crafting.coolant", "Coolant");
        provider.add("gui.neoecoae.crafting.energy_short", "Energy");
        provider.add("gui.neoecoae.crafting.cooling_short", "Cooling");
        provider.add("gui.neoecoae.crafting.waste_short", "Waste");
        provider.add("gui.neoecoae.crafting.patterns_short", "Patterns");
        provider.add("gui.neoecoae.crafting.workers_short", "Workers");
        provider.add("gui.neoecoae.crafting.energy_cooling", "Energy / Cooling");
        provider.add("gui.neoecoae.crafting.energy_usage", "Current Energy Usage");
        provider.add("gui.neoecoae.crafting.max_parallel", "Max Parallel");
        provider.add("gui.neoecoae.crafting.module_preview", "Structure Module Preview");
        provider.add("gui.neoecoae.crafting.no_parallel_core", "No parallel core");
        provider.add("gui.neoecoae.crafting.no_worker_cores", "No worker cores detected");
        provider.add("gui.neoecoae.crafting.parallel_core_tiers", "Parallel Core Tiers");
        provider.add("gui.neoecoae.crafting.parallel_per_core", "Parallel: %s");
        provider.add("gui.neoecoae.crafting.effective_parallel", "Effective parallel: %s");
        provider.add("gui.neoecoae.crafting.performance", "Performance");
        provider.add("gui.neoecoae.crafting.performance_short", "Performance");
        provider.add("gui.neoecoae.crafting.tasks", "Crafting Tasks");
        provider.add("gui.neoecoae.crafting.no_tasks", "No active tasks");
        provider.add("gui.neoecoae.crafting.task.amount", "Amount: %s");
        provider.add("gui.neoecoae.crafting.task.crafts", "Crafts: %s");
        provider.add("gui.neoecoae.crafting.task.time", "Time: %s / %s");
        provider.add("gui.neoecoae.crafting.task.status.running", "Running");
        provider.add("gui.neoecoae.crafting.task.status.queued", "Queued");
        provider.add("gui.neoecoae.crafting.task.status.waiting_output", "Waiting for output");
        provider.add("gui.neoecoae.crafting.total_parallelism.overflow", "Overflow: %s (%s%%)");
        provider.add("gui.neoecoae.crafting.max_energy_usage", "Max Energy: §b%s AE");
        provider.add(
                "gui.neoecoae.crafting.overclock_status",
                "Theoretical Structure Overclock: %s, Current Effective Overclock: %s");
        provider.add(
                "gui.neoecoae.crafting.overclock_status.disabled",
                "Theoretical Structure Overclock: 0, Current Effective Overclock: 0");
        provider.add("gui.neoecoae.crafting.enable_overlock", "Enable Overlock: ");
        provider.add("gui.neoecoae.crafting.enable_overclock", "Enable Overclock: ");
        provider.add("gui.neoecoae.crafting.overclock", "Overclock");
        provider.add("gui.neoecoae.crafting.overclock.on", "Overclock: On");
        provider.add("gui.neoecoae.crafting.overclock.off", "Overclock: Off");
        provider.add("gui.neoecoae.crafting.active_cooling", "Active Cooling");
        provider.add("gui.neoecoae.crafting.active_cooling.on", "Active Cooling: On");
        provider.add("gui.neoecoae.crafting.active_cooling.off", "Active Cooling: Off");
        provider.add("gui.neoecoae.crafting.auto_clear_coolant", "Auto-clear Waste Fluid");
        provider.add("gui.neoecoae.crafting.auto_clear_coolant.on", "Auto-clear Waste Fluid: On");
        provider.add("gui.neoecoae.crafting.auto_clear_coolant.off", "Auto-clear Waste Fluid: Off");
        provider.add(
                "gui.neoecoae.crafting.overclocked.tooltip",
                "Increases performance within limits but consumes more §cenergy§f.");
        provider.add("gui.neoecoae.crafting.enable_active_cooling", "Enable Active Cooling: ");
        provider.add(
                "gui.neoecoae.crafting.active_cooling.tooltip",
                "Consumes coolant from the fluid input hatch to boost performance and eliminate extra energy cost from overclocking.\nAvailable coolant can be checked in JEI.\nIf coolant runs out while the machine is operating, it will stop.\nIf the fluid output hatch is full, coolant cannot be consumed from the input hatch and the machine will be unable to replenish coolant.");
        provider.add("gui.neoecoae.crafting.clear_coolant", "Clear");
        provider.add(
                "gui.neoecoae.crafting.clear_coolant.tooltip",
                "Clear the currently cached coolant value to switch to another coolant.");
        provider.add("gui.neoecoae.crafting.coolant_fluid", "Coolant Fluid: %s");
        provider.add("gui.neoecoae.crafting.coolant_fluid.none", "None");
        provider.add("gui.neoecoae.crafting.coolant_fluid.unknown", "Unknown");
        provider.add("gui.neoecoae.crafting.coolant_max_overclock", "Max overclock supported by current coolant: %s");
        provider.add(
                "gui.neoecoae.crafting.coolant_max_overclock.none", "Max overclock supported by current coolant: None");

        // machine status panels
        provider.add("gui.neoecoae.machine.accelerators", "Accelerators: %s");
        provider.add("gui.neoecoae.machine.accelerators_label", "Accelerators");
        provider.add("gui.neoecoae.machine.active", "Active");
        provider.add("gui.neoecoae.machine.active_cooling", "Active Cooling");
        provider.add("gui.neoecoae.machine.bytes_unit", "Bytes");
        provider.add("gui.neoecoae.machine.bytes_value", "Bytes: %s / %s");
        provider.add("gui.neoecoae.machine.energy_value", "Energy: %s / %s AE");
        provider.add("gui.neoecoae.machine.formed", "Formed");
        provider.add("gui.neoecoae.machine.no_storage_cells", "No storage cells detected");
        provider.add("gui.neoecoae.machine.open_crafting", "Open Crafting");
        provider.add("gui.neoecoae.machine.overclocked", "Overclocked");
        provider.add("gui.neoecoae.machine.parallel", "Parallel: %s");
        provider.add("gui.neoecoae.machine.parallel_cores", "Parallel Cores: %s");
        provider.add("gui.neoecoae.machine.parallel_cores_label", "Parallel Cores");
        provider.add("gui.neoecoae.machine.parallel_label", "Parallel");
        provider.add("gui.neoecoae.machine.patterns", "Patterns: %s");
        provider.add("gui.neoecoae.machine.patterns_label", "Patterns");
        provider.add("gui.neoecoae.machine.storage", "Storage");
        provider.add("gui.neoecoae.machine.storage_bytes", "Storage: %s / %s bytes");
        provider.add("gui.neoecoae.machine.test", "Test");
        provider.add("gui.neoecoae.machine.threads_label", "Threads");
        provider.add("gui.neoecoae.machine.threads_value", "Threads: %s / %s");
        provider.add("gui.neoecoae.machine.types_value", "Types: %s / %s");
        provider.add("gui.neoecoae.machine.use_structure_terminal", "Use structure terminal to build");
        provider.add("gui.neoecoae.machine.workers", "Workers: %s");
        provider.add("gui.neoecoae.machine.workers_label", "Workers");

        provider.add("itemGroup.neoecoae.main", "Neo ECO AE Extension");
        provider.add("screen.neoecoae.config.title", "Neo ECO AE Extension Config");
        provider.add("screen.neoecoae.config.save", "Save");
        provider.add("screen.neoecoae.config.cancel", "Cancel");
        provider.add(
                "screen.neoecoae.config.invalid", "Please enter positive integers; pattern bus pages must be 1-8.");
        provider.add(
                "screen.neoecoae.config.remote_server_locked",
                "Local common config cannot be changed while connected to a remote server.");
        provider.add(
                "screen.neoecoae.config.restart_notice",
                "Changes are fully applied after re-entering the world or restarting the server.");
        provider.add("screen.neoecoae.config.craftingSystemMaxLength", "Crafting Controller Max Length");
        provider.add("screen.neoecoae.config.computationSystemMaxLength", "Computation Controller Max Length");
        provider.add("screen.neoecoae.config.storageSystemMaxLength", "Storage Controller Max Length");
        provider.add("screen.neoecoae.config.craftingPatternBusPages", "Smart Pattern Bus Pages (1-8)");
        provider.add("screen.neoecoae.config.craftingPatternBusPages.capacity", "%s pages = %s patterns");
        provider.add("screen.neoecoae.config.increaseCapacity", "Increase Capacity");
        provider.add("screen.neoecoae.config.increaseCapacity.on", "On");
        provider.add("screen.neoecoae.config.increaseCapacity.off", "Off");
        provider.add("screen.neoecoae.config.increaseCapacity.tooltip.title", "After enabling, storage cell capacity:");
        provider.add("screen.neoecoae.config.increaseCapacity.tooltip.storage", "%s storage cells: from %s to %s");
    }
}
