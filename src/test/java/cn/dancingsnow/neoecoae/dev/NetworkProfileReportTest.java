package cn.dancingsnow.neoecoae.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class NetworkProfileReportTest {
    @TempDir Path directory;

    @Test void inclusiveWeightsDoNotDoubleCountTheRootAndMixinMethodNamesArePreserved() {
        var report = new NetworkProfileReport();
        report.execution.add(List.of("server.tick", "appeng.NetworkStorage.insert", "ae2lt$refundIntoSafeStorage"), 3, false);
        report.execution.add(List.of("server.tick", "appeng.NetworkStorage.insert", "disk.insert"), 2, true);
        var tree = report.execution.report();
        assertEquals(5L, tree.get("totalWeight"));
        assertEquals(1L, tree.get("truncatedStackSamples"));
        String json = new com.google.gson.Gson().toJson(tree);
        assertTrue(json.contains("ae2lt$refundIntoSafeStorage"));
        assertTrue(json.contains("\"inclusiveWeight\":5"));
    }

    @Test void veryDeepOrBroadStacksAreBoundedWithoutLosingTotalWeight() {
        var tree = new NetworkProfileReport.Tree();
        tree.add(IntStream.range(0, 10000).mapToObj(i -> "frame" + i).toList(), 2, false);
        assertEquals(true, tree.report().get("depthLimitReached"));
        for (int i = 0; i < 6000; i++) tree.add(List.of("root" + i), 1, false);
        assertEquals(true, tree.report().get("nodeLimitReached"));
        assertEquals(6002L, tree.report().get("totalWeight"));
    }

    @Test void htmlEscapesMethodNamesAndMetadataAndLabelsEmptySamplesHonestly() throws Exception {
        var report = new NetworkProfileReport();
        report.execution.add(List.of("addon.<method>&\""), 1, false);
        Path json = directory.resolve("profile.json");
        Path html = directory.resolve("profile.html");
        report.write(json, html, Map.of("target", "<script>alert('bad')</script>"));
        String page = Files.readString(html);
        assertFalse(page.contains("<script>"));
        assertTrue(page.contains("addon.&lt;method&gt;&amp;&quot;"));
        assertTrue(page.contains("没有采集到"));
        assertTrue(page.contains("缺少有效的起止线程分配计数"));
        assertTrue(page.contains("不能把调用次数 / 秒当作最大吞吐量或 TPS"));
        assertTrue(Files.readString(json).contains("serverExecutionSamples"));
    }

    @Test void historicalBoundaryWeightDoesNotDominateAllocationHotspots() {
        var start = Instant.parse("2026-11-05T06:40:30Z");
        var report = new NetworkProfileReport();
        report.addAllocationSamples(List.of(
                new NetworkProfileReport.AllocationSample(start.plusSeconds(2), List.of("server", "actual.second"), 64, true),
                new NetworkProfileReport.AllocationSample(start, List.of("server", "historical"), 31_437_552_112L, false),
                new NetworkProfileReport.AllocationSample(start.plusSeconds(1), List.of("server", "actual.first"), 32, false)));
        var sampling = report.sampling();
        assertEquals(96L, report.allocations.report().get("totalWeight"));
        assertEquals(3L, sampling.get("serverAllocationSampleCount"));
        assertEquals(2L, sampling.get("serverAllocationAttributedSampleCount"));
        assertEquals(1L, report.allocations.report().get("truncatedStackSamples"));
        assertFalse(new com.google.gson.Gson().toJson(report.allocations.report()).contains("historical"));
        assertEquals(31_437_552_112L, ((Map<?, ?>) sampling.get("excludedAllocationBoundarySample")).get("weight"));
    }

    @Test void evenARealFirstSampleHasUnknownBoundaryCoverageAndIsNotUsedAsTotal() {
        var report = new NetworkProfileReport();
        report.addAllocationSamples(List.of(new NetworkProfileReport.AllocationSample(Instant.EPOCH, List.of("first"), 32, false)));
        assertEquals(0L, report.allocations.report().get("totalWeight"));
        assertEquals(false, ((Map<?, ?>) report.sampling().get("serverThreadAllocation")).get("available"));
        assertEquals(1L, report.sampling().get("serverAllocationSampleCount"));
    }

    @Test void cumulativeAllocationCountersGiveWindowDeltaWithExplicitBoundaryTimes() {
        var start = Instant.parse("2026-11-05T06:40:30Z");
        var end = start.plusSeconds(30);
        var result = NetworkProfileReport.allocationCounterDelta(List.of(
                new NetworkProfileReport.AllocationCounter(end.plusMillis(100), 32_695_491_440L),
                new NetworkProfileReport.AllocationCounter(start.minusSeconds(300), 1L),
                new NetworkProfileReport.AllocationCounter(start.plusMillis(40), 31_437_270_536L)), start, end);
        assertEquals(true, result.get("available"));
        assertEquals(1_258_220_904L, result.get("allocatedBytes"));
        assertEquals(30.06, (double) result.get("measurementSeconds"), .000001);
        assertEquals(start.plusMillis(40).toString(), result.get("startedAt"));
        assertEquals(end.plusMillis(100).toString(), result.get("endedAt"));
        assertEquals(true, result.get("coversRequestedWindow"));
    }

    @Test void missingPartialOrResetCountersDoNotPretendToCoverTheWholeWindow() {
        var start = Instant.EPOCH;
        var end = start.plusSeconds(30);
        var first = new NetworkProfileReport.AllocationCounter(start.plusSeconds(10), 100);
        var last = new NetworkProfileReport.AllocationCounter(start.plusSeconds(20), 200);
        assertEquals(false, NetworkProfileReport.allocationCounterDelta(List.of(first), start, end).get("available"));
        var partial = NetworkProfileReport.allocationCounterDelta(List.of(first, last), start, end);
        assertEquals(100L, partial.get("allocatedBytes"));
        assertEquals(false, partial.get("coversRequestedWindow"));
        var reset = new NetworkProfileReport.AllocationCounter(start.plusSeconds(15), 99);
        assertEquals(false, NetworkProfileReport.allocationCounterDelta(List.of(first, reset, last), start, end).get("available"));
        assertEquals(false, NetworkProfileReport.allocationCounterDelta(List.of(first, last), end, start).get("available"));
    }

    @Test void realJfrCounterEventsAreParsedSeparatelyFromAllocationWeights() throws Exception {
        Path jfr = directory.resolve("allocations.jfr");
        try (var recording = new Recording()) {
            recording.enable("jdk.ThreadAllocationStatistics").with("period", "everyChunk");
            recording.setDestination(jfr);
            recording.start();
            var retained = new ArrayList<byte[]>();
            for (int i = 0; i < 32; i++) retained.add(new byte[64 * 1024]);
            DevNetworkProfiler.finishRecording(recording, jfr);
            assertEquals(32, retained.size());
        }
        var counters = new ArrayList<NetworkProfileReport.AllocationCounter>();
        try (var file = new jdk.jfr.consumer.RecordingFile(jfr)) {
            while (file.hasMoreEvents()) {
                var event = file.readEvent();
                if (event.getEventType().getName().equals("jdk.ThreadAllocationStatistics")) {
                    var thread = event.getThread("thread");
                    if (thread != null && thread.getJavaThreadId() == Thread.currentThread().threadId())
                        counters.add(new NetworkProfileReport.AllocationCounter(event.getStartTime(), event.getLong("allocated")));
                }
            }
        }
        assertTrue(counters.size() >= 2);
        counters.sort(java.util.Comparator.comparing(NetworkProfileReport.AllocationCounter::at));
        var first = counters.getFirst();
        var last = counters.getLast();
        var report = NetworkProfileReport.read(jfr, Thread.currentThread().threadId(), first.at(), last.at());
        var allocation = (Map<?, ?>) report.sampling().get("serverThreadAllocation");
        assertEquals(last.bytes() - first.bytes(), allocation.get("allocatedBytes"));
        assertTrue((long) allocation.get("allocatedBytes") >= 2 * 1024 * 1024);
        assertEquals(0L, report.allocations.report().get("totalWeight"), "no sampled weights were enabled");
    }

    @Test void realJfrAutomaticDestinationIsPreservedAndCanBeParsed() throws Exception {
        Path jfr = directory.resolve("network.jfr");
        try (var recording = new Recording(Configuration.getConfiguration("profile"))) {
            recording.setDestination(jfr);
            recording.enable(Marker.class);
            recording.start();
            new Marker().commit();
            DevNetworkProfiler.finishRecording(recording, jfr);
            DevNetworkProfiler.finishRecording(recording, jfr); // Automatic completion/manual stop may both observe a closed recorder.
        }
        assertTrue(Files.size(jfr) > 0);
        var report = NetworkProfileReport.read(jfr, Thread.currentThread().threadId(), Instant.EPOCH, Instant.EPOCH);
        assertEquals(0L, report.execution.report().get("totalWeight"));
        assertEquals(0L, report.allocations.report().get("totalWeight"));
        var otherThread = NetworkProfileReport.read(jfr, -1, Instant.MIN, Instant.MAX);
        assertEquals(0L, otherThread.execution.report().get("totalWeight"));
        assertEquals(0L, otherThread.allocations.report().get("totalWeight"));
    }

    @Test void timerClosesAndSavesTheRecordingWithoutWaitingForAnotherGameTick() throws Exception {
        Path jfr = directory.resolve("timed.jfr");
        try (var recording = new Recording()) {
            var stopped = new CountDownLatch(1);
            FlightRecorderListener listener = new FlightRecorderListener() {
                @Override public void recordingStateChanged(Recording changed) {
                    if (changed == recording && changed.getState() == RecordingState.CLOSED) stopped.countDown();
                }
            };
            FlightRecorder.addListener(listener);
            try {
                recording.setDestination(jfr);
                recording.setDuration(Duration.ofMillis(100));
                recording.enable(Marker.class);
                recording.start();
                new Marker().commit();
                assertTrue(stopped.await(10, TimeUnit.SECONDS));
                DevNetworkProfiler.finishRecording(recording, jfr);
                assertTrue(Files.size(jfr) > 0);
            } finally { FlightRecorder.removeListener(listener); }
        }
    }

    static class Marker extends Event {}
}
