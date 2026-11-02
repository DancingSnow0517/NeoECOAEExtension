package cn.dancingsnow.neoecoae.recipe;

import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One material relationship for viewers; its original execution variants retain their legal batch sizes. */
public record LargeWorkstationRecipeGroup(LargeWorkstationRecipe representative, List<Variant> variants) {
    public LargeWorkstationRecipeGroup {
        Objects.requireNonNull(representative);
        variants = List.copyOf(variants);
        if (variants.isEmpty() || variants.stream().noneMatch(variant -> variant.recipe() == representative)) {
            throw new IllegalArgumentException("The representative must be an original variant");
        }
    }

    /** Any one of these module IDs can execute a variant; this does not make all variants interchangeable. */
    public List<Identifier> supportedModules() {
        return variants.stream().flatMap(variant -> variant.modules().stream()).distinct()
            .sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    public List<Identifier> upgradeCards() {
        return supportedModules().stream().map(id -> ECOWorkstationModules.resolve(id).upgradeCard()).distinct().toList();
    }

    public boolean availableWithoutCard() {
        return variants.stream().anyMatch(Variant::unrestricted);
    }

    public List<Variant> availableVariants(Identifier installedModule) {
        return LargeWorkstationRecipes.selectExecutionVariants(
            variants.stream().filter(variant -> variant.unlocks(installedModule)).toList());
    }

    /** Viewers show the same fixed costs as execution, including each card's independent winner. */
    public List<Variant> displayVariants() {
        var selected = new java.util.HashSet<Variant>();
        for (var module : supportedModules()) selected.addAll(availableVariants(module));
        selected.addAll(LargeWorkstationRecipes.selectExecutionVariants(
            variants.stream().filter(Variant::unrestricted).toList()));
        return variants.stream().filter(selected::contains).toList();
    }

    /** sourceId/type identify the real upstream recipe, even when recipe.id() is a borrowed execution alias. */
    public record Variant(LargeWorkstationRecipe recipe, Identifier sourceId, Identifier sourceType,
                          Set<Identifier> modules, boolean unrestricted, boolean borrowed) {
        public Variant {
            Objects.requireNonNull(recipe);
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(sourceType);
            modules = Set.copyOf(modules);
            if (!unrestricted && modules.isEmpty()) throw new IllegalArgumentException("Missing module requirement");
        }

        public boolean unlocks(Identifier installedModule) {
            return unrestricted || modules.contains(installedModule);
        }
    }
}
