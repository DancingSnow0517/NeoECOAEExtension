package cn.dancingsnow.neoecoae.registration;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class NERegistrate extends Registrate {
    private static final Logger logger = LogManager.getLogger(NERegistrate.class);
    protected NERegistrate(String modid) {
        super(modid);
    }

    public static NERegistrate create(String modid) {
        NERegistrate registrate = new NERegistrate(modid);
        Optional<IEventBus> modEventBus = ModList.get().getModContainerById(modid).map(ModContainer::getEventBus);
        modEventBus.ifPresentOrElse(registrate::registerEventListeners, () -> {
            String message = "# [Registrate] Failed to register eventListeners for mod " + modid + ", This should be reported to this mod\'s dev #";
            StringBuilder hashtags = new StringBuilder().append("#".repeat(message.length()));
            logger.fatal(hashtags.toString());
            logger.fatal(message);
            logger.fatal(hashtags.toString());
        });
        return registrate;
    }

    public <T extends NEBlockEntity<?, T>> NEBlockEntityBuilder<T, NERegistrate> blockEntityBlockLinked(String name, BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return blockEntityBlockLinked(this, name, factory);
    }

    public <T extends NEBlockEntity<?, T>> NEBlockEntityBuilder<T, NERegistrate> blockEntityBlockLinked(NERegistrate parent, String name, BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return (NEBlockEntityBuilder<T, NERegistrate>) this.entry(name, callback -> NEBlockEntityBuilder.createMy(this, parent, name, callback, factory));
    }

    public void runCommonSetup() {
        for (RegistryEntry<BlockEntityType<?>, BlockEntityType<?>> entry : getAll(Registries.BLOCK_ENTITY_TYPE)) {
            if (entry instanceof NEBlockEntityEntry entityEntry){
                entityEntry.runCommonSetup();
            }
        }
    }
}
