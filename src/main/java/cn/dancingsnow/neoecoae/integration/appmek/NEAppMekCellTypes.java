package cn.dancingsnow.neoecoae.integration.appmek;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NEAppMekCellTypes {
    public static final DeferredRegister<ECOCellType> CELL_TYPES = DeferredRegister.create(NERegistries.Keys.CELL_TYPE, NeoECOAE.MOD_ID);


    public static final DeferredHolder<ECOCellType, ECOCellType> MEKANISM = CELL_TYPES.register(
        "mekanism",
        () -> new ECOCellType(MekanismKeyType.TYPE.getDescription())
    );

    public static void register(IEventBus modBus) {
        CELL_TYPES.register(modBus);
    }
}
