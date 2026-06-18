package cn.dancingsnow.neoecoae.registration;

import appeng.api.networking.IInWorldGridNodeHost;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.capabilities.Capabilities;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public class NEBlockEntityEntry<T extends NEBlockEntity<?, T>> extends BlockEntityEntry<T> {
    private final BlockEntry<? extends NEBlock<T>> blockEntry;

    @Nullable private final BlockEntityTicker<T> clientTicker;

    @Nullable private final BlockEntityTicker<T> serverTicker;

    public NEBlockEntityEntry(
            AbstractRegistrate<?> owner,
            RegistryObject<BlockEntityType<T>> delegate,
            BlockEntry<? extends NEBlock<T>> blockEntry,
            @Nullable BlockEntityTicker<T> clientTicker,
            @Nullable BlockEntityTicker<T> serverTicker) {
        super(owner, delegate);
        this.blockEntry = blockEntry;
        this.clientTicker = clientTicker;
        this.serverTicker = serverTicker;
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        //noinspection unchecked
        blockEntry
                .get()
                .setBlockEntity(
                        (Class<T>) get().create(BlockPos.ZERO, blockEntry.get().defaultBlockState())
                                .getClass(),
                        get(),
                        clientTicker,
                        serverTicker);

        AEBaseBlockEntity.registerBlockEntityItem(get(), blockEntry.asItem());
    }

    public <C> net.minecraftforge.common.util.LazyOptional<C> getCapability(
            net.minecraftforge.common.capabilities.Capability<C> cap, T blockEntity) {
        if (cap == Capabilities.IN_WORLD_GRID_NODE_HOST) {
            return net.minecraftforge.common.util.LazyOptional.of(() -> (IInWorldGridNodeHost) blockEntity)
                    .cast();
        }
        return net.minecraftforge.common.util.LazyOptional.empty();
    }
}
