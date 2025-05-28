package cn.dancingsnow.neoecoae.registration;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

public class NEBlockEntityEntry<T extends NEBlockEntity<?, T>> extends BlockEntityEntry<T> {
    private final Supplier<? extends NEBlock<T>> blockSupplier;

    public NEBlockEntityEntry(
        AbstractRegistrate<?> owner,
        DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> delegate,
        Supplier<NEBlock<T>> blockSupplier
    ) {
        super(owner, delegate);
        this.blockSupplier = blockSupplier;
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        //noinspection unchecked
        blockSupplier.get().setBlockEntity(
            (Class<T>) getDelegate().value().create(BlockPos.ZERO, blockSupplier.get().defaultBlockState()).getClass(),
            (BlockEntityType<T>) getDelegate().value(),
            null,
            null
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
