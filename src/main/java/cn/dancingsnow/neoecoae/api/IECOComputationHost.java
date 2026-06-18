package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridNodeService;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;

public interface IECOComputationHost extends IGridNodeService {
    ECOComputationSystemBlockEntity getComputationHost();
}
