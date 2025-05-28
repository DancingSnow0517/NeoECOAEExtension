package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.builders.NoConfigBuilder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class NERegistrate extends AbstractRegistrate<NERegistrate> {
    private static final Logger logger = LogManager.getLogger(NERegistrate.class);

    public static NERegistrate create(String modid) {
        NERegistrate registrate = new NERegistrate(modid);
        Optional<IEventBus> modEventBus = ModList.get().getModContainerById(modid).map(ModContainer::getEventBus);
        modEventBus.ifPresentOrElse(registrate::registerEventListeners, () -> {
            String message = "# [Registrate] Failed to register eventListeners for mod " + modid + ", This should be reported to this mod's dev #";
            StringBuilder hashtags = new StringBuilder().append("#".repeat(message.length()));
            logger.fatal(hashtags.toString());
            logger.fatal(message);
            logger.fatal(hashtags.toString());
        });
        return registrate;
    }

    protected NERegistrate(String modid) {
        super(modid);
    }

    @Override
    public NERegistrate registerEventListeners(IEventBus bus) {
        super.registerEventListeners(bus);
        bus.addListener(this::onCommonSetup);
        bus.addListener(this::onRegisterCapabilities);
        return self();
    }

    public <T extends NEBlockEntity<C, T>, C extends NECluster<C>> NEBlockEntityBuilder<T, NERegistrate> blockEntityClusterElement(
        String name,
        NEClusterCalculator.Factory<T, C> tcFactory,
        ClusterBlockEntityFactory<T, C> factory
    ) {
        return blockEntityBlockLinked(
            this,
            name,
            ((type, pos, state) -> factory.create(type, pos, state, tcFactory))
        );
    }

    public <T extends NEBlockEntity<?, T>> NEBlockEntityBuilder<T, NERegistrate> blockEntityBlockLinked(String name, BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return blockEntityBlockLinked(this, name, factory);
    }

    public <T extends NEBlockEntity<?, T>> NEBlockEntityBuilder<T, NERegistrate> blockEntityBlockLinked(NERegistrate parent, String name, BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return (NEBlockEntityBuilder<T, NERegistrate>) this.entry(name, callback -> NEBlockEntityBuilder.createMy(this, parent, name, callback, factory));
    }

    public NoConfigBuilder<CreativeModeTab, CreativeModeTab, NERegistrate> defaultCreativeTab(String name, CreativeModeTab.Builder builder) {
        return defaultCreativeTab(self(), name, builder);
    }

    public <P> NoConfigBuilder<CreativeModeTab, CreativeModeTab, P> defaultCreativeTab(P parent, String name, CreativeModeTab.Builder builder) {
        defaultCreativeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(getModid(), name)));
        return this.generic(parent, name, Registries.CREATIVE_MODE_TAB, builder::build);
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        for (RegistryEntry<BlockEntityType<?>, BlockEntityType<?>> entry : getAll(Registries.BLOCK_ENTITY_TYPE)) {
            //noinspection rawtypes
            if (entry instanceof NEBlockEntityEntry entityEntry) {
                entityEntry.onCommonSetup(event);
            }
        }
    }

    public void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        for (RegistryEntry<BlockEntityType<?>, BlockEntityType<?>> entry : getAll(Registries.BLOCK_ENTITY_TYPE)) {
            //noinspection rawtypes
            if (entry instanceof NEBlockEntityEntry entityEntry) {
                entityEntry.onRegisterCapabilies(event);
            }
        }
    }

    public interface ClusterBlockEntityFactory<T extends NEBlockEntity<C, T>, C extends NECluster<C>> {
        T create(BlockEntityType<T> type, BlockPos pos, BlockState state, NEClusterCalculator.Factory<T, C> tcFactory);
    }
}
