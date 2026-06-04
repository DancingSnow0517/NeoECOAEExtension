package cn.dancingsnow.neoecoae.data;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.data.lang.NELangGenerator;
import cn.dancingsnow.neoecoae.data.provider.NELangMergerProvider;
import cn.dancingsnow.neoecoae.data.provider.NERegistryProvider;
import cn.dancingsnow.neoecoae.data.recipe.NERecipeGenerator;
import cn.dancingsnow.neoecoae.data.tag.NETagGenerator;
import com.tterrag.registrate.providers.ProviderType;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID)
public class NEDataGen {
    public static void configureDataGen() {
        // REGISTRATE.addDataGenerator(NEProviderTypes.CELL_MODEL, CellModelGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.LANG, NELangGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.RECIPE, NERecipeGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.ITEM_TAGS, NETagGenerator::itemTag);
        REGISTRATE.addDataGenerator(ProviderType.LOOT, prov -> {});
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();
        PackOutput packOutput = generator.getPackOutput();

        generator.addProvider(event.includeServer(), new NERegistryProvider(packOutput, registries));
        generator.addProvider(event.includeClient(), new NELangMergerProvider(packOutput));
    }
}
