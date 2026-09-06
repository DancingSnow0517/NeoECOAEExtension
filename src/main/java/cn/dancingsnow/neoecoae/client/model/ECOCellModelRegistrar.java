package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.api.ECOCellModels;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber
public final class ECOCellModelRegistrar {
    private ECOCellModelRegistrar() {
    }

    @SubscribeEvent
    public static void on(ModelEvent.RegisterAdditional event) {
        ECOCellModels.getDeferredModels().values().forEach(location ->
            event.register(ModelResourceLocation.standalone(location)));
        event.register(ModelResourceLocation.standalone(ECOCellModels.DEFAULT_MODEL));
    }
}
