package cn.dancingsnow.neoecoae.data.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class NELangMergerProvider implements DataProvider {

    public static final Logger LOGGER = LogUtils.getLogger();

    private final PackOutput packOutput;
    private final String modid;

    public NELangMergerProvider(PackOutput packOutput) {
        this(packOutput, NeoECOAE.MOD_ID);
    }

    public NELangMergerProvider(PackOutput packOutput, String modid) {
        this.packOutput = packOutput;
        this.modid = modid;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path langPath = packOutput.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(modid).resolve("lang");
        Path path = langPath.resolve("en_us.json");
        JsonElement original;
        try (BufferedReader bfr = Files.newBufferedReader(path)) {
            original = JsonParser.parseReader(bfr);
        } catch (IOException e) {
            LOGGER.error("Error reading language file!", e);
            throw new RuntimeException(e);
        }

        List<CompletableFuture<?>> saveFutures = new ArrayList<>();
        for (Pair<String, JsonElement> pair : getAllLocalizationFiles()) {
            String lang = pair.getFirst();
            JsonObject localization = pair.getSecond().getAsJsonObject();
            LOGGER.info("Loading localization file: {}", pair.getFirst());
            JsonObject newElement = new JsonObject();
            int unlocalized = 0;
            for (Map.Entry<String, JsonElement> entry : original.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue().getAsString();
                if (localization.has(key)) {
                    newElement.add(key, localization.get(key));
                } else {
                    newElement.addProperty(key, "UNLOCALIZED: " + value);
                    unlocalized++;
                }
            }
            if (unlocalized > 0) {
                saveFutures.add(DataProvider.saveStable(output, newElement, langPath.resolve("unfinished").resolve(lang)));
            }
        }
        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture<?>[0]));
    }

    private List<Pair<String, JsonElement>> getAllLocalizationFiles() {
        List<Pair<String, JsonElement>> localizationFiles = new ArrayList<>();
        IModFile file = ModList.get().getModFileById(modid).getFile();
        try (Stream<Path> listDir = Files.list(file.findResource("assets", modid, "lang"))) {
            listDir.forEach(p -> {
                String fileName = p.getFileName().toString();
                if (!fileName.endsWith(".json") || fileName.equals("en_us.json") || fileName.equals("en_ud.json")) {
                    return;
                }
                try (BufferedReader bfr = Files.newBufferedReader(p)) {
                    localizationFiles.add(Pair.of(p.getFileName().toString(), JsonParser.parseReader(bfr)));
                } catch (IOException e) {
                    LOGGER.error("Error reading file {}", p, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to list assets in lang file!", e);
        }


        return localizationFiles;
    }

    @Override
    public String getName() {
        return "Lang Merger";
    }
}
