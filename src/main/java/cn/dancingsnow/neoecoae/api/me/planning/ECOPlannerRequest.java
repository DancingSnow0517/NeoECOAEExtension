package cn.dancingsnow.neoecoae.api.me.planning;

import appeng.api.networking.crafting.ICraftingSimulationRequester;

/** Marker carried by a requester that explicitly opts one AE2 calculation into ECO planning. */
public interface ECOPlannerRequest extends ICraftingSimulationRequester {
    ECOPlannerOptions neoecoae$getPlannerOptions();
}
