package cn.dancingsnow.neoecoae.crafting.display.terminal;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;

/** Keeps AE2's package-private variant counters opaque to the snapshot implementation. */
public interface KeyCounterSnapshotAccess {
    Reference2ObjectMap<Object, ?> neoecoae$primaryMaps();
    void neoecoae$primaryMaps(Reference2ObjectMap<Object, ?> maps);
}
