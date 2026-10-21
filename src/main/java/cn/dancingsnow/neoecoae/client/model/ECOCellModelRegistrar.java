package cn.dancingsnow.neoecoae.client.model;

import cn.dancingsnow.neoecoae.api.ECOCellModels;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

@EventBusSubscriber
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
