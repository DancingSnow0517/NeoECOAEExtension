package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

/** Only the approved legacy Structure Terminal language contract; registration owns the item name. */
final class StructureTerminalLangs {
    private StructureTerminalLangs() {}
    static void accept(RegistrateLangProvider provider) {
        provider.add("gui.neoecoae.structure_terminal.available", "Owned: %s");
        provider.add("gui.neoecoae.structure_terminal.hint_shift_build", "Shift+Right-click the Controller to build");
        provider.add("gui.neoecoae.structure_terminal.host_selection", "Controller Selection");
        provider.add("gui.neoecoae.structure_terminal.length", "Variable Length: %s");
        provider.add("gui.neoecoae.structure_terminal.length_range", "Min: %s  Max: %s");
        provider.add("gui.neoecoae.structure_terminal.missing", "Missing: %s");
        provider.add("gui.neoecoae.structure_terminal.mode.build", "Build");
        provider.add("gui.neoecoae.structure_terminal.mode.build.short", "Build");
        provider.add("gui.neoecoae.structure_terminal.mode.build.tooltip", "Build the standard structure");
        provider.add("gui.neoecoae.structure_terminal.mode.dismantle", "Dismantle");
        provider.add("gui.neoecoae.structure_terminal.mode.dismantle.short", "Dism.");
        provider.add("gui.neoecoae.structure_terminal.mode.dismantle.tooltip", "Dismantle the current structure");
        provider.add("gui.neoecoae.structure_terminal.mode.mirrored_build", "Mirrored");
        provider.add("gui.neoecoae.structure_terminal.mode.mirrored_build.short", "Mirror");
        provider.add("gui.neoecoae.structure_terminal.mode.mirrored_build.tooltip", "Build a mirrored structure");
        provider.add("gui.neoecoae.structure_terminal.no_materials", "No required materials");
        provider.add("gui.neoecoae.structure_terminal.preview_formed", "Form Preview");
        provider.add("gui.neoecoae.structure_terminal.preview_formed.short", "Form");
        provider.add("gui.neoecoae.structure_terminal.preview_mirrored", "Mirrored Preview");
        provider.add("gui.neoecoae.structure_terminal.preview_mirrored.short", "Mirror");
        provider.add("gui.neoecoae.structure_terminal.preview_unformed", "Prototype");
        provider.add("gui.neoecoae.structure_terminal.required", "Required: %s");
        provider.add("gui.neoecoae.structure_terminal.required_materials", "Required Blocks");
        provider.add("gui.neoecoae.structure_terminal.reset", "Reset");
        provider.add("gui.neoecoae.structure_terminal.target.computation", "Computation");
        provider.add("gui.neoecoae.structure_terminal.target.computation.short", "Comp");
        provider.add("gui.neoecoae.structure_terminal.target.computation.tooltip", "Computation Subsystem");
        provider.add("gui.neoecoae.structure_terminal.target.crafting", "Crafting");
        provider.add("gui.neoecoae.structure_terminal.target.crafting.short", "Craft");
        provider.add("gui.neoecoae.structure_terminal.target.crafting.tooltip", "Crafting Subsystem");
        provider.add("gui.neoecoae.structure_terminal.target.storage", "Storage");
        provider.add("gui.neoecoae.structure_terminal.target.storage.short", "Store");
        provider.add("gui.neoecoae.structure_terminal.target.storage.tooltip", "Storage Subsystem");
        provider.add("gui.neoecoae.structure_terminal.unknown_material", "Unknown material");
        provider.add("gui.neoecoae.structure_terminal.variable_sections", "Variable Sections: %s [%s-%s]");
    }
}
