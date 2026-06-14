package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridService;

public interface IECOPatternStorageService extends IGridService {
    /**
     * 鑾峰彇姝ょ綉缁滅殑鎬?{@link IECOPatternStorage}
     */
    IECOPatternStorage getPatternStorage();
}
