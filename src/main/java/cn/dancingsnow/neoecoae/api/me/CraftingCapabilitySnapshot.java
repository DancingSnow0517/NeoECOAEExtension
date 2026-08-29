package cn.dancingsnow.neoecoae.api.me;

/** Immutable crafting capability DTO shared by topology and tests. */
public record CraftingCapabilitySnapshot(int physicalFxCount, int activeFxCount, int normalSwitchHosts,
        int highEnergySwitchHosts, int networkMultiplier, Capacity batchPerFx, Capacity totalBatchCapacity,
        long ftParallelCapacity, int runningBatchCount, int theoreticalOverclock, int effectiveOverclock,
        boolean virtualEligible, boolean virtualMode, long energyUsage, CoolantState coolantState) {
    public static final long BASE_BATCH_PER_FX = 32L;
    public static final long F9_OVERCLOCKED_BATCH_PER_FX = 512L;
    public static final int MAX_OVERCLOCK = 9;
    public record Capacity(boolean unlimited, long finiteValue) { public static Capacity finite(long v){return new Capacity(false,Math.max(0,v));} public static Capacity unlimitedCapacity(){return new Capacity(true,0);} }
    public record CoolantState(boolean activeCooling,long amount,long capacity,int maxSupportedOverclock) {}
    public record VirtualHost(boolean f9, boolean highEnergySwitch, int actualFxCount, int requiredFxCount) {}
    public record Input(int physicalFxCount,int activeFxCount,int normalSwitchHosts,int highEnergySwitchHosts,long standaloneOverclockedBatchPerFx,long ftParallelCapacity,int runningBatchCount,boolean overclocked,boolean activeCooling,int overclockPowerMultiplier,boolean virtualTopologyEligible,CoolantState coolantState) {}
    public static boolean isVirtualTopologyEligible(java.util.List<VirtualHost> hosts) {
        if (hosts == null || hosts.size() != 8) return false;
        return hosts.stream().allMatch(h -> h != null && h.f9() && h.highEnergySwitch() && h.actualFxCount() == h.requiredFxCount());
    }
    public static CraftingCapabilitySnapshot calculate(Input i) {
        long mult=Math.max(0L, i.normalSwitchHosts()*2L+i.highEnergySwitchHosts()*8L); boolean virt=i.virtualTopologyEligible();
        long per=virt?0:(i.overclocked()?Math.max(BASE_BATCH_PER_FX,i.standaloneOverclockedBatchPerFx()):BASE_BATCH_PER_FX);
        return new CraftingCapabilitySnapshot(Math.max(0,i.physicalFxCount()),Math.max(0,i.activeFxCount()),Math.max(0,i.normalSwitchHosts()),Math.max(0,i.highEnergySwitchHosts()),(int)Math.min(Integer.MAX_VALUE,mult),virt?Capacity.unlimitedCapacity():Capacity.finite(per),virt?Capacity.unlimitedCapacity():Capacity.finite(per*Math.max(0,i.physicalFxCount())),Math.max(0,i.ftParallelCapacity()),Math.max(0,i.runningBatchCount()),0,0,virt,virt,0,i.coolantState());
    }
}
