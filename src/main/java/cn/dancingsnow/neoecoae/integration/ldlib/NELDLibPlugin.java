package cn.dancingsnow.neoecoae.integration.ldlib;

import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.lowdragmc.lowdraglib.plugin.ILDLibPlugin;
import com.lowdragmc.lowdraglib.plugin.LDLibPlugin;
import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import cn.dancingsnow.neoecoae.platform.sync.NERegistryAccessor;

@LDLibPlugin
public class NELDLibPlugin implements ILDLibPlugin {
    @Override
    public void onLoad() {
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new,
            new NERegistryAccessor<>(ECOCellType.class, () -> NERegistries.CELL_TYPE), 100);
        TypedPayloadRegistries.register(NbtTagPayload.class, NbtTagPayload::new,
            new NERegistryAccessor<>(IECOTier.class, () -> NERegistries.ECO_TIER), 100);
    }
}
