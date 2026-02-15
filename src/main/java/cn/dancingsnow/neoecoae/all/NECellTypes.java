package cn.dancingsnow.neoecoae.all;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NECellTypes {
    public static final DeferredRegister<ECOCellType> CELL_TYPES = DeferredRegister.create(NERegistries.Keys.CELL_TYPE, NeoECOAE.MOD_ID);

    public static final DeferredHolder<ECOCellType, ECOCellType> ITEM = CELL_TYPES.register(
        "items",
        () -> new ECOCellType(AEKeyType.items().getDescription())
    );

    public static final DeferredHolder<ECOCellType, ECOCellType> FLUID = CELL_TYPES.register(
        "fluids",
        () -> new ECOCellType(AEKeyType.fluids().getDescription())
    );

    public static void register(IEventBus modBus) {
        CELL_TYPES.register(modBus);
    }
}
