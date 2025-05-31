package cn.dancingsnow.neoecoae.all;

import appeng.api.networking.GridServices;
import cn.dancingsnow.neoecoae.api.IECOPatternStorageService;
import cn.dancingsnow.neoecoae.grid.PatternStorage;

public class NEGridServices {
    public static void register() {
        GridServices.register(IECOPatternStorageService.class, PatternStorage.class);
    }
}
