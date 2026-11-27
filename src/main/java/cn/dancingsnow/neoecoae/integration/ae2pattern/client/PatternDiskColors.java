package cn.dancingsnow.neoecoae.integration.ae2pattern.client;

import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.ae2pattern.NEPatternDiskItems;
import cn.dancingsnow.neoecoae.integration.ae2pattern.PatternDiskSupport;
import io.github.lounode.ae2pattern.api.PatternDiskApi;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public final class PatternDiskColors {
    private PatternDiskColors() {
    }

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.Item event) {
        if (!PatternDiskSupport.available()) {
            return;
        }
        event.register((stack, tintIndex) -> {
            if (tintIndex != 2) {
                return 0xFFFFFFFF;
            }
            var contents = PatternDiskApi.contents(stack);
            CellState state = CellState.ABSENT;
            if (contents != null) {
                state = contents.used() == 0 ? CellState.EMPTY
                    : contents.isFull() ? CellState.FULL : CellState.NOT_EMPTY;
            }
            return FastColor.ARGB32.opaque(state.getStateColor());
        }, NEPatternDiskItems.L4.get(), NEPatternDiskItems.L6.get(), NEPatternDiskItems.L9.get());
    }
}
