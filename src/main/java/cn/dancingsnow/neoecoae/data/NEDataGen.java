package cn.dancingsnow.neoecoae.data;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.data.lang.NELangGenerator;
import cn.dancingsnow.neoecoae.data.model.CellModelGenerator;
import cn.dancingsnow.neoecoae.data.provider.NEBiomeModifierConditionProvider;
import cn.dancingsnow.neoecoae.data.provider.NELangMergerProvider;
import cn.dancingsnow.neoecoae.data.provider.NERegistryProvider;
import cn.dancingsnow.neoecoae.data.recipe.NERecipeGenerator;
import cn.dancingsnow.neoecoae.data.tag.NETagGenerator;
import cn.dancingsnow.neoecoae.registration.provider.NEProviderTypes;
import com.tterrag.registrate.providers.ProviderType;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NEDataGen {
    private static boolean configured;

    public static void configureDataGen() {
        if (configured) {
            return;
        }
        configured = true;

        REGISTRATE.addDataGenerator(NEProviderTypes.CELL_MODEL, CellModelGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.LANG, NELangGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.RECIPE, NERecipeGenerator::accept);
        REGISTRATE.addDataGenerator(ProviderType.ITEM_TAGS, NETagGenerator::itemTag);
        REGISTRATE.addDataGenerator(ProviderType.LOOT, prov -> {});
    }

    @SubscribeEvent
    public static void configureRegistrateProviders(GatherDataEvent event) {
        configureDataGen();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();
        PackOutput packOutput = generator.getPackOutput();

        generator.addProvider(event.includeServer(), new NERegistryProvider(packOutput, registries));
        generator.addProvider(event.includeServer(), new NEBiomeModifierConditionProvider(packOutput));
        generator.addProvider(event.includeClient(), new NELangMergerProvider(packOutput));
    }
}
