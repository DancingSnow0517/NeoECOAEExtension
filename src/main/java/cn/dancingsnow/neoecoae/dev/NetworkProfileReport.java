package cn.dancingsnow.neoecoae.dev;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Reads immutable JFR files off the server thread, preserving full method names, including addon Mixin wrappers. */
final class NetworkProfileReport {
    static final class Tree {
        private static final int MAX_NODES = 5000;
        final Node root = new Node("Server thread");
        int nodes = 1;
        long truncatedSamples;
        boolean nodeLimitReached;
        boolean depthLimitReached;

        void add(List<String> frames, long weight, boolean truncated) {
            if (weight < 0) return;
            if (truncated) truncatedSamples++;
            Node current = root;
            current.weight += weight;
            int depth = 0;
            for (String frame : frames) {
                if (++depth > 128) {
                    depthLimitReached = true;
                    current.omittedWeight += weight;
                    return;
                }
                Node child = current.children.get(frame);
                if (child == null) {
                    if (nodes >= MAX_NODES) {
                        nodeLimitReached = true;
                        current.omittedWeight += weight;
                        return;
                    }
                    child = new Node(frame);
                    current.children.put(frame, child);
                    nodes++;
                }
                current = child;
                current.weight += weight;
            }
            current.selfWeight += weight;
        }

        Map<String, Object> report() {
            var result = new LinkedHashMap<String, Object>();
            result.put("totalWeight", root.weight);
            result.put("truncatedStackSamples", truncatedSamples);
            result.put("nodeLimitReached", nodeLimitReached);
            result.put("depthLimitReached", depthLimitReached);
            result.put("root", root.report());
            return result;
        }
    }

    private static final class Node {
        final String name;
        final Map<String, Node> children = new LinkedHashMap<>();
        long weight, selfWeight, omittedWeight;
        Node(String name) { this.name = name; }

        List<Node> sorted() {
            return children.values().stream().sorted(Comparator.comparingLong((Node node) -> node.weight)
                .reversed().thenComparing(node -> node.name)).toList();
        }

        Map<String, Object> report() {
            var result = new LinkedHashMap<String, Object>();
            result.put("method", name);
            result.put("inclusiveWeight", weight);
            result.put("selfWeight", selfWeight);
            result.put("omittedDeeperWeight", omittedWeight);
            result.put("children", sorted().stream().map(Node::report).toList());
            return result;
        }
    }

    final Tree execution = new Tree();
    final Tree allocations = new Tree();
    private long allocationSamples, gcPauses, gcPauseNanos;
    private Map<String, Object> allocationTotal = Map.of("available", false);
    private AllocationSample allocationBoundarySample;

    record AllocationCounter(Instant at, long bytes) {}
    record AllocationSample(Instant at, List<String> frames, long weight, boolean truncated) {}

