package cn.dancingsnow.neoecoae.all;

import appeng.api.networking.GridServices;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.grid.PatternCatalog;
import cn.dancingsnow.neoecoae.integration.biggerae2.BiggerAE2SingularityPowerService;

public class NEGridServices {
    public static void register() {
        GridServices.register(IECOPatternStorageService.class, PatternCatalog.class);
        GridServices.register(BiggerAE2SingularityPowerService.class, BiggerAE2SingularityPowerService.class);
    }
}
