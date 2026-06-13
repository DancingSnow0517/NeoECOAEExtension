package cn.dancingsnow.neoecoae.registration;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.blockentity.AEBaseBlockEntity;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

public class NEBlockEntityEntry<T extends NEBlockEntity<?, T>> extends BlockEntityEntry<T> {
    private final BlockEntry<? extends NEBlock<T>> blockEntry;
    @Nullable
    private final BlockEntityTicker<T> clientTicker;
    @Nullable
    private final BlockEntityTicker<T> serverTicker;
    private final Class<?> clazz;

    public NEBlockEntityEntry(
        AbstractRegistrate<?> owner,
        DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> delegate,
        BlockEntry<? extends NEBlock<T>> blockEntry,
        @Nullable BlockEntityTicker<T> clientTicker,
        @Nullable BlockEntityTicker<T> serverTicker,
        Class<?> clazz
    ) {
        super(owner, delegate);
        this.blockEntry = blockEntry;
        this.clientTicker = clientTicker;
        this.serverTicker = serverTicker;
        this.clazz = clazz;
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        AEBaseBlockEntity.registerBlockEntityItem(
            getDelegate().value(),
            blockEntry.asItem()
        );
        //noinspection unchecked
        blockEntry.get().setBlockEntity(
            (Class<T>) clazz,
            (BlockEntityType<T>) getDelegate().value(),
            clientTicker,
            serverTicker
        );
    }

    public void onRegisterCapabilies(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            this.getDelegate().value(),
            (o, unused) -> (IInWorldGridNodeHost) o
        );
    }
}
