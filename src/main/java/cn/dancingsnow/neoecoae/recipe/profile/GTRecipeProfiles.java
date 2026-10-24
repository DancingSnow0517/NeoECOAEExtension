package cn.dancingsnow.neoecoae.recipe.profile;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.config.RecipeProfileConfig;
import cn.dancingsnow.neoecoae.config.RecipeProfileConfig.Profile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Optional GT progression is expressed as ordinary, required server datapacks. */
public final class GTRecipeProfiles {
    public enum Schema {
        LEGACY, MODERN;

        static Schema fromRegistryType(String type) {
            return switch (type) {
                case "com.gregtechceu.gtceu.api.registry.GTRegistry$String" -> LEGACY;
                case "com.gregtechceu.gtceu.api.registry.GTRegistry$RL" -> MODERN;
                default -> throw new IllegalStateException("Unsupported GT recipe capability registry: " + type);
            };
        }
    }

    private GTRecipeProfiles() {}

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        var packs = packPaths(RecipeProfileConfig.profile(), ModList.get()::isLoaded, GTRecipeProfiles::detectSchema);
        if (packs.isEmpty()) return;
        var modFile = ModList.get().getModFileById(NeoECOAE.MOD_ID).getFile();
        event.addRepositorySource(consumer -> {
            for (var path : packs) {
                var root = modFile.findResource("recipe_profiles", path);
                var pack = Pack.readMetaAndCreate("neoecoae:recipe_profile/" + path,
                    Component.literal("NeoECOAE " + path.toUpperCase(Locale.ROOT) + " recipes"), true,
                    id -> new PathPackResources(id, true, root), PackType.SERVER_DATA, Pack.Position.TOP,
                    PackSource.BUILT_IN);
                if (pack == null) throw new IllegalStateException("Missing NeoECOAE recipe profile pack: " + path);
                consumer.accept(pack);
            }
        });
    }

    static List<String> packPaths(Profile profile, Predicate<String> loaded, Supplier<Schema> schema) {
        if (profile == Profile.STANDALONE) return List.of();
        if (!loaded.test("gtceu")) {
            throw new IllegalStateException("NeoECOAE recipe profile " + profile + " requires gtceu; "
                + "install GTM or set config/neoecoae-recipes-common.toml profile to STANDALONE.");
        }
        if (profile == Profile.GTL && !loaded.test("gtlcore")) {
            throw new IllegalStateException("NeoECOAE GTL recipe profile requires both gtceu and gtlcore.");
        }
        return List.of(profile.name().toLowerCase(Locale.ROOT),
            "gt_" + schema.get().name().toLowerCase(Locale.ROOT));
    }

    private static Schema detectSchema() {
        try {
            // Inspect the actual registry contract, not the mod's version string or the presence of EnergyStack.
            // String-keyed 1.4.4 and namespaced newer APIs require different capability/condition JSON keys.
            var registries = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries", false,
                GTRecipeProfiles.class.getClassLoader());
            return Schema.fromRegistryType(registries.getField("RECIPE_CAPABILITIES").getType().getName());
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Cannot identify the installed GT recipe API for the selected profile", error);
        }
    }
}
