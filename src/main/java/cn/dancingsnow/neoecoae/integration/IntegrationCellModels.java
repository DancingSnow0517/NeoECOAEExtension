package cn.dancingsnow.neoecoae.integration;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.client.ECOCellModels;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.InitializeClientRegistriesEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drive cell model registration for optional cell integrations. The 26.1 {@link ECOCellModels}
 * resolves holders immediately, so integrations defer it to client registry initialization.
 */
public final class IntegrationCellModels {
    private static final Map<Holder<Item>, Identifier> PENDING = new LinkedHashMap<>();
    private static boolean listening;

    private IntegrationCellModels() {
    }

    /** Registers a drive cell model once the item registry is bound (client only; no-op on servers). */
    public static synchronized void register(Holder<Item> item, Identifier model) {
        PENDING.put(item, model);
        if (!listening) {
            listening = true;
            NeoECOAE.MOD_BUS.addListener(InitializeClientRegistriesEvent.class, event -> flush());
        }
    }

    private static synchronized void flush() {
        PENDING.forEach(ECOCellModels::register);
        PENDING.clear();
    }
}
