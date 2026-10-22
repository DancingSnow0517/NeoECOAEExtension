package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ECOCellModelRegistrar {
    private ECOCellModelRegistrar() {
    }

    @SubscribeEvent
    public static void on(ModelEvent.RegisterAdditional event) {
        ECOCellModels.getDeferredModels().values().forEach(location ->
            event.register(location));
        event.register(ECOCellModels.DEFAULT_MODEL);
    }
}
