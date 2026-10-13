package cn.dancingsnow.neoecoae.api.me.output;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class ECOExternalCpuOutputRouterTest {
    @Test
    void distinctClustersAreVisitedOnce() {
        var a = new Cluster(new Cpu("a1", false, 0));
        var b = new Cluster(new Cpu("b1", false, 0));
        var byHost = Map.of("x", a, "y", a, "z", b);
        var visited = new ArrayList<Cluster>();

        var result = ECOExternalCpuOutputRouter.insertIntoOwningCpu(
                List.of("x", "y", "z"),
                byHost::get,
                cluster -> {
                    visited.add(cluster);
                    return cluster.cpus;
                },
                (Cpu cpu) -> false,
                (Cpu cpu) -> 0L);

        assertTrue(result.isEmpty());
        assertEquals(2, visited.size());
        assertTrue(visited.contains(a));
        assertTrue(visited.contains(b));
    }

    @Test
    void nullClustersAreSkipped() {
        var a = new Cluster(new Cpu("a1", true, 9));
        var byHost = Map.of("a", a);
        var visited = new ArrayList<Cluster>();

        var result = ECOExternalCpuOutputRouter.insertIntoOwningCpu(
                List.of("missing", "a"),
                byHost::get,
                cluster -> {
                    visited.add(cluster);
                    return cluster.cpus;
                },
                (Cpu cpu) -> cpu.owns,
                (Cpu cpu) -> cpu.result);

        assertEquals(List.of(a), visited);
        assertEquals(OptionalLong.of(9L), result);
    }

    @Test
    void firstOwnerInCpuOrderWinsAndOnlyItIsInserted() {
        var a = new Cluster(
                new Cpu("c1", false, 0),
                new Cpu("c2", true, 5),
                new Cpu("c3", true, 7));
        var byHost = Map.of("a", a);
        var probed = new ArrayList<String>();
        var inserted = new ArrayList<String>();

        var result = ECOExternalCpuOutputRouter.insertIntoOwningCpu(
                List.of("a"),
                byHost::get,
                cluster -> cluster.cpus,
                (Cpu cpu) -> {
                    probed.add(cpu.name);
                    return cpu.owns;
                },
                (Cpu cpu) -> {
                    inserted.add(cpu.name);
                    return cpu.result;
                });

        assertEquals(OptionalLong.of(5L), result);
        assertEquals(List.of("c1", "c2"), probed);
        assertEquals(List.of("c2"), inserted);
    }

    @Test
    void ownerZeroResultIsReturnedWithoutTryingLaterCpus() {
        var a = new Cluster(new Cpu("c1", true, 0), new Cpu("c2", true, 3));
        var byHost = Map.of("a", a);
        var inserted = new ArrayList<String>();

        var result = ECOExternalCpuOutputRouter.insertIntoOwningCpu(
                List.of("a"),
                byHost::get,
                cluster -> cluster.cpus,
                (Cpu cpu) -> cpu.owns,
                (Cpu cpu) -> {
                    inserted.add(cpu.name);
                    return cpu.result;
                });

        assertTrue(result.isPresent());
        assertEquals(0L, result.getAsLong());
        assertEquals(List.of("c1"), inserted);
    }

    @Test
    void emptyWhenNoCpuOwnsTheJob() {
        var a = new Cluster(new Cpu("c1", false, 4), new Cpu("c2", false, 6));
        var byHost = Map.of("a", a);
        var inserted = new ArrayList<String>();

        var result = ECOExternalCpuOutputRouter.insertIntoOwningCpu(
                List.of("a", "missing"),
                byHost::get,
                cluster -> cluster.cpus,
                (Cpu cpu) -> cpu.owns,
                (Cpu cpu) -> {
                    inserted.add(cpu.name);
                    return cpu.result;
                });

        assertTrue(result.isEmpty());
        assertTrue(inserted.isEmpty());
    }

    private static final class Cluster {
        private final List<Cpu> cpus;

        private Cluster(Cpu... cpus) {
            this.cpus = List.of(cpus);
        }
    }

    private static final class Cpu {
        private final String name;
        private final boolean owns;
        private final long result;

        private Cpu(String name, boolean owns, long result) {
            this.name = name;
            this.owns = owns;
            this.result = result;
        }
    }
}
