package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

/** Terminal notice and the length range shared with the host builder. */
final class StructureTerminalLangs {
    private StructureTerminalLangs() {}
    static void accept(RegistrateLangProvider provider) {
        provider.add("gui.neoecoae.structure_terminal.hint_host_builder",
                "Please use the Build button in the corresponding host's UI.");
        provider.add("gui.neoecoae.structure_terminal.length_range", "Min: %s  Max: %s");
    }
}
