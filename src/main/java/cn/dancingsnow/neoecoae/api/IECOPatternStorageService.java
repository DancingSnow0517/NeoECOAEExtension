package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridService;

public interface IECOPatternStorageService extends IGridService {
    /**
     * Returns the ECO pattern storage attached to this grid.
     */
    IECOPatternStorage getPatternStorage();
}
