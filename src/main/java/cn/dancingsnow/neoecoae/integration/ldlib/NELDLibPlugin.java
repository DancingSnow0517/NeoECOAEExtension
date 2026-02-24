package cn.dancingsnow.neoecoae.integration.ldlib;

import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.lowdragmc.lowdraglib2.plugin.ILDLibPlugin;
import com.lowdragmc.lowdraglib2.plugin.LDLibPlugin;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.RegistryAccessor;

@LDLibPlugin
public class NELDLibPlugin implements ILDLibPlugin {
    @Override
    public void onLoad() {
        AccessorRegistries.setPriority(100);

        AccessorRegistries.registerAccessor(RegistryAccessor.of(ECOCellType.class, NERegistries.CELL_TYPE));
        AccessorRegistries.registerAccessor(RegistryAccessor.of(IECOTier.class, NERegistries.ECO_TIER));
    }
}
