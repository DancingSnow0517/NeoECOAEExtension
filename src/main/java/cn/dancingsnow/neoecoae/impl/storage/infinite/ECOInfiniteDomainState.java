package cn.dancingsnow.neoecoae.impl.storage.infinite;

/** The mountable state of an infinite-storage domain. */
public enum ECOInfiniteDomainState {
    LOADING,
    MIGRATING_V1,
    READY,
    QUARANTINED,
    CLOSED
}