    static NetworkProfileReport read(Path file, long serverThreadId, Instant start, Instant end) throws IOException {
        var report = new NetworkProfileReport();
        var counters = new ArrayList<AllocationCounter>();
        try (var recording = new RecordingFile(file)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String type = event.getEventType().getName();
                if (type.equals("jdk.ThreadAllocationStatistics")) {
                    RecordedThread thread = event.getThread("thread");
                    if (thread != null && thread.getJavaThreadId() == serverThreadId)
                        counters.add(new AllocationCounter(event.getStartTime(), event.getLong("allocated")));
                }
                if (event.getStartTime().isBefore(start) || event.getStartTime().isAfter(end)) continue;
                if (type.equals("jdk.ExecutionSample") || type.equals("jdk.NativeMethodSample")) {
                    RecordedThread thread = event.getThread("sampledThread");
                    if (thread != null && thread.getJavaThreadId() == serverThreadId && event.getStackTrace() != null)
                        report.execution.add(frames(event.getStackTrace()), 1, event.getStackTrace().isTruncated());
                } else if (type.equals("jdk.ObjectAllocationSample")) {
                    RecordedThread thread = event.getThread();
                    if (thread != null && thread.getJavaThreadId() == serverThreadId)
                        report.addAllocationSample(new AllocationSample(event.getStartTime(),
                                event.getStackTrace() == null ? List.of() : frames(event.getStackTrace()),
                                event.getLong("weight"), event.getStackTrace() != null && event.getStackTrace().isTruncated()));
                } else if (type.equals("jdk.GCPhasePause")) {
                    report.gcPauses++;
                    report.gcPauseNanos += event.getDuration().toNanos();
                }
            }
        }
        report.allocationTotal = allocationCounterDelta(counters, start, end);
        return report;
    }

    void addAllocationSamples(List<AllocationSample> samples) {
        samples.forEach(this::addAllocationSample);
    }

    private void addAllocationSample(AllocationSample sample) {
        // JFR file order is not chronological across thread buffers. A weight covers the interval
        // since the preceding sample, which is unknown at the left edge of this recording window.
        // In a fresh recording that first weight can include the thread's entire allocation history.
        allocationSamples++;
        if (allocationBoundarySample == null) {
            allocationBoundarySample = sample;
            return;
        }
        if (sample.at.isBefore(allocationBoundarySample.at)) {
            var previous = allocationBoundarySample;
            allocationBoundarySample = sample;
            sample = previous;
        }
        // Retain only the earliest sample; all other stacks go straight into the bounded tree.
        allocations.add(sample.frames, sample.weight, sample.truncated);
    }

    static Map<String, Object> allocationCounterDelta(List<AllocationCounter> counters, Instant start, Instant end) {
        var result = new LinkedHashMap<String, Object>();
        result.put("available", false);
        result.put("allocatedBytes", null);
        result.put("scope", "Difference of cumulative allocated-byte counters for this server thread, not retained "
                + "heap or the sum of sampled weights. Counter timestamps are recorded explicitly; chunk-boundary "
                + "counters can differ slightly from the requested window. Partial coverage is labeled.");
        if (!end.isAfter(start)) return result;
        Duration tolerance = Duration.ofMillis(250);
        var selected = counters.stream().filter(counter ->
                (!counter.at.isBefore(start) && !counter.at.isAfter(end))
                        || Duration.between(start, counter.at).abs().compareTo(tolerance) <= 0
                        || Duration.between(end, counter.at).abs().compareTo(tolerance) <= 0)
                .sorted(Comparator.comparing(AllocationCounter::at)).toList();
        if (selected.size() < 2) return result;
        var first = selected.getFirst();
        var last = selected.getLast();
        result.put("startedAt", first.at.toString());
        result.put("endedAt", last.at.toString());
        long previous = -1;
        for (var counter : selected) {
            if (counter.bytes < 0 || counter.bytes < previous) {
                result.put("unavailableReason", "An allocation counter is unavailable or decreased within the window.");
                return result;
            }
            previous = counter.bytes;
        }
        double seconds = Duration.between(first.at, last.at).toNanos() / 1e9;
        if (seconds <= 0) return result;
        long bytes = last.bytes - first.bytes;
        result.put("available", true);
        result.put("allocatedBytes", bytes);
        result.put("measurementSeconds", seconds);
        result.put("bytesPerWallSecond", bytes / seconds);
        result.put("startCounter", first.bytes);
        result.put("endCounter", last.bytes);
        result.put("coversRequestedWindow", Duration.between(start, first.at).abs().compareTo(tolerance) <= 0
                && Duration.between(end, last.at).abs().compareTo(tolerance) <= 0);
        return result;
    }

    private static List<String> frames(RecordedStackTrace stack) {
        var frames = new ArrayList<String>();
        for (var frame : stack.getFrames()) {
            var method = frame.getMethod();
            if (method != null) frames.add(method.getType().getName() + "." + method.getName()
                + (frame.getLineNumber() < 0 ? "" : ":" + frame.getLineNumber()));
        }
        Collections.reverse(frames);
        return frames;
    }

    Map<String, Object> sampling() {
        var result = new LinkedHashMap<String, Object>();
        result.put("serverExecutionSamples", execution.report());
        result.put("serverThreadAllocation", allocationTotal);
        result.put("serverAllocationEstimatedBytes", allocations.report());
        result.put("serverAllocationSampleCount", allocationSamples);
        result.put("serverAllocationAttributedSampleCount", Math.max(0, allocationSamples - 1));
        result.put("excludedAllocationBoundarySample", allocationBoundarySample == null ? null
                : Map.of("at", allocationBoundarySample.at.toString(), "weight", allocationBoundarySample.weight,
                        "reason", "The preceding allocation sample is outside or absent from this window. Its weight may "
                                + "contain pre-recording allocation history; it is excluded from hotspot attribution only."));
        result.put("jvmGcPauseEvents", gcPauses);
        result.put("jvmGcPauseNanos", gcPauseNanos);
        result.put("limitations", "Execution tree weights are sample counts, not measured method durations or per-operation overhead. "
            + "Allocation tree weights estimate only intervals after the first in-window sample; the boundary sample is excluded "
            + "because it can include allocation history. Use serverThreadAllocation for the separately measured total. "
            + "The tree is not exact bytes or object counts. Zero samples do not prove zero cost. "
            + "Inclusive parent and child percentages overlap. Server trees include other activity and recorder instrumentation; "
            + "GC is JVM-wide. The raw JFR includes other threads. Trees are capped at 5000 nodes; the raw JFR retains the captured stacks.");
        return result;
    }

    void write(Path json, Path html, Map<String, Object> metadata) throws IOException {
        var result = new LinkedHashMap<>(metadata);
        result.put("sampling", sampling());
        String text = new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(result);
        Files.writeString(json, text);
        StringBuilder page = new StringBuilder("""
            <!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
            <title>NeoECOAE 网络运行记录</title><style>
            body{font:14px system-ui,sans-serif;margin:28px;background:#111827;color:#e5e7eb}
            h1{font-size:23px}h2{font-size:18px;margin-top:28px}p{line-height:1.7;max-width:1000px}
            summary{cursor:pointer;font:13px ui-monospace,monospace;padding:5px;white-space:pre-wrap;overflow-wrap:anywhere}
            summary:hover{background:#263449}details details{margin-left:20px;border-left:1px solid #374151;padding-left:8px}
            .weight{color:#fbbf24}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#1f2937;padding:16px}
            button{padding:7px 12px;margin-right:8px;cursor:pointer}
            </style><h1>NeoECOAE 网络运行记录</h1>
            <p>记录的是实际游戏运行。下面只汇总服务器线程；其他线程保留在原始 JFR 文件中。
            执行树百分比表示执行样本占比，包含子调用，不能把父子相加，也不能当作某个模组额外增加的耗时。
            总分配量取服务器线程累计计数差；分配树只用于定位热点，排除可能含录制前历史的首条样本。
            没有样本不代表没有开销。计数器和采样本身也有成本。</p>
            <button onclick="document.querySelectorAll('details').forEach(x=>x.open=true)">展开调用树</button>
            <button onclick="document.querySelectorAll('details').forEach(x=>x.open=false)">折叠调用树</button>
            """);
        page.append("<h2>服务器线程总分配量（计数差）</h2>");
        if (Boolean.TRUE.equals(allocationTotal.get("available"))) {
            page.append("<p>").append(String.format(Locale.ROOT, "%,d bytes · %.3f 秒", allocationTotal.get("allocatedBytes"),
                    allocationTotal.get("measurementSeconds")));
            if (!Boolean.TRUE.equals(allocationTotal.get("coversRequestedWindow")))
                page.append(" · 计数器仅覆盖部分录制时间，不能作为整段总量");
            page.append("</p>");
        } else page.append("<p>缺少有效的起止线程分配计数，无法报告总分配量；不使用分配采样权重代替。</p>");
        page.append("<h2>采样条件</h2><p>时长为现实经过时间。世界暂停或长 tick 会减少发生器调用次数，不能把调用次数 / 秒当作最大吞吐量或 TPS。"
                + "终端打开状态应在各轮保持一致；下方记录长 tick 间隔及发起玩家的服务端菜单。</p><pre>")
                .append(escape(new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(metadata.get("runtimeConditions"))))
                .append("</pre>");
        page.append("<h2>服务器线程执行样本</h2>");
        render(page, execution.root, execution.root.weight, 0);
        page.append("<h2>服务器线程分配热点（排除首条边界样本的估算字节）</h2>");
        render(page, allocations.root, allocations.root.weight, 0);
        if (execution.nodeLimitReached || allocations.nodeLimitReached || execution.depthLimitReached || allocations.depthLimitReached)
            page.append("<p>调用树达到节点或深度上限，部分深层节点已省略；可在原始 JFR 中继续查看。</p>");
        page.append("<h2>存取统计、环境与记录边界</h2><pre>").append(escape(text)).append("</pre></html>");
        Files.writeString(html, page.toString());
    }

    private static void render(StringBuilder html, Node node, long total, int depth) {
        if (total == 0) {
            html.append("<p>没有采集到该类服务器线程样本，请延长记录或增加实际工作负载。</p>");
            return;
        }
        html.append("<details").append(depth < 2 ? " open" : "").append("><summary><span class=weight>")
            .append(String.format(Locale.ROOT, "%.2f%%", 100.0 * node.weight / total)).append(" · ")
            .append(node.weight).append("</span> ").append(escape(node.name)).append("</summary>");
        for (Node child : node.sorted()) render(html, child, total, depth + 1);
        html.append("</details>");
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
