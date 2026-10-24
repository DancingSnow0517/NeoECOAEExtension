package cn.dancingsnow.neoecoae.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A viewer material relationship; execution variants keep their own batch, time and payment contract. */
public record LargeWorkstationRecipeGroup(LargeWorkstationRecipe representative, List<Variant> variants) {
    public LargeWorkstationRecipeGroup {
        Objects.requireNonNull(representative);
        variants = List.copyOf(variants);
        if (variants.isEmpty() || variants.stream().noneMatch(variant -> variant.recipe() == representative)) {
            throw new IllegalArgumentException("Representative must belong to the group");
        }
    }

    public List<LargeWorkstationModule> supportedModules() {
        return variants.stream().flatMap(variant -> variant.modules().stream()).distinct().sorted().toList();
    }
    public List<ResourceLocation> upgradeCards() { return supportedModules().stream().map(LargeWorkstationModule::upgradeCardId).toList(); }
    public boolean availableWithoutCard() { return variants.stream().anyMatch(Variant::unrestricted); }
    public List<Variant> availableVariants(LargeWorkstationModule installed) { return variants.stream().filter(variant -> variant.unlocks(installed)).toList(); }

    public record Variant(LargeWorkstationRecipe recipe, ResourceLocation sourceId, ResourceLocation sourceType,
                          Set<LargeWorkstationModule> modules, boolean unrestricted, boolean borrowed) {
        public Variant {
            Objects.requireNonNull(recipe);
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(sourceType);
            modules = Set.copyOf(modules);
            if (!unrestricted && modules.isEmpty()) throw new IllegalArgumentException("Missing module requirement");
        }
        public boolean unlocks(LargeWorkstationModule installed) { return unrestricted || modules.contains(installed); }
    }
}
