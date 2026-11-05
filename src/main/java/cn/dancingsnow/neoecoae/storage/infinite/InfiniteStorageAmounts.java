package cn.dancingsnow.neoecoae.storage.infinite;

import cn.dancingsnow.neoecoae.storage.persistence.StorageAmounts;

/** Compatibility name for the shared server-thread quantity table. */
final class InfiniteStorageAmounts extends StorageAmounts {
    InfiniteStorageAmounts() { enableChangeLog(CHANGE_LOG_CAPACITY); }
}
