package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class GuiLangs {
    public static void accept(RegistrateLangProvider provider) {
        provider.add(
                "gui.neoecoae.sync.too_large",
                "Display data is too large or the sync queue is busy. Close and reopen this screen to retry.");
        provider.add("gui.neoecoae.sync.loading", "Loading display data...");
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

        // crafting planner status and fallback messages
        provider.add("gui.neoecoae.planning.overflow_title", "Overflow - ECO fast - %s ms - %s");
        provider.add("gui.neoecoae.planning.title", "Crafting Plan");
        provider.add("gui.neoecoae.planning.eco_fast_suffix", " - ECO fast - %s ms");
        provider.add("gui.neoecoae.planning.partial_plan_cycle", "Partial crafting plan (insufficient materials%s)");
        provider.add(
                "gui.neoecoae.planning.partial_plan_cycle.warning", "- cycle detected - insufficient starting seed");
        provider.add("gui.neoecoae.planning.cycle_missing_seed", "Missing cycle seed: %s");
        provider.add("gui.neoecoae.planning.cycle_tooltip.header", "ECO cycle calculation");
        provider.add("gui.neoecoae.planning.cycle_tooltip.initial", "Initial: %s");
        provider.add("gui.neoecoae.planning.cycle_tooltip.consumed", "Consumed in cycle: %s");
        provider.add("gui.neoecoae.planning.cycle_tooltip.produced", "Produced in cycle: %s");
        provider.add("gui.neoecoae.planning.cycle_tooltip.remaining", "After planning: %s");
        provider.add("gui.neoecoae.planning.reason.dynamic_smithing", "Crafting Plan - dynamic replacement pattern");
        provider.add("gui.neoecoae.planning.reason.no_eco_host", "No ECO crafting host available");
        provider.add("gui.neoecoae.planning.reason.snapshot_rejected", "The request's recipe graph could not be read");
        provider.add(
                "gui.neoecoae.planning.reason.pattern_incompatible",
                "This pattern requires AE2 native planning semantics");
        provider.add(
                "gui.neoecoae.planning.reason.snapshot_limit_exceeded",
                "The pattern graph exceeds ECO planning limits");
        provider.add(
                "gui.neoecoae.planning.reason.fallback_setup_failed", "Could not initialize AE2 fallback planning");
        provider.add("gui.neoecoae.planning.reason.solver_no_route", "No executable crafting route found");
        provider.add("gui.neoecoae.planning.reason.solver_budget_exhausted", "Planning budget exhausted");
        provider.add(
                "gui.neoecoae.planning.reason.assembly_rejected",
                "The result could not be assembled into an executable plan");
        provider.add("gui.neoecoae.planning.reason.craft_less_no_craftable", "No craftable quantity available");
        provider.add("gui.neoecoae.planning.reason.precise_path_failed", "Precise substitute-input planning failed");
        provider.add("gui.neoecoae.planning.reason.differential_mismatch", "The result did not match AE2 validation");
        provider.add("gui.neoecoae.planning.reason.planning_failure", "ECO planning failed unexpectedly");
        provider.add("gui.neoecoae.planning.ae2_fallback_title", "Crafting Plan - using AE2 (%s)");
        provider.add(
                "chat.neoecoae.planning.ae2_fallback", "ECO fast planning was not used; AE2 planning selected: %s");

        // short controller titles for the compact three-zone layout
        provider.add("gui.neoecoae.ui.storage_system.short", "ECO - %s Storage System");
        provider.add("gui.neoecoae.ui.computation_system.short", "ECO - %s Computation System");
        // legacy keys kept for backward compatibility
        provider.add("gui.neoecoae.ui.storage_subsystem.short", "ECO - %s Storage Subsystem");
        provider.add("gui.neoecoae.ui.computation_subsystem.short", "ECO - %s Computation Subsystem");
        provider.add("gui.neoecoae.ui.crafting_controller.short", "ECO - %s Crafting Controller");

        // AE2 crafting confirmation
        provider.add("gui.ae2.ConfirmCraftCpuStatus", "Storage: %s; Co-processors: %s");
        provider.add("gui.ae2.ConfirmCraftNoCpu", "Storage: N/A; Co-processors: N/A");

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
        provider.add(
                "gui.neoecoae.integrated_working_station.entity_speed_card_upgrade", "Entity Acceleration Card (%s)");
        provider.add("gui.neoecoae.integrated_working_station.effective_speed", "Current Speed: %s progress/tick");
        provider.add("gui.neoecoae.integrated_working_station.speed_limit", "Speed Limit: %s progress/tick");
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
        provider.add("gui.neoecoae.multiblock.actions", "Actions");
        provider.add(
                "gui.neoecoae.multiblock.auto_preview_hint",
                "Changing parameters refreshes the preview automatically.");
        provider.add("gui.neoecoae.multiblock.conflict_positions", "Conflict Positions");
        provider.add("gui.neoecoae.multiblock.conflict_preview", "Conflict Preview");
        provider.add("gui.neoecoae.multiblock.item_required", "Required: %d");
        provider.add("gui.neoecoae.multiblock.live_result", "Live Result");
        provider.add("gui.neoecoae.multiblock.material_enough", "Enough Materials");
        provider.add("gui.neoecoae.multiblock.material_missing", "Missing Materials");
        provider.add("gui.neoecoae.multiblock.materials", "Materials");
        provider.add("gui.neoecoae.multiblock.mirror.off", "Off");
        provider.add("gui.neoecoae.multiblock.mirror.off.tooltip", "Build without mirroring");
        provider.add("gui.neoecoae.multiblock.mirror.on", "On");
        provider.add("gui.neoecoae.multiblock.mirror.on.tooltip", "Build a mirrored structure");
        provider.add("gui.neoecoae.multiblock.more_conflicts", "%d more conflicts");
        provider.add("gui.neoecoae.multiblock.no_conflicts", "No Conflicts");
        provider.add("gui.neoecoae.multiblock.parameters", "Build Parameters");
        provider.add("gui.neoecoae.multiblock.status.mirror_updated", "Mirror option updated");

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
        provider.add(
                "gui.neoecoae.storage.infinite_extract_blocked",
                "Cannot remove storage matrices in infinite storage mode");
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
        provider.add("gui.neoecoae.storage.status.domain_loading", "Domain loading");
        provider.add("gui.neoecoae.storage.status.domain_migrating_v1", "Migrating V1 domain");
        provider.add("gui.neoecoae.storage.status.domain_quarantined", "Domain quarantined");
        provider.add("gui.neoecoae.storage.status.domain_closed", "Domain closed");
        provider.add("gui.neoecoae.storage.status.domain_unavailable", "Domain unavailable");
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
        // storage controller details
        provider.add("gui.neoecoae.storage.bulk_mark", "Auto-mark compressible items above %s");
        provider.add("gui.neoecoae.storage.bulk_mark.result.busy", "Storage transfer or migration is in progress");
        provider.add("gui.neoecoae.storage.bulk_mark.result.invalid_threshold", "The auto-mark threshold is invalid");
        provider.add("gui.neoecoae.storage.bulk_mark.result.no_bulk_cell", "No ECO MEGA long bulk cell installed");
        provider.add(
                "gui.neoecoae.storage.bulk_mark.result.success",
                "Auto-marked %s; already marked %s; no space %s; internally transferred %s");
        provider.add("gui.neoecoae.storage.bulk_mark.result.unavailable", "MEGA bulk-cell integration is unavailable");
        provider.add("gui.neoecoae.storage.mega.title", "MEGA Bulk Storage");
        provider.add("gui.neoecoae.storage.mega.upgrade", "Upgrade Card");
        provider.add("gui.neoecoae.storage.host.build", "Build Structure");
        provider.add("gui.neoecoae.storage.host.details", "Storage Details");
        provider.add("gui.neoecoae.storage.host.guide", "Storage System Guide");
        provider.add("gui.neoecoae.storage.host.mirror", "Mirror: %s");
        provider.add("gui.neoecoae.storage.host.preview", "Preview");
        provider.add("gui.neoecoae.storage.legacy.cell_bytes", "Bytes: %s / %s");
        provider.add("gui.neoecoae.storage.legacy.cell_info", "%s (%s)");
        provider.add("gui.neoecoae.storage.legacy.cell_info.custom", "%s Storage Matrix");
        provider.add("gui.neoecoae.storage.legacy.cell_info.empty", "Unknown");
        provider.add("gui.neoecoae.storage.legacy.cell_info.fluid", "Fluid Storage Matrix");
        provider.add("gui.neoecoae.storage.legacy.cell_info.gas", "Gas Storage Matrix");
        provider.add("gui.neoecoae.storage.legacy.cell_info.item", "Item Storage Matrix");
        provider.add("gui.neoecoae.storage.legacy.cell_info.other", "Other Storage Matrix");
        provider.add("gui.neoecoae.storage.legacy.cell_tooltip", "%s (%s)\nBytes: %s / %s");
        provider.add("gui.neoecoae.storage.legacy.cell_types", "Types: %s / %s");
        provider.add("gui.neoecoae.storage.legacy.graph.energy_stored", "Energy: %s");
        provider.add("gui.neoecoae.storage.legacy.graph.energy_usage", "Energy Use: %s AE/t");
        provider.add("gui.neoecoae.storage.legacy.graph.fluid", "Fluids Used: %s");
        provider.add("gui.neoecoae.storage.legacy.graph.fluid_type", "Fluid Types: %s / %s");
        provider.add("gui.neoecoae.storage.legacy.graph.gas", "Other Used: %s");
        provider.add("gui.neoecoae.storage.legacy.graph.gas_type", "Other Types: %s / %s");
        provider.add("gui.neoecoae.storage.legacy.graph.item", "Items Used: %s");
        provider.add("gui.neoecoae.storage.legacy.graph.item_type", "Item Types: %s / %s");
        provider.add("gui.neoecoae.storage.legacy.graph.total", "Storage Used: %s");
        provider.add("gui.neoecoae.storage.legacy.graph.total_bytes", "Bytes: %s");
        provider.add("gui.neoecoae.storage.legacy.graph.total_usage", "Usage: %s");
        provider.add("gui.neoecoae.storage.status.degraded", "Some infinite storage data needs repair");
        provider.add("gui.neoecoae.storage.status.domain_migrating_matrices", "Migrating storage matrices");
        provider.add("gui.neoecoae.storage.status.full", "%s capacity full");
        provider.add("gui.neoecoae.storage.status.high", "%s nearly full");
        provider.add("gui.neoecoae.storage.status.recovery", "Infinite storage is temporarily read-only");
        provider.add("gui.neoecoae.storage.status.stable", "Stable");
        provider.add("gui.neoecoae.storage.status.unavailable", "Infinite storage unavailable");
        provider.add("gui.neoecoae.storage.status.warning", "%s load increasing");
        provider.add("gui.neoecoae.storage_priority.close", "Close priority panel");
        provider.add("gui.neoecoae.storage_priority.extract_hint", "Extraction: lower priority devices first.");
        provider.add("gui.neoecoae.storage_priority.insert_hint", "Insertion: higher priority devices first.");
        provider.add("gui.neoecoae.storage_priority.open", "Open priority panel");
        provider.add("gui.neoecoae.storage_priority.title", "Priority");

        provider.add("gui.neoecoae.storage_interface.title", "Storage Interface");
        provider.add("gui.neoecoae.storage_interface.network", "Network");
        provider.add("gui.neoecoae.storage_interface.structure", "Structure");
        provider.add("gui.neoecoae.storage_interface.connected", "Connected");
        provider.add("gui.neoecoae.storage_interface.disconnected", "Disconnected");
        provider.add("gui.neoecoae.storage_interface.formed", "Formed");
        provider.add("gui.neoecoae.storage_interface.unformed", "Unformed");
        provider.add("gui.neoecoae.storage_interface.mode.storage", "Storage");
        provider.add("gui.neoecoae.storage_interface.mode.input", "Input");
        provider.add("gui.neoecoae.storage_interface.mode.output", "Output");
        provider.add("gui.neoecoae.storage_interface.storage_mode", "Mode: Mounted as ECO storage");
        provider.add("gui.neoecoae.storage_interface.import", "Import: %s / tick");
        provider.add("gui.neoecoae.storage_interface.export", "Export: %s / tick");
        provider.add("gui.neoecoae.storage_interface.infinite_import.enabled", "Infinite/creative import: enabled");
        provider.add("gui.neoecoae.storage_interface.infinite_import.disabled", "Infinite/creative import: disabled");
        provider.add(
                "gui.neoecoae.storage_interface.infinite_import.tooltip",
                "Allows GTL quantities above the long limit, but also continuously imports from creative storage.");
        provider.add(
                "gui.neoecoae.storage_interface.input_tooltip",
                "Input mode pauses L-series storage mounting and imports contents from the external ME network.");
        provider.add(
                "gui.neoecoae.storage_interface.output_tooltip",
                "Output mode pauses L-series storage mounting and exports contents to the external ME network.");

        provider.add("gui.neoecoae.storage_interface.infinite_ready", "Available");
        provider.add("gui.neoecoae.storage_interface.infinite_unavailable", "Unavailable");
        provider.add("gui.neoecoae.storage_interface.transfer", "Transfer: %s / tick");
        provider.add("gui.neoecoae.storage_interface.transfer_prefix", "Transfer: ");
        provider.add("gui.neoecoae.storage_interface.transfer_suffix", " / tick");

        // computation
        provider.add("gui.neoecoae.computation.thread_info", "Used Threads: %s / %s");
        provider.add("gui.neoecoae.computation.parallel_info", "Parallel: %s");
        provider.add("gui.neoecoae.computation.storage_info", "Used Storage: %s / %s");
        provider.add("gui.neoecoae.computation.threads", "Threads");
        provider.add("gui.neoecoae.computation.accelerators", "Accelerators: %s");
        provider.add("gui.neoecoae.computation.capacity", "Computation Capacity");
        provider.add("gui.neoecoae.computation.fast_task_planning", "ECO Fast Task Planning");
        provider.add("gui.neoecoae.computation.fast_task_planning.off", "Fast Task Planning: Disabled");
        provider.add("gui.neoecoae.computation.fast_task_planning.on", "Fast Task Planning: Enabled");
        provider.add(
                "gui.neoecoae.computation.fast_task_planning.tooltip",
                "Use ECO's fast task planner. When disabled, AE2's standard planner is used."
                        + " Changes synchronize to all computation hosts in this network.");
        provider.add("gui.neoecoae.computation.batch_fair_scheduling", "ECO Batch Fair Scheduling");
        provider.add("gui.neoecoae.computation.batch_fair_scheduling.off", "Batch Fair Scheduling: Disabled");
        provider.add("gui.neoecoae.computation.batch_fair_scheduling.on", "Batch Fair Scheduling: Enabled");
        provider.add(
                "gui.neoecoae.computation.batch_fair_scheduling.tooltip",
                "Let each virtual F9 crafting job finish its current batch before receiving another."
                        + " This improves small-order latency across the AE network.");
        provider.add("gui.neoecoae.computation.upgrade_slot", "Computation Controller Upgrade Slot");
        provider.add(
                "gui.neoecoae.computation.upgrade_slot.field_generators",
                "Field generators: %s, choose one tier and fill %s");
        provider.add(
                "gui.neoecoae.computation.upgrade_slot.infinite_component", "Infinite component: full stack of %s");
        provider.add("gui.neoecoae.computation.available_storage", "Available Storage");
        provider.add("gui.neoecoae.computation.storage_used", "Storage Used");
        provider.add("gui.neoecoae.computation.task.crafting", "Crafting %s %s");
        provider.add("gui.neoecoae.computation.task.crafted", "Crafted %s in %s");
        provider.add("gui.neoecoae.computation.parallel_count", "Parallel Count: %s");
        provider.add("gui.neoecoae.computation.parallel_control", "Parallel Control");
        provider.add("gui.neoecoae.computation.parallel_control.enabled", "Parallel control is available");
        provider.add(
                "gui.neoecoae.computation.parallel_control.requires_infinite",
                "Requires a full stack of 64 infinite components");
        provider.add("gui.neoecoae.computation.parallel_input.tooltip", "Allowed range: 0 - %s");
        provider.add("gui.neoecoae.computation.parallel_max", "Maximum: %s");
        provider.add("gui.neoecoae.computation.parallel_value", "Accelerators");
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

        provider.add("gui.neoecoae.computation_interface.hint", "Mark items to ignore differences in their components");

        // crafting
        provider.add("gui.neoecoae.crafting_interface.title", "Crafting Interface");
        provider.add("gui.neoecoae.crafting_interface.preview.search", "Search patterns");
        provider.add("gui.neoecoae.crafting_interface.preview.filter_substitutions", "Show substitution patterns");
        provider.add(
                "gui.neoecoae.crafting_interface.preview.filter_fluid_substitutions",
                "Show fluid substitution patterns");
        provider.add("gui.neoecoae.crafting_interface.preview.organize", "Organize pattern buses");
        provider.add("gui.neoecoae.crafting_interface.preview.slots", "Patterns: %s");
        provider.add("gui.neoecoae.crafting_interface.preview.scroll", "Row %s / %s");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer", "Transfer Patterns");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.ready", "Ready to transfer.");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.progress", "Transfer: %s / %s");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.unavailable", "Transfer unavailable.");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.result_primary", "Added: %s | Existing: %s");
        provider.add(
                "gui.neoecoae.crafting_interface.preview.organize.result_primary",
                "Organized: %d invalid and %d duplicate patterns recovered");
        provider.add(
                "gui.neoecoae.crafting_interface.preview.organize.result_secondary",
                "Inventory full; %d patterns remain to be recovered");
        provider.add("gui.neoecoae.crafting_interface.preview.organizing", "Organizing pattern buses: %d%%");
        provider.add(
                "gui.neoecoae.crafting_interface.preview.search.tooltip",
                "Search pattern inputs and outputs. Separate keywords with spaces; right-click to clear.");

        provider.add("gui.neoecoae.crafting.pattern_bus_count", "Pattern Bus Count: %s");
        provider.add("gui.neoecoae.crafting.parallel_core_count", "Parallel Core Count: %s");
        provider.add("gui.neoecoae.crafting.worker_count", "Worker Core Count: %s");
        provider.add("gui.neoecoae.crafting.working_threads", "Working Threads: %s / %s (%s%%)");
        provider.add("gui.neoecoae.crafting.coolant_amount", "Coolant: %s / %s");
        provider.add("gui.neoecoae.crafting.total_parallelism", "Total Parallelism: %s");
        provider.add("gui.neoecoae.crafting.recipe_slots", "Recipe Slots");
        provider.add("gui.neoecoae.crafting.batch_parallel", "Throughput");
        provider.add("gui.neoecoae.crafting.ui.batch_per_thread", "Batch per thread");
        provider.add("gui.neoecoae.crafting.ui.batch_per_thread.detail", "Per-host batch details");
        provider.add(
                "gui.neoecoae.crafting.ui.batch_per_thread.network_rule",
                "Exchange rule: each host adds lanes; x%s multiplies each lane's batch.");
        provider.add("gui.neoecoae.crafting.ui.batch_per_thread.total", "Maximum crafting throughput: %s");
        provider.add("gui.neoecoae.crafting.ft_cores_short", "FT Cores");
        provider.add("gui.neoecoae.crafting.status", "Status");
        provider.add("gui.neoecoae.crafting.stats", "Crafting Stats");
        provider.add("gui.neoecoae.crafting.ui.status", "Status");
        provider.add("gui.neoecoae.crafting.ui.overclock_short", "OC");
        provider.add("gui.neoecoae.crafting.ui.cooling_short", "Cool");
        provider.add("gui.neoecoae.crafting.coolant", "Coolant");
        provider.add("gui.neoecoae.crafting.fast_planner.on", "ECO Fast Planning: Enabled");
        provider.add("gui.neoecoae.crafting.fast_planner.off", "ECO Fast Planning: Disabled");
        provider.add("gui.neoecoae.crafting.cycle_planning.on", "Cycle Planning: Enabled");
        provider.add("gui.neoecoae.crafting.cycle_planning.off", "Cycle Planning: Disabled");
        // crafting capacity and fast path diagnostics
        provider.add("gui.neoecoae.crafting.capability.batch_per_fx", "Capacity per FX Core: %s");
        provider.add("gui.neoecoae.crafting.capability.ft_parallel", "FT Parallelism: %s");
        provider.add("gui.neoecoae.crafting.capability.fx", "FX Worker Cores: %d active / %d installed");
        provider.add("gui.neoecoae.crafting.capability.network_composition", "Network: %d x2 hosts / %d x8 hosts");
        provider.add("gui.neoecoae.crafting.capability.network_multiplier", "Network Multiplier M: %d");
        provider.add("gui.neoecoae.crafting.capability.overclock", "Overclock: %d theoretical / %d effective");
        provider.add("gui.neoecoae.crafting.capability.total", "Total Network Capacity: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason", "Fast Path fallback: %s");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.ae2_introspection_unavailable",
                "Cannot read AE2 pattern information");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.assembly_contract_mismatch",
                "Crafting result does not match the pattern");
        provider.add("gui.neoecoae.crafting.fast_path_reason.cache_miss", "No verified result in the cache");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.cache_result_mismatch",
                "Cached result does not match the current execution");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.cached_result_materialization_failed",
                "Cannot restore the cached result");
        provider.add("gui.neoecoae.crafting.fast_path_reason.classifier_failed", "Pattern classification failed: %s");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.durability_transition_invalid",
                "Invalid durability transition");
        provider.add("gui.neoecoae.crafting.fast_path_reason.fast_path_disabled", "Fast Path is disabled");
        provider.add("gui.neoecoae.crafting.fast_path_reason.input", "Input validation failed: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.invalid_input", "Invalid pattern input");
        provider.add("gui.neoecoae.crafting.fast_path_reason.invalid_item_input", "Invalid pattern input item");
        provider.add("gui.neoecoae.crafting.fast_path_reason.invalid_remainder", "Invalid pattern remainder");
        provider.add("gui.neoecoae.crafting.fast_path_reason.key_build_failed", "Cannot build the cache key");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.mixed_reusable_state_models",
                "Mixed reusable state model types");
        provider.add("gui.neoecoae.crafting.fast_path_reason.multiple", "Multiple reasons");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.negative_cache", "This recipe has been marked as unsupported");
        provider.add("gui.neoecoae.crafting.fast_path_reason.no_inputs", "Pattern has no inputs");
        provider.add("gui.neoecoae.crafting.fast_path_reason.no_outputs", "Pattern has no outputs");
        provider.add("gui.neoecoae.crafting.fast_path_reason.non_item_input", "Pattern input is not an item");
        provider.add("gui.neoecoae.crafting.fast_path_reason.non_item_output", "Pattern output is not an item");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.one_to_one_reusable_item_or_component",
                "One-to-one reusable item or component");
        provider.add("gui.neoecoae.crafting.fast_path_reason.output", "Output validation failed: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.output_count", "Output count is not 1: %s");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.pattern_input_inspection_failed",
                "Cannot inspect pattern inputs");
        provider.add("gui.neoecoae.crafting.fast_path_reason.pattern_null", "Pattern is missing");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.post_crafting_event_enabled",
                "Crafting events are enabled, so Fast Path is disabled");
        provider.add("gui.neoecoae.crafting.fast_path_reason.remainder", "Remainder validation failed: %s");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.remainder_is_not_reusable_item",
                "Remainder is not a reusable item");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.reusable_state_model_missing", "Missing reusable state model");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.runtime_simulation_required", "Runtime simulation is required");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.slow_execution_context",
                "The current execution context requires the slow path");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.state_second_step_proof_failed",
                "Second-step state transition verification failed");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.state_slot_count_mismatch", "State slot count has changed");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.state_transition_not_provably_linear",
                "Cannot prove that the state transition is linear");
        provider.add("gui.neoecoae.crafting.fast_path_reason.static_item_contract", "Static item contract");
        provider.add("gui.neoecoae.crafting.fast_path_reason.unknown", "Unknown reason");
        provider.add("gui.neoecoae.crafting.fast_path_reason.unknown_code", "Unknown reason: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.unsafe_pattern_type", "Unsupported pattern type");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.validation.component_patch", "Unsupported component patch");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.damaged_item", "Item is damaged");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.empty_item_stack", "Item stack is empty");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.validation.empty_required",
                "Required data collection is empty");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.invalid_amount", "Invalid amount");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.non_item_key", "Key is not an item");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.null_collection", "Data collection is missing");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.null_stack", "Item stack is missing");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.too_many_entries", "Too many entries");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.validation.unknown_validation", "Unknown validation failure");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.verification_rejected", "Recipe verification was rejected");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.verified_output_or_input_conversion_failed",
                "Cannot convert verified inputs or outputs");
        provider.add(
                "gui.neoecoae.crafting.fast_path_reason.verified_stack_validation_failed",
                "Verified item stack validation failed");
        provider.add(
                "gui.neoecoae.crafting.planning.ignore_substitutions.off", "Enable ignoring pattern substitutions");
        provider.add(
                "gui.neoecoae.crafting.planning.ignore_substitutions.on", "Disable ignoring pattern substitutions");
        provider.add(
                "gui.neoecoae.crafting.planning.substitution_pattern_count", "%d patterns have substitutions enabled");
        provider.add("gui.neoecoae.crafting.ui.batch_parallel", "Batch");
        provider.add("gui.neoecoae.crafting.ui.energy_cooling", "Energy/Cooling");
        provider.add("gui.neoecoae.crafting.ui.energy_short", "Energy");
        provider.add("gui.neoecoae.crafting.ui.energy_usage", "Energy");
        provider.add("gui.neoecoae.crafting.ui.ft_cores_short", "Parallel");
        provider.add("gui.neoecoae.crafting.ui.fx_cores", "FX Cores");
        provider.add("gui.neoecoae.crafting.ui.logical_threads", "Threads");
        provider.add("gui.neoecoae.crafting.ui.patterns_short", "Patterns");
        provider.add("gui.neoecoae.crafting.ui.recipe_slots", "Slots");
        provider.add("gui.neoecoae.crafting.ui.recipe_time_ratio", "Time");
        provider.add("gui.neoecoae.crafting.ui.single_core_capacity", "Capacity");
        provider.add("gui.neoecoae.crafting.ui.stats", "Stats");

        provider.add("gui.neoecoae.crafting_report.calculating", "Calculating...");
        provider.add("gui.neoecoae.crafting_report.solving_large_cycle", "[ECO] Solving large cycle");
        provider.add("gui.neoecoae.crafting_report.bytes", " - Bytes: %s B");
        provider.add("gui.neoecoae.crafting_report.bytes_only", "Bytes: %s B");
        provider.add("gui.neoecoae.crafting_report.single_net_output", "Change per craft: %s");
        provider.add("gui.neoecoae.crafting_report.total_net_output", "Total change: %s");
        provider.add("gui.neoecoae.crafting_report.total_net_output_unknown", "Total change: unknown");
        provider.add("gui.neoecoae.crafting_report.total_consumed", "Total consumed: %s");
        provider.add("gui.neoecoae.crafting_report.total_produced", "Total produced: %s");
        provider.add("gui.neoecoae.crafting_report.requested_exact", "Total requested: %s");
        provider.add(
                "gui.neoecoae.crafting_report.cycle_unsupported",
                "A cyclic recipe unsupported by the current solver was detected.");
        provider.add("gui.neoecoae.crafting_report.cycle_not_detected", "No cycle detected");
        provider.add("gui.neoecoae.crafting_report.cycle_planning_enabled", "Cycle detected");
        provider.add("gui.neoecoae.crafting_report.cycle_planning_disabled", "Cycle planning is disabled");
        provider.add("gui.neoecoae.crafting_report.missing_startup_seed", "Missing startup seed");
        provider.add("gui.neoecoae.crafting_graph.open", "Planning Graph");
        provider.add("gui.neoecoae.crafting_graph.title", "ECO Crafting Planning Graph");
        provider.add("gui.neoecoae.crafting_graph.search", "Search");
        provider.add("gui.neoecoae.crafting_graph.search_hint", "Search AEKey / item");
        provider.add("gui.neoecoae.crafting_graph.node.pending_craft", "Pending Craft");
        provider.add("gui.neoecoae.crafting_graph.toolbar.fit_all", "Fit All");
        provider.add("gui.neoecoae.crafting_graph.toolbar.missing_on", "Missing Only: On");
        provider.add("gui.neoecoae.crafting_graph.toolbar.missing_off", "Missing Only: Off");
        provider.add("gui.neoecoae.crafting_graph.toolbar.root", "Root");
        provider.add("gui.neoecoae.crafting_graph.toolbar.expand", "Expand");
        provider.add("gui.neoecoae.crafting_graph.toolbar.collapse", "Collapse");
        provider.add("gui.neoecoae.crafting_graph.toolbar.expand_all", "All");
        provider.add("gui.neoecoae.crafting_graph.toolbar.depth", "Depth: %s");
        provider.add("gui.neoecoae.crafting_graph.toolbar.depth_all", "Depth: All");
        provider.add("gui.neoecoae.crafting_graph.toolbar.depth_four", "Depth 4");
        provider.add("gui.neoecoae.crafting_graph.toolbar.fold_four", "Fold 4");
        provider.add("gui.neoecoae.crafting_graph.toolbar.view_tree", "View: Tree");
        provider.add("gui.neoecoae.crafting_graph.toolbar.view_graph", "View: Graph");
        provider.add("gui.neoecoae.crafting_graph.toolbar.debug_on", "Debug: On");
        provider.add("gui.neoecoae.crafting_graph.toolbar.debug_off", "Debug: Off");
        provider.add("gui.neoecoae.crafting_graph.breadcrumb.plan", "ECO Plan");
        provider.add("gui.neoecoae.crafting_graph.breadcrumb.cycle", "ECO Plan  >  Cycle #%s");
        provider.add("gui.neoecoae.crafting_graph.breadcrumb.cluster", "ECO Plan  >  Cycle Cluster #%s");
        provider.add("gui.neoecoae.crafting_graph.details.cycle_title", "Cycle #%s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_title", "Cycle Cluster #%s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_cycles", "Independent Cycles: %s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_flows", "Cross-Cycle Flows: %s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_hint", "Double-click a node to view its cycle");
        provider.add("gui.neoecoae.crafting_graph.details.status_label", "Status:");
        provider.add("gui.neoecoae.crafting_graph.details.seed_label", "Seed:");
        provider.add("gui.neoecoae.crafting_graph.details.external_label", "External Input:");
        provider.add("gui.neoecoae.crafting_graph.details.execute_label", "Execution:");
        provider.add("gui.neoecoae.crafting_graph.details.witness_steps", "Witness Steps: %s");
        provider.add("gui.neoecoae.crafting_graph.details.required_outputs", "Required Outputs: %s");
        provider.add("gui.neoecoae.crafting_graph.summary.more", ", plus %s more");
        provider.add("gui.neoecoae.crafting_graph.summary.more_suffix", " more");
        provider.add("gui.neoecoae.crafting_graph.details.folder", "Collapsed Group");
        provider.add("gui.neoecoae.crafting_graph.details.hidden_nodes", "Hidden Nodes: %s");
        provider.add("gui.neoecoae.crafting_graph.details.hidden_layers", "Hidden Layers: %s");
        provider.add("gui.neoecoae.crafting_graph.details.depth", "Depth: %s");
        provider.add("gui.neoecoae.crafting_graph.details.requested", "Requested: %s");
        provider.add("gui.neoecoae.crafting_graph.details.inventory", "From Inventory: %s");
        provider.add("gui.neoecoae.crafting_graph.details.to_craft", "To Craft: %s");
        provider.add("gui.neoecoae.crafting_graph.details.consumed", "Consumed by Job: %s");
        provider.add("gui.neoecoae.crafting_graph.details.produced", "Produced by Job: %s");
        provider.add("gui.neoecoae.crafting_graph.details.missing", "Missing: %s");
        provider.add("gui.neoecoae.crafting_graph.details.unsupported", "Unsupported: %s");
        provider.add("gui.neoecoae.crafting_graph.details.cycle", "Cycle: %s");
        provider.add("gui.neoecoae.crafting_graph.details.status", "Status: %s");
        provider.add("gui.neoecoae.crafting_graph.details.source_patterns", "Source Patterns: %s");
        provider.add("gui.neoecoae.crafting_graph.details.members", "Members: %s");
        provider.add("gui.neoecoae.crafting_graph.details.action", "Action: %s");
        provider.add("gui.neoecoae.crafting_graph.action.expand", "Expand");
        provider.add("gui.neoecoae.crafting_graph.status.satisfied", "Satisfied");
        provider.add("gui.neoecoae.crafting_graph.status.crafting", "Crafting");
        provider.add("gui.neoecoae.crafting_graph.status.missing", "Missing Materials");
        provider.add("gui.neoecoae.crafting_graph.status.unsupported", "Unsupported");
        provider.add("gui.neoecoae.crafting_graph.status.cycle", "Cycle");
        provider.add("gui.neoecoae.crafting_graph.status.not_required", "Not Required");
        provider.add("gui.neoecoae.crafting_graph.status.disabled", "Disabled");
        provider.add("gui.neoecoae.crafting_graph.status.not_implemented", "Not Implemented");
        provider.add("gui.neoecoae.crafting_graph.status.solved", "Solved");
        provider.add("gui.neoecoae.crafting_graph.status.insufficient_external_input", "Insufficient External Input");
        provider.add("gui.neoecoae.crafting_graph.status.unknown_budget", "Solver Budget Exhausted");
        provider.add("gui.neoecoae.crafting_graph.status.too_complex", "Too Complex");
        provider.add("gui.neoecoae.crafting_graph.status.cancelled", "Cancelled");
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
        // host status and network controls
        provider.add("gui.neoecoae.host.computation.accelerators", "Accelerators");
        provider.add("gui.neoecoae.host.computation.active_vcpu", "Active vCPUs");
        provider.add("gui.neoecoae.host.computation.capacity", "Computation Capacity");
        provider.add("gui.neoecoae.host.computation.cpu_pool", "Crafting CPU Pool");
        provider.add(
                "gui.neoecoae.host.computation.cpu_pool_hint",
                "Threads provide virtual crafting CPUs to the ME network.");
        provider.add("gui.neoecoae.host.computation.cpu_storage", "CPU Storage");
        provider.add("gui.neoecoae.host.computation.footer", "Computation status at a glance.");
        provider.add("gui.neoecoae.host.computation.free_memory", "Free CPU Storage");
        provider.add("gui.neoecoae.host.computation.max_vcpu", "Max vCPUs");
        provider.add("gui.neoecoae.host.computation.parallel_count", "Parallelism");
        provider.add("gui.neoecoae.host.computation.subtitle", "Computation Subsystem Host");
        provider.add("gui.neoecoae.host.computation.thread_usage", "Thread Usage");
        provider.add("gui.neoecoae.host.crafting.coolant", "Coolant");
        provider.add("gui.neoecoae.host.crafting.energy", "Energy Use");
        provider.add("gui.neoecoae.host.crafting.footer", "Crafting status and controls at a glance.");
        provider.add("gui.neoecoae.host.crafting.max_energy_usage", "Max Energy Use");
        provider.add("gui.neoecoae.host.crafting.overclock_cooling", "Overclock and Cooling");
        provider.add(
                "gui.neoecoae.host.crafting.overclock_summary", "Theoretical: %d; effective: %d; coolant limit: %s.");
        provider.add("gui.neoecoae.host.crafting.parallel_cores", "Parallel Cores");
        provider.add("gui.neoecoae.host.crafting.pattern_buses", "Pattern Buses");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.indexing", "Indexing: %d%%");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.no_target", "No available pattern bus found");
        provider.add("gui.neoecoae.host.crafting.pattern_transfer.result_secondary", "No space: %d | Incompatible: %d");
        provider.add("gui.neoecoae.host.crafting.runtime", "Crafting Status");
        provider.add("gui.neoecoae.host.crafting.subtitle", "Crafting Subsystem Host");
        provider.add("gui.neoecoae.host.crafting.total_parallelism", "Total Parallelism");
        provider.add("gui.neoecoae.host.crafting.worker_cores", "Worker Cores");
        provider.add("gui.neoecoae.host.crafting.working_threads", "Working Threads");
        provider.add("gui.neoecoae.host.metric.bytes", "Bytes");
        provider.add("gui.neoecoae.host.metric.types", "Types");
        provider.add("gui.neoecoae.host.network_frequency.cycle", "Cycle network frequency (current: %d)");
        provider.add("gui.neoecoae.host.network_frequency.cycle.unassigned", "Cycle network frequency (unassigned)");
        provider.add("gui.neoecoae.host.status.online", "Online");
        provider.add("gui.neoecoae.host.status.running", "Running");
        provider.add("gui.neoecoae.host.storage.channels", "Storage Channels");
        provider.add("gui.neoecoae.host.storage.energy_buffer", "Energy Buffer");
        provider.add(
                "gui.neoecoae.host.storage.footer",
                "Storage channels update automatically. Scroll the list to view more channels.");
        provider.add("gui.neoecoae.host.storage.storage_usage", "Storage Usage");
        provider.add("gui.neoecoae.host.storage.subtitle", "Storage Subsystem Host");
        provider.add("gui.neoecoae.host.storage.type_usage", "Type Usage");

        provider.add("gui.neoecoae.host.crafting.overflow", "Overflow");
        provider.add("gui.neoecoae.host.crafting.host_line", "%s - Threads %s - Batch %s");
        provider.add("gui.neoecoae.host.crafting.host_type.high_energy", "High-energy");
        provider.add("gui.neoecoae.host.crafting.host_type.normal", "Normal");
        provider.add("gui.neoecoae.host.network", "Network");
        provider.add("gui.neoecoae.host.network.local", "Local");
        provider.add("gui.neoecoae.host.network.offline", "Offline");
        provider.add("gui.neoecoae.host.network.normal", "Normal x2 (%s hosts)");
        provider.add("gui.neoecoae.host.network.high_energy", "High-Energy x8 (%s hosts)");
        provider.add("gui.neoecoae.host.network.mode.local", "Local x1");
        provider.add("gui.neoecoae.host.network.mode.normal", "Network Exchange x2");
        provider.add("gui.neoecoae.host.network.mode.high_energy", "High-Energy Exchange x8");
        provider.add("gui.neoecoae.host.network.connected", "NETWORK ONLINE");
        provider.add("gui.neoecoae.host.network.disconnected", "NETWORK OFFLINE");
        provider.add("gui.neoecoae.host.network.frequency", "Network Frequency: %s");
        provider.add(
                "gui.neoecoae.host.network.frequency.tooltip",
                "Hosts with the same frequency join the same logical cluster. Click to cycle.");
        provider.add("gui.neoecoae.crafting.run_status.normal", "RUNNING NORMALLY");
        provider.add("gui.neoecoae.crafting.run_status.missing_coolant", "MISSING COOLANT");
        provider.add("gui.neoecoae.crafting.run_status.missing_energy", "MISSING ENERGY");
        provider.add("gui.neoecoae.crafting.run_status.overclock_mismatch", "COOLANT / OVERCLOCK MISMATCH");
        provider.add(
                "gui.neoecoae.crafting.run_status.network_coolant_incompatible",
                "COOLANT / NETWORK MODULE INCOMPATIBLE");
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
        provider.add("gui.neoecoae.crafting.overclock.on", "Disable Overclock");
        provider.add("gui.neoecoae.crafting.overclock.off", "Enable Overclock");
        provider.add("gui.neoecoae.crafting.active_cooling", "Active Cooling");
        provider.add("gui.neoecoae.crafting.active_cooling.on", "Disable Active Cooling");
        provider.add("gui.neoecoae.crafting.active_cooling.off", "Enable Active Cooling");
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
