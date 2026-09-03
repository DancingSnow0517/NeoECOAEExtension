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
        provider.add("gui.neoecoae.crafting.ui.single_core_capacity", "Processing Capacity");
        provider.add("gui.neoecoae.crafting.ui.recipe_time_ratio", "Runtime");
        provider.add("gui.neoecoae.crafting.ui.batch_per_thread.detail", "Authoritative Crafting Capability");
        provider.add("gui.neoecoae.crafting.ui.batch_per_thread.total", "Max Crafts per Tick: %s");
        provider.add("gui.neoecoae.host.crafting.host_line", "%s · FT Parallel Capacity %d · Batch %d");
        provider.add("gui.neoecoae.crafting.capability.fx", "FX Cores: %d active / %d physical");
        provider.add("gui.neoecoae.crafting.capability.network_composition", "Network: x2 %d / x8 %d");
        provider.add("gui.neoecoae.crafting.capability.network_multiplier", "Network Multiplier M: %d");
        provider.add("gui.neoecoae.crafting.capability.batch_per_fx", "Batch per FX: %s");
        provider.add("gui.neoecoae.crafting.capability.total", "Total Batch Capacity: %s");
        provider.add("gui.neoecoae.crafting.capability.ft_parallel", "FT Parallel Capacity: %s");
        provider.add("gui.neoecoae.crafting.capability.overclock", "Overclock: %d theoretical / %d effective");
        provider.add("gui.neoecoae.host.crafting.host_type.high_energy", "High-Energy");
        provider.add("gui.neoecoae.host.crafting.host_type.normal", "Normal");
        provider.add("gui.neoecoae.crafting.ui.energy_usage", "Energy Usage");
        provider.add("gui.neoecoae.crafting.performance", "Performance");
        provider.add("gui.neoecoae.crafting.task.status.running", "Running");
        provider.add("gui.neoecoae.crafting.task.status.queued", "Queued");
        provider.add("gui.neoecoae.crafting.task.status.waiting_output", "Waiting for output");
        provider.add("gui.neoecoae.crafting.fast_path_reason", "FastPath miss reason: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.cache_miss", "No verified result in the cache");
        provider.add("gui.neoecoae.crafting.fast_path_reason.fast_path_disabled", "FastPath is disabled");
        provider.add("gui.neoecoae.crafting.fast_path_reason.post_crafting_event_enabled",
            "Crafting events are enabled, so FastPath is disabled");
        provider.add("gui.neoecoae.crafting.fast_path_reason.key_build_failed", "Cannot build the cache key");
        provider.add("gui.neoecoae.crafting.fast_path_reason.ae2_introspection_unavailable",
            "AE2 pattern information is unavailable");
        provider.add("gui.neoecoae.crafting.fast_path_reason.unsafe_pattern_type", "Unsupported pattern type");
        provider.add("gui.neoecoae.crafting.fast_path_reason.slow_execution_context", "Slow execution context");
        provider.add("gui.neoecoae.crafting.fast_path_reason.cache_result_mismatch", "Cached result does not match");
        provider.add("gui.neoecoae.crafting.fast_path_reason.negative_cache", "The recipe is marked as unsupported");
        provider.add("gui.neoecoae.crafting.fast_path_reason.cached_result_materialization_failed",
            "Cached result could not be materialized");
        provider.add("gui.neoecoae.crafting.fast_path_reason.verified_output_or_input_conversion_failed",
            "Verified output or input conversion failed");
        provider.add("gui.neoecoae.crafting.fast_path_reason.assembly_contract_mismatch",
            "Assembly result does not match the pattern contract");
        provider.add("gui.neoecoae.crafting.fast_path_reason.state_second_step_proof_failed",
            "Second state transition proof failed");
        provider.add("gui.neoecoae.crafting.fast_path_reason.verified_stack_validation_failed",
            "Verified stack validation failed");
        provider.add("gui.neoecoae.crafting.fast_path_reason.reusable_state_model_missing",
            "Reusable state model is missing");
        provider.add("gui.neoecoae.crafting.fast_path_reason.state_slot_count_mismatch",
            "State slot count changed");
        provider.add("gui.neoecoae.crafting.fast_path_reason.mixed_reusable_state_models",
            "Reusable state models are mixed");
        provider.add("gui.neoecoae.crafting.fast_path_reason.durability_transition_invalid",
            "Durability transition is invalid");
        provider.add("gui.neoecoae.crafting.fast_path_reason.state_transition_not_provably_linear",
            "State transition cannot be proven linear");
        provider.add("gui.neoecoae.crafting.fast_path_reason.verification_rejected", "Recipe verification was rejected");
        provider.add("gui.neoecoae.crafting.fast_path_reason.pattern_input_inspection_failed",
            "Pattern inputs could not be inspected");
        provider.add("gui.neoecoae.crafting.fast_path_reason.pattern_null", "Pattern is missing");
        provider.add("gui.neoecoae.crafting.fast_path_reason.no_inputs", "Pattern has no inputs");
        provider.add("gui.neoecoae.crafting.fast_path_reason.no_outputs", "Pattern has no outputs");
        provider.add("gui.neoecoae.crafting.fast_path_reason.non_item_output", "Pattern output is not an item");
        provider.add("gui.neoecoae.crafting.fast_path_reason.invalid_input", "Pattern input is invalid");
        provider.add("gui.neoecoae.crafting.fast_path_reason.non_item_input", "Pattern input is not an item");
        provider.add("gui.neoecoae.crafting.fast_path_reason.invalid_item_input", "Pattern input item is invalid");
        provider.add("gui.neoecoae.crafting.fast_path_reason.invalid_remainder", "Pattern remainder is invalid");
        provider.add("gui.neoecoae.crafting.fast_path_reason.remainder_is_not_reusable_item",
            "Remainder is not a reusable item");
        provider.add("gui.neoecoae.crafting.fast_path_reason.runtime_simulation_required",
            "Runtime simulation is required");
        provider.add("gui.neoecoae.crafting.fast_path_reason.one_to_one_reusable_item_or_component",
            "One-to-one reusable item or component");
        provider.add("gui.neoecoae.crafting.fast_path_reason.static_item_contract", "Static item contract");
        provider.add("gui.neoecoae.crafting.fast_path_reason.multiple", "Multiple reasons");
        provider.add("gui.neoecoae.crafting.fast_path_reason.unknown", "Unknown reason");
        provider.add("gui.neoecoae.crafting.fast_path_reason.unknown_code", "Unknown reason: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.output_count", "Output count is not 1: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.output", "Output validation failed: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.remainder", "Remainder validation failed: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.input", "Input validation failed: %s");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.null_collection", "collection is null");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.too_many_entries", "too many entries");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.empty_required", "required collection is empty");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.null_stack", "stack is null");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.invalid_amount", "amount is invalid");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.non_item_key", "key is not an item");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.empty_item_stack", "item stack is empty");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.damaged_item", "item is damaged");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.component_patch", "component patch is unsupported");
        provider.add("gui.neoecoae.crafting.fast_path_reason.validation.unknown_validation", "unknown validation failure");
        provider.add("gui.neoecoae.crafting.fast_path_reason.classifier_failed", "Pattern classifier failed: %s");
        provider.add("gui.neoecoae.crafting.overclock.on", "Disable Overclock");
        provider.add("gui.neoecoae.crafting.overclock.off", "Enable Overclock");
        provider.add("gui.neoecoae.crafting.active_cooling.on", "Disable Active Cooling");
        provider.add("gui.neoecoae.crafting.active_cooling.off", "Enable Active Cooling");
        provider.add("gui.neoecoae.crafting.planning.ignore_substitutions.on",
            "Disable Ignore Pattern Substitutions");
        provider.add("gui.neoecoae.crafting.planning.ignore_substitutions.off",
            "Enable Ignore Pattern Substitutions");
        provider.add("gui.neoecoae.crafting.planning.substitution_pattern_count",
            "Currently %d substitution-enabled patterns");
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
        provider.add("gui.neoecoae.crafting_report.single_net_output", "Single change: %s");
        provider.add("gui.neoecoae.crafting_report.total_net_output", "Total change: %s");
        provider.add("gui.neoecoae.crafting_report.total_net_output_unknown", "Total change: unknown");
        provider.add("gui.neoecoae.crafting_report.cycle_not_detected", "No cycles detected");
        provider.add("gui.neoecoae.crafting_report.cycle_planning_enabled", "Cycle detected");
        provider.add("gui.neoecoae.crafting_report.cycle_planning_disabled", "Cycle planning is disabled");
        provider.add("gui.neoecoae.crafting_report.missing_startup_seed", "Missing startup seed");
        provider.add("gui.neoecoae.crafting_report.solving_large_cycle", "[ECO] Solving large cycle");
        provider.add("gui.neoecoae.crafting_graph.search_hint", "Search AEKey / item");
        provider.add("gui.neoecoae.crafting_graph.node.pending_craft", "Pending craft");
        provider.add("gui.neoecoae.crafting_graph.toolbar.fit_all", "Fit All");
        provider.add("gui.neoecoae.crafting_graph.toolbar.missing_on", "Missing only: On");
        provider.add("gui.neoecoae.crafting_graph.toolbar.missing_off", "Missing only: Off");
        provider.add("gui.neoecoae.crafting_graph.toolbar.root", "Root");
        provider.add("gui.neoecoae.crafting_graph.toolbar.expand", "Expand");
        provider.add("gui.neoecoae.crafting_graph.toolbar.collapse", "Collapse");
        provider.add("gui.neoecoae.crafting_graph.toolbar.expand_all", "All");
        provider.add("gui.neoecoae.crafting_graph.toolbar.depth", "Depth: %s");
        provider.add("gui.neoecoae.crafting_graph.toolbar.depth_all", "Depth: All");
        provider.add("gui.neoecoae.crafting_graph.toolbar.depth_four", "D4");
        provider.add("gui.neoecoae.crafting_graph.toolbar.fold_four", "Fold4");
        provider.add("gui.neoecoae.crafting_graph.toolbar.view_tree", "View: Tree");
        provider.add("gui.neoecoae.crafting_graph.toolbar.view_graph", "View: Graph");
        provider.add("gui.neoecoae.crafting_graph.toolbar.debug_on", "Debug: ON");
        provider.add("gui.neoecoae.crafting_graph.toolbar.debug_off", "Debug: OFF");
        provider.add("gui.neoecoae.crafting_graph.breadcrumb.plan", "ECO Plan");
        provider.add("gui.neoecoae.crafting_graph.breadcrumb.cycle", "ECO Plan  >  Cycle #%s");
        provider.add("gui.neoecoae.crafting_graph.breadcrumb.cluster", "ECO Plan  >  Cycle Cluster #%s");
        provider.add("gui.neoecoae.crafting_graph.details.cycle_title", "Cycle #%s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_title", "Cycle Cluster #%s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_cycles", "Independent cycles: %s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_flows", "Inter-cycle flows: %s");
        provider.add("gui.neoecoae.crafting_graph.details.cluster_hint", "Double-click a ring node to open one cycle");
        provider.add("gui.neoecoae.crafting_graph.details.status_label", "Status: ");
        provider.add("gui.neoecoae.crafting_graph.details.seed_label", "Seed: ");
        provider.add("gui.neoecoae.crafting_graph.details.external_label", "External: ");
        provider.add("gui.neoecoae.crafting_graph.details.execute_label", "Execute: ");
        provider.add("gui.neoecoae.crafting_graph.details.witness_steps", "Witness steps: %s");
        provider.add("gui.neoecoae.crafting_graph.details.required_outputs", "Required outputs: %s");
        provider.add("gui.neoecoae.crafting_graph.summary.more", ", %s more");
        provider.add("gui.neoecoae.crafting_graph.summary.more_suffix", " more");
    }
}
