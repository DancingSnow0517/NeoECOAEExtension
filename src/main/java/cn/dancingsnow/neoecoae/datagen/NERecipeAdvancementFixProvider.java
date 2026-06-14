package cn.dancingsnow.neoecoae.datagen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NeoECOAE.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NERecipeAdvancementFixProvider implements DataProvider {
    private final PackOutput output;

    public NERecipeAdvancementFixProvider(PackOutput output) {
        this.output = output;
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        generator.addProvider(event.includeServer(), new NERecipeAdvancementFixProvider(generator.getPackOutput()));
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.runAsync(() -> {
            Path root = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                    .resolve(NeoECOAE.MOD_ID)
                    .resolve("advancements")
                    .resolve("recipes");
            if (!Files.isDirectory(root)) {
                return;
            }

            List<Path> files = new ArrayList<>();
            try (var paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".json")).forEach(files::add);
            } catch (IOException e) {
                throw new RuntimeException("Failed to scan NeoECOAE recipe advancements", e);
            }

            for (Path file : files) {
                fixAdvancement(cache, file);
            }
        });
    }

    private static void fixAdvancement(CachedOutput cache, Path file) {
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read recipe advancement " + file, e);
        }

        if (fixInventoryChangedCriteria(json)) {
            try {
                DataProvider.saveStable(cache, json, file).join();
            } catch (RuntimeException e) {
                throw new RuntimeException("Failed to write fixed recipe advancement " + file, e);
            }
        }
    }

    private static boolean fixInventoryChangedCriteria(JsonObject advancement) {
        JsonObject criteria =
                advancement.has("criteria") && advancement.get("criteria").isJsonObject()
                        ? advancement.getAsJsonObject("criteria")
                        : null;
        if (criteria == null) {
            return false;
        }

        boolean changed = false;
        for (String criterionName : criteria.keySet()) {
            JsonObject criterion = criteria.getAsJsonObject(criterionName);
            if (!criterion.has("trigger")
                    || !"minecraft:inventory_changed"
                            .equals(criterion.get("trigger").getAsString())
                    || !criterion.has("conditions")
                    || !criterion.get("conditions").isJsonObject()) {
                continue;
            }

            JsonObject conditions = criterion.getAsJsonObject("conditions");
            if (!conditions.has("items") || !conditions.get("items").isJsonArray()) {
                continue;
            }

            for (JsonElement element : conditions.getAsJsonArray("items")) {
                if (element.isJsonObject()) {
                    changed |= fixItemPredicate(element.getAsJsonObject());
                }
            }
        }
        return changed;
    }

    private static boolean fixItemPredicate(JsonObject predicate) {
        if (!predicate.has("items")
                || !predicate.get("items").isJsonPrimitive()
                || !predicate.get("items").getAsJsonPrimitive().isString()) {
            return false;
        }

        String value = predicate.get("items").getAsString();
        predicate.remove("items");
        if (value.startsWith("#")) {
            predicate.addProperty("tag", value.substring(1));
        } else {
            JsonArray items = new JsonArray();
            items.add(value);
            predicate.add("items", items);
        }
        return true;
    }

    @Override
    public String getName() {
        return "NeoECOAE recipe advancement format fix";
    }
}
