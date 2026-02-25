package cn.dancingsnow.neoecoae.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class ConfigLangs {
    public static void accept(RegistrateLangProvider provider) {
        provider.add("neoecoae.configuration.structure", "Structure");
        provider.add("neoecoae.configuration.structure.tooltip", "Multiblock structure size limits.");
        provider.add("neoecoae.configuration.craftingSystemMaxLength", "Max Length of Crafting System");
        provider.add(
            "neoecoae.configuration.craftingSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Crafting System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.add("neoecoae.configuration.computationSystemMaxLength", "Max Length of Computation System");
        provider.add(
            "neoecoae.configuration.computationSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Computation System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.add("neoecoae.configuration.storageSystemMaxLength", "Max Length of Storage System");
        provider.add(
            "neoecoae.configuration.storageSystemMaxLength.tooltip",
            "Maximum length (in blocks) allowed for the Storage System multiblock.\n" +
                "Higher values allow longer expansions but may increase structure check cost."
        );
        provider.add("neoecoae.configuration.postCraftingEvent", "Post Crafting Event");
        provider.add(
            "neoecoae.configuration.postCraftingEvent.tooltip",
            "Post a vanilla crafting event (ItemCraftedEvent) when the Crafting System finishes a recipe.\n" +
                "May introduce extra event/listener overhead; can be more noticeable with mods like Balm installed."
        );
    }
}
