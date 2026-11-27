package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;

/** Internal filter for known AE2 observers; unknown addon observers keep receiving all changes. */
public interface StorageWatcherScope {
    boolean neoecoae$observes(AEKey changed);

    static boolean samePrimary(AEKey filter, AEKey changed) {
        // KeyCounter indexes primary resources by identity, including fuzzy variant groups.
        return filter == null || changed == null || filter.getPrimaryKey() == changed.getPrimaryKey();
    }
}
