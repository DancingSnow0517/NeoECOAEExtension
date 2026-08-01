package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

/** The execution stage at which a FastPath attempt was rejected or failed. */
public enum ECOFastPathStage {
    ELIGIBILITY,
    CACHE_LOOKUP,
    CACHE_VERIFY,
    RESOURCE_LIMIT,
    INPUT_RESERVATION,
    PROVIDER_DISPATCH,
    WORKER_ACCEPT,
    ENERGY_CHARGE,
    ACCOUNTING,
    SLOW_EXECUTION
}
