package cn.dancingsnow.neoecoae.core.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The persisted execution structure. Platform bindings own patterns and resource representations. */
public final class ExecutionPlanCodec {
    private ExecutionPlanCodec() {}

    public record Plan<P, K>(String mode, List<Task<P, K>> tasks, List<Phase<K>> phases) {}
    public record Task<P, K>(int id, P pattern, long total, int phase, String kind,
                             List<Allocation<K>> allocations) {}
    public record Allocation<K>(int slot, List<Run<K>> runs) {}
    public record Run<K>(K key, long amount, long crafts) {}
    public record Stack<K>(K key, long amount) {}
    public record Step(int task, long count, int repeatWidth, long repetitions) {}
    public record Phase<K>(int index, int component, String type, List<Integer> tasks,
                            List<Step> steps, List<Integer> dependencies, Map<Integer, Long> dynamic,
                            Map<K, Long> initialSeed) {}

    public interface Platform<P, K> {
        void writePattern(DataWriter<K> task, P pattern);
        P readPattern(DataReader<K> task);
        void writeStack(DataWriter<K> entry, K key, long amount);
        Stack<K> readStack(DataReader<K> entry);
    }

    public static <P, K> void write(DataWriter<K> data, Plan<P, K> plan, Platform<P, K> platform) {
        data.writeString("mode", plan.mode());
        writeEntries(data, "tasks", plan.tasks(), (taskTag, task) -> {
            taskTag.writeInt("id", task.id());
            taskTag.writeLong("total", task.total());
            taskTag.writeInt("phase", task.phase());
            taskTag.writeString("kind", task.kind());
            platform.writePattern(taskTag, task.pattern());
            writeEntries(taskTag, "inputAllocations", task.allocations(), (allocationTag, allocation) -> {
                allocationTag.writeInt("slot", allocation.slot());
                writeEntries(allocationTag, "runs", allocation.runs(), (runTag, run) -> {
                    platform.writeStack(runTag, run.key(), run.amount());
                    runTag.writeLong("crafts", run.crafts());
                });
            });
        });
        writeEntries(data, "phases", plan.phases(), (phaseTag, phase) -> {
            phaseTag.writeInt("index", phase.index());
            phaseTag.writeInt("component", phase.component());
            phaseTag.writeString("type", phase.type());
            phaseTag.writeIntArray("tasks", phase.tasks().stream().mapToInt(Integer::intValue).toArray());
            phaseTag.writeIntArray("dependencies", phase.dependencies().stream().mapToInt(Integer::intValue).toArray());
            writeEntries(phaseTag, "steps", phase.steps(), (stepTag, step) -> {
                stepTag.writeInt("task", step.task());
                stepTag.writeLong("count", step.count());
                if (step.repetitions() > 1L) {
                    stepTag.writeInt("repeatWidth", step.repeatWidth());
                    stepTag.writeLong("repetitions", step.repetitions());
                }
            });
            writeEntries(phaseTag, "dynamic", new ArrayList<>(phase.dynamic().entrySet()), (firingTag, firing) -> {
                firingTag.writeInt("task", firing.getKey());
                firingTag.writeLong("count", firing.getValue());
            });
            writeEntries(phaseTag, "initialSeed", new ArrayList<>(phase.initialSeed().entrySet()),
                (seedTag, seed) -> platform.writeStack(seedTag, seed.getKey(), seed.getValue()));
        });
    }

    public static <P, K> Plan<P, K> read(DataReader<K> data, Platform<P, K> platform) {
        String mode = data.readString("mode");
        List<Task<P, K>> tasks = new ArrayList<>();
        for (var taskTag : data.readCompoundList("tasks")) {
            int id = taskTag.readInt("id");
            if (id != tasks.size()) throw new IllegalArgumentException("Persisted execution task ids are not dense");
            P pattern = platform.readPattern(taskTag);
            List<Allocation<K>> allocations = new ArrayList<>();
            for (var allocationTag : taskTag.readCompoundList("inputAllocations")) {
                List<Run<K>> runs = new ArrayList<>();
                for (var runTag : allocationTag.readCompoundList("runs")) {
                    Stack<K> stack = platform.readStack(runTag);
                    if (stack == null) throw new IllegalArgumentException("Invalid persisted input allocation");
                    runs.add(new Run<>(stack.key(), stack.amount(), runTag.readLong("crafts")));
                }
                allocations.add(new Allocation<>(allocationTag.readInt("slot"), runs));
            }
            tasks.add(new Task<>(id, pattern, taskTag.readLong("total"), taskTag.readInt("phase"),
                taskTag.readString("kind"), allocations));
        }
        List<Phase<K>> phases = new ArrayList<>();
        for (var phaseTag : data.readCompoundList("phases")) {
            int index = phaseTag.readInt("index");
            if (index != phases.size()) throw new IllegalArgumentException("Persisted execution phase ids are not dense");
            List<Step> steps = new ArrayList<>();
            for (var stepTag : phaseTag.readCompoundList("steps")) {
                steps.add(new Step(stepTag.readInt("task"), stepTag.readLong("count"),
                    stepTag.contains("repeatWidth") ? stepTag.readInt("repeatWidth") : 1,
                    stepTag.contains("repetitions") ? stepTag.readLong("repetitions") : 1L));
            }
            Map<Integer, Long> dynamic = new LinkedHashMap<>();
            for (var firing : phaseTag.readCompoundList("dynamic")) {
                dynamic.put(firing.readInt("task"), firing.readLong("count"));
            }
            Map<K, Long> seed = new LinkedHashMap<>();
            for (var seedTag : phaseTag.readCompoundList("initialSeed")) {
                Stack<K> stack = platform.readStack(seedTag);
                if (stack == null || stack.amount() <= 0L) throw new IllegalArgumentException("Invalid persisted seed");
                seed.put(stack.key(), stack.amount());
            }
            phases.add(new Phase<>(index, phaseTag.readInt("component"), phaseTag.readString("type"),
                Arrays.stream(phaseTag.readIntArray("tasks")).boxed().toList(), steps,
                Arrays.stream(phaseTag.readIntArray("dependencies")).boxed().toList(), dynamic, seed));
        }
        return new Plan<>(mode, tasks, phases);
    }

    private static <K, T> void writeEntries(DataWriter<K> writer, String name, List<T> values,
                                            BiConsumer<DataWriter<K>, T> write) {
        List<Consumer<DataWriter<K>>> entries = new ArrayList<>(values.size());
        for (T value : values) entries.add(entry -> write.accept(entry, value));
        writer.writeList(name, entries);
    }
}
