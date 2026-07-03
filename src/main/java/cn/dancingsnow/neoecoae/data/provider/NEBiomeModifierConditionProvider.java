package cn.dancingsnow.neoecoae.data.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import org.slf4j.Logger;

public class NEBiomeModifierConditionProvider implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PackOutput packOutput;

    public NEBiomeModifierConditionProvider(PackOutput packOutput) {
        this.packOutput = packOutput;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = packOutput
                .getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(NeoECOAE.MOD_ID)
                .resolve("forge")
                .resolve("biome_modifier")
                .resolve("ore_end.json");
        JsonObject biomeModifier;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            biomeModifier = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            LOGGER.error("Error reading generated End ore biome modifier {}", path, e);
            throw new RuntimeException(e);
        }

        biomeModifier.add("conditions", gtceuNotLoadedConditions());
        return DataProvider.saveStable(output, biomeModifier, path);
    }

    private static JsonArray gtceuNotLoadedConditions() {
        JsonObject modLoaded = new JsonObject();
        modLoaded.addProperty("type", "forge:mod_loaded");
        modLoaded.addProperty("modid", "gtceu");

        JsonObject notLoaded = new JsonObject();
        notLoaded.addProperty("type", "forge:not");
        notLoaded.add("value", modLoaded);

        JsonArray conditions = new JsonArray();
        conditions.add(notLoaded);
        return conditions;
    }

    @Override
    public String getName() {
        return "NeoECOAE Biome Modifier Conditions";
    }
}
