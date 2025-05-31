package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridService;

public interface IECOPatternStorageService extends IGridService {
    /**
     * 获取此网络的总 {@link IECOPatternStorage}
     */
    IECOPatternStorage getPatternStorage();
}
