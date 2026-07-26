package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Log records and snapshots both carry a revision, and recovery uses the pair to decide whether a logged delta is
 * already contained in the snapshot next to it. Getting that wrong duplicates or loses items, so the rule is pinned
 * down here on a model that mirrors the bookkeeping in {@code FileBackedInfiniteStorageEngine} and
 * {@code FileBackedECOStorageBackend} without needing the Minecraft registry to build an {@code AEKey}.
 */
class ECOStorageRevisionTest {
    @Test
    void aRecordIsNotReplayedIntoTheSnapshotThatAlreadyContainsIt() {
        Model model = new Model();
        model.mutate("diamond", 0, 10);
        model.drain();
        model.checkpoint(0);

        assertEquals(Map.of("diamond", 10L), model.recover());
    }

    @Test
    void aRecordIsReplayedWhenItsShardNeverReachedTheDisk() {
        Model model = new Model();
        model.mutate("diamond", 0, 10);
        model.mutate("emerald", 1, 20);
        model.drain();
        model.checkpoint(0);
        // The crash lands here: shard 1 is still only in the log.

        assertEquals(Map.of("diamond", 10L, "emerald", 20L), model.recover());
    }

    @Test
    void stampingTheTickRevisionDuplicatesTheCheckpointedShard() {
        Model model = new Model();
        model.mutate("diamond", 0, 10);
        model.mutate("emerald", 1, 20);
        model.drainWithTickRevision();
        model.checkpoint(0);

        // Both records claim revision 2 while shard 0's snapshot claims 1, so the diamonds are counted twice: once
        // from the snapshot and once again from the log. This is the defect the per-mutation revision fixes.
        assertEquals(20L, model.recover().get("diamond"));
    }

    @Test
    void mergedDeltasCarryTheNewestRevisionTheyContain() {
        Model model = new Model();
        model.mutate("diamond", 0, 10);
        model.mutate("emerald", 1, 5);
        model.mutate("diamond", 0, 7);
        model.drain();
        model.checkpoint(1);

        assertEquals(Map.of("diamond", 17L, "emerald", 5L), model.recover());
    }

    @Test
    void aSnapshotTakenMidMergeStraddlesTheRecord() {
        Model straddled = new Model();
        straddled.mutate("diamond", 0, 10);
        straddled.mutate("ruby", 0, 1);
        straddled.checkpoint(0); // No drain first - exactly what the engines must never do.
        straddled.mutate("diamond", 0, 7);
        straddled.drain();

        // The record folds +10 and +7 into one delta stamped with the newer revision, but the snapshot already holds
        // the +10 and recovery has no way to apply only the +7.
        assertEquals(27L, straddled.recover().get("diamond"));

        Model drained = new Model();
        drained.mutate("diamond", 0, 10);
        drained.mutate("ruby", 0, 1);
        drained.drain(); // Draining first keeps every record wholly on one side of the snapshot.
        drained.checkpoint(0);
        drained.mutate("diamond", 0, 7);
        drained.drain();

        assertEquals(17L, drained.recover().get("diamond"));
    }

    /** The revision bookkeeping of a sharded engine, stripped of everything that needs a running game. */
    private static final class Model {
        private final Map<String, Long> amounts = new LinkedHashMap<>();
        private final Map<String, Integer> shards = new LinkedHashMap<>();
        private final Map<String, Pending> pending = new LinkedHashMap<>();
        private final Map<Integer, Long> shardMutationRevisions = new LinkedHashMap<>();
        private final Map<Integer, Snapshot> snapshots = new LinkedHashMap<>();
        private final List<Record> log = new ArrayList<>();
        private long revision;

        void mutate(String key, int shard, long delta) {
            shards.put(key, shard);
            amounts.merge(key, delta, Long::sum);
            revision++;
            pending.merge(key, new Pending(delta, revision), Pending::mergedWith);
            shardMutationRevisions.put(shard, revision);
        }

        /** What the engines do now: every record carries the revision of the newest mutation folded into it. */
        void drain() {
            drain(true);
        }

        /** What they used to do: every record carries the engine's revision at drain time. */
        void drainWithTickRevision() {
            drain(false);
        }

        private void drain(boolean perMutationRevision) {
            for (Map.Entry<String, Pending> entry : pending.entrySet()) {
                Pending value = entry.getValue();
                log.add(new Record(entry.getKey(), value.delta(), perMutationRevision ? value.revision() : revision));
            }
            pending.clear();
        }

        /** A snapshot always holds every mutation applied so far and claims that shard's newest mutation revision. */
        void checkpoint(int shard) {
            Map<String, Long> contents = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : amounts.entrySet()) {
                if (shards.get(entry.getKey()) == shard) {
                    contents.put(entry.getKey(), entry.getValue());
                }
            }
            snapshots.put(shard, new Snapshot(shardMutationRevisions.getOrDefault(shard, 0L), contents));
        }

        /** Restart: read every snapshot that landed, then replay the log on top of it. */
        Map<String, Long> recover() {
            Map<String, Long> recovered = new LinkedHashMap<>();
            Map<String, Long> loadedKeyRevisions = new LinkedHashMap<>();
            for (Snapshot snapshot : snapshots.values()) {
                for (Map.Entry<String, Long> entry : snapshot.contents().entrySet()) {
                    recovered.put(entry.getKey(), entry.getValue());
                    loadedKeyRevisions.put(entry.getKey(), snapshot.revision());
                }
            }
            for (Record record : log) {
                if (record.revision() > loadedKeyRevisions.getOrDefault(record.key(), 0L)) {
                    recovered.merge(record.key(), record.delta(), Long::sum);
                }
            }
            return recovered;
        }
    }

    private record Pending(long delta, long revision) {
        Pending mergedWith(Pending newer) {
            return new Pending(delta + newer.delta, Math.max(revision, newer.revision));
        }
    }

    private record Record(String key, long delta, long revision) {}

    private record Snapshot(long revision, Map<String, Long> contents) {}
}
