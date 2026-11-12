package cn.dancingsnow.neoecoae.dev;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.blockentity.AEBaseBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.config.DevToolsConfig;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import net.minecraft.util.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Server-owned timer and counters; JFR dumping, parsing and report writing run on the IO executor. */
final class DevNetworkProfiler {
    private static final long MAX_RECORDING_BYTES = 64L * 1024 * 1024;
    private static Session session;

    private record Target(AEBaseBlockEntity host, IChestOrDrive drive, int slot, GeneratorState state, String description) {
        boolean present() {
            if (host.isRemoved()) return false;
            if (drive == null) return host instanceof DevResourceGenerator.GeneratorBlockEntity generator && generator.state == state;
            return drive.getOriginalCellInventory(slot) instanceof GeneratorGridService.GeneratorCell cell && cell.state == state;
        }
    }

    private static final class Session {
        final MinecraftServer server;
        final UUID owner;
        final Target target;
        final Recording recording;
        final NetworkTrafficRecorder traffic;
        final NetworkProfileActivity activity;
        final Object reuseGeneration = KeyReuse.epoch();
        final long startedNanos, durationNanos;
        final Instant start;
        final long threadId = Thread.currentThread().threadId();
        final String threadName = Thread.currentThread().getName();
        final int seconds;
        final Path jfr, json, html;
        final String workloadAtStart;
        final String workloadFingerprintAtStart;
        final Map<String, String> mods = new LinkedHashMap<>();
        volatile String phase = "recording";
        volatile String error;
        long stoppedNanos;

        Session(CommandSourceStack source, Target target, Recording recording, Path base, int seconds, String fingerprint) {
            this.server = source.getServer();
            this.owner = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
            this.target = target;
            this.recording = recording;
            this.seconds = seconds;
            this.jfr = Path.of(base + ".jfr");
            this.json = Path.of(base + ".json");
            this.html = Path.of(base + ".html");
            this.workloadAtStart = target.state.describe();
            this.workloadFingerprintAtStart = fingerprint;
            this.startedNanos = System.nanoTime();
            this.durationNanos = Duration.ofSeconds(seconds).toNanos();
            this.start = recording.getStartTime();
            this.traffic = new NetworkTrafficRecorder(startedNanos, durationNanos);
            this.activity = new NetworkProfileActivity(startedNanos, durationNanos, ownerMenu(server, owner));
            var list = ModList.get();
            if (list != null) list.getMods().forEach(mod -> mods.put(mod.getModId(), mod.getVersion().toString()));
        }

        String describe() {
            long elapsed = Math.min(seconds, Math.max(0, ((phase.equals("recording") ? System.nanoTime() : stoppedNanos)
                - startedNanos) / 1_000_000_000));
            return "Network profile " + phase + ": " + elapsed + "/" + seconds + " s; " + target.description
                + (error == null ? "" : "; " + error);
        }
    }

    private DevNetworkProfiler() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(DevNetworkProfiler::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            if (session != null && session.server == event.getServer()) {
                finish(session, "cancelled", "Server stopped");
                session = null;
            }
        });
    }

    static void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("start").then(argument("seconds", integer(1, 600))
            .executes(context -> start(context, null))
            .then(argument("pos", BlockPosArgument.blockPos())
                .executes(context -> start(context, BlockPosArgument.getLoadedBlockPos(context, "pos")))
                .then(argument("slot", integer(0, 63))
                    .executes(context -> start(context, BlockPosArgument.getLoadedBlockPos(context, "pos")))))));
        root.then(literal("stop").executes(context -> control(context.getSource(), true)));
        root.then(literal("status").executes(context -> control(context.getSource(), false)));
    }

    private static int start(CommandContext<CommandSourceStack> context, BlockPos pos) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        if (session != null && (session.phase.equals("recording") || session.phase.equals("writing"))) {
            source.sendFailure(Component.literal("A network profile is already recording or writing; /neodev status"));
            return 0;
        }
        if (pos == null) {
            var hit = source.getPlayerOrException().pick(6, 0, false);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
                source.sendFailure(Component.literal("Look at the ME Drive containing the dev generator, or append x y z [slot]"));
                return 0;
            }
            pos = block.getBlockPos();
        }
        Recording recording = null;
        try {
            Integer slot;
            try { slot = getInteger(context, "slot"); } catch (IllegalArgumentException absent) { slot = null; }
            Target target = target(source, pos, slot);
            if (!FlightRecorder.isAvailable()) throw new IllegalStateException("JFR is unavailable in this Java runtime");
            int seconds = getInteger(context, "seconds");
            // Serialize the ordered resource palette before recording; this is never done per tick.
            String fingerprint = workloadFingerprint(target.state.write(source.registryAccess()));
            Path directory = source.getServer().getWorldPath(LevelResource.ROOT).resolve("neoecoae-dev-reports");
            Files.createDirectories(directory);
            Path base = directory.resolve("network-" + UUID.randomUUID());
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.setName("NeoECOAE network profile");
            recording.setToDisk(true);
            recording.setMaxSize(MAX_RECORDING_BYTES);
            recording.setDuration(Duration.ofSeconds(seconds));
            recording.setDestination(Path.of(base + ".jfr"));
            recording.setDumpOnExit(true);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(10));
            recording.enable("jdk.ObjectAllocationSample").withStackTrace().with("throttle", "150/s");
            recording.enable("jdk.ThreadAllocationStatistics").with("period", "everyChunk");
            recording.start();
            Session current = new Session(source, target, recording, base, seconds, fingerprint);
            session = current;
            target.state.recording = current.traffic;
            source.sendSuccess(() -> Component.literal("Recording real network activity for " + seconds + " seconds; "
                + target.description + ". Auto-stops recording and saves HTML/JSON/JFR; workloads keep running. "
                + "Keep the world unpaused and use the same terminal state for each comparison. "
                + "Output: " + current.html.toAbsolutePath()), false);
            return 1;
        } catch (Exception | LinkageError problem) {
            if (recording != null) recording.close();
            source.sendFailure(Component.literal("Could not start network profile: " + problem));
            return 0;
        }
    }

    private static Target target(CommandSourceStack source, BlockPos pos, Integer slot) {
        var level = source.getLevel();
        if (!level.hasChunkAt(pos)) throw new IllegalArgumentException("Target chunk is not loaded");
        var entity = level.getBlockEntity(pos);
        String location = level.dimension().identifier() + " " + pos.toShortString();
        if (entity instanceof DevResourceGenerator.GeneratorBlockEntity generator) {
            if (slot != null) throw new IllegalArgumentException("A placed generator has no cell slot");
            return new Target(generator, null, -1, generator.state, location);
        }
        if (!(entity instanceof IChestOrDrive drive) || !(entity instanceof AEBaseBlockEntity host))
            throw new IllegalArgumentException("Target must be an ME Drive containing a dev generator, or a placed dev generator");
        int selected = selectSlot(drive, slot);
        var cell = (GeneratorGridService.GeneratorCell) drive.getOriginalCellInventory(selected);
        if (cell.state == null) cell.state = GeneratorState.read(DevResourceGenerator.readItem(cell.stack), source.registryAccess());
        return new Target(host, drive, selected, cell.state, location + " slot=" + selected);
    }

    static int selectSlot(IChestOrDrive drive, Integer slot) {
        if (slot != null) {
            if (slot < 0 || slot >= drive.getCellCount() || !(drive.getOriginalCellInventory(slot) instanceof GeneratorGridService.GeneratorCell))
                throw new IllegalArgumentException("No dev generator in slot " + slot + " (slots start at 0)");
            return slot;
        }
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < drive.getCellCount(); i++)
            if (drive.getOriginalCellInventory(i) instanceof GeneratorGridService.GeneratorCell) slots.add(i);
        if (slots.size() != 1) throw new IllegalArgumentException(slots.isEmpty() ? "No dev generator in this drive"
            : "Multiple dev generators in slots " + slots + "; append x y z slot");
        return slots.getFirst();
    }

    private static int control(CommandSourceStack source, boolean stop) {
        if (session == null || session.server != source.getServer()) {
            source.sendFailure(Component.literal("Look at the drive and use /neodev start <seconds> first"));
            return 0;
        }
        Session current = session;
        if (stop) finish(current, "cancelled", "Stopped by command");
        source.sendSuccess(() -> Component.literal(current.describe() + "; report: " + current.html.toAbsolutePath()), false);
        return 1;
    }

    private static void tick(ServerTickEvent.Post event) {
        Session current = session;
        if (current == null || current.server != event.getServer() || !current.phase.equals("recording")) return;
        current.activity.tick(System.nanoTime(), ownerMenu(current.server, current.owner));
        if (!DevToolsConfig.enabled()) finish(current, "cancelled", "Development tools disabled");
        else if (!current.target.present()) finish(current, "cancelled", "Generator removed or unloaded");
        else if (current.owner != null && current.server.getPlayerList().getPlayer(current.owner) == null)
            finish(current, "cancelled", "Player disconnected");
        else if (System.nanoTime() - current.startedNanos >= current.durationNanos || current.recording.getState() != RecordingState.RUNNING)
            finish(current, "completed", "Recording duration reached");
    }

    private static void finish(Session current, String outcome, String reason) {
        if (!current.phase.equals("recording")) return;
        current.stoppedNanos = System.nanoTime();
        current.phase = "writing";
        current.traffic.close();
        if (current.target.state.recording == current.traffic) current.target.state.recording = null;
        Instant now = Instant.now();
        Instant deadline = current.start.plusSeconds(current.seconds);
        Instant end = now.isBefore(deadline) ? now : deadline;
        var data = new LinkedHashMap<String, Object>();
        data.put("profileFormatVersion", 2);
        data.put("outcome", outcome);
        data.put("reason", reason);
        data.put("requestedSeconds", current.seconds);
        data.put("startedAt", current.start.toString());
        data.put("endedAt", end.toString());
        data.put("elapsedSeconds", Duration.between(current.start, end).toNanos() / 1_000_000_000.0);
        data.put("serverThreadId", current.threadId);
        data.put("serverThreadName", current.threadName);
        data.put("target", current.target.description);
        data.put("workloadAtStart", current.workloadAtStart);
        data.put("workloadAtEnd", current.target.state.describe());
        data.put("workloadFingerprintAtStart", current.workloadFingerprintAtStart);
        try {
            String fingerprint = workloadFingerprint(current.target.state.write(current.server.registryAccess()));
            data.put("workloadFingerprintAtEnd", fingerprint);
            data.put("workloadFingerprintChanged", !current.workloadFingerprintAtStart.equals(fingerprint));
        } catch (RuntimeException | LinkageError unavailable) {
            data.put("workloadFingerprintAtEnd", null);
            data.put("workloadFingerprintChanged", null);
            data.put("workloadFingerprintError", unavailable.toString());
        }
        data.put("workloadFingerprintScope", "SHA-256 of ordered resource keys and generator settings, including FE "
                + "configuration when present. Matching hashes do not assert matching random amount sequences or network contents.");
        data.put("runtimeConditions", current.activity.snapshot(current.stoppedNanos));
        data.put("keyReuseEnabledAtStart", current.reuseGeneration != null);
        data.put("keyReuseEnabledAtEnd", KeyReuse.epoch() != null);
        data.put("keyReuseGenerationChanged", current.reuseGeneration != KeyReuse.epoch());
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("modVersions", Map.copyOf(current.mods));
        data.put("generatorTraffic", current.traffic.snapshot());
        data.put("rawJfr", current.jfr.toAbsolutePath().toString());
        data.put("jfrMaxRetainedBytes", MAX_RECORDING_BYTES);
        data.put("scope", "Real server activity; addon calls appear when sampled. Only generatorTraffic is restricted to the selected device. "
            + "No global optimization setting is changed. The JFR is capped at 64 MiB and may retain only the latest chunks. "
            + "Parsing/dumping runs after collection on the IO executor. Compare repeat runs with the same world, workload and duration; "
            + "a single profile is not an optimization on/off comparison.");
        CompletableFuture.runAsync(() -> {
            try (var recording = current.recording) {
                finishRecording(recording, current.jfr);
                NetworkProfileReport.read(current.jfr, current.threadId, current.start, end).write(current.json, current.html, data);
                current.phase = outcome;
            } catch (Exception | LinkageError problem) {
                current.error = problem.toString();
                current.phase = "failed";
                data.put("exportError", problem.toString());
                try { Files.writeString(current.json, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(data)); }
                catch (Exception writing) { problem.addSuppressed(writing); }
                LoggerFactory.getLogger(DevNetworkProfiler.class).warn("Could not export network profile", problem);
            }
            if (current.server.isRunning()) current.server.execute(() -> {
                var player = current.owner == null ? null : current.server.getPlayerList().getPlayer(current.owner);
                if (player != null) player.sendSystemMessage(Component.literal(current.describe() + "; report: " + current.html.toAbsolutePath()));
            });
        }, Util.ioPool());
    }

    static void finishRecording(Recording recording, Path file) throws IOException {
        if (recording.getState() == RecordingState.RUNNING) {
            try { recording.stop(); }
            catch (IllegalStateException race) {
                if (recording.getState() == RecordingState.RUNNING) throw race;
            }
        }
        // With a destination, JFR writes and closes automatically on stop (including its timer).
        try {
            if (recording.getState() != RecordingState.CLOSED) recording.dump(file);
        } catch (IOException race) {
            if (recording.getState() != RecordingState.CLOSED || !Files.isRegularFile(file) || Files.size(file) == 0) throw race;
        }
        if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IOException("JFR recording produced no file: " + file);
    }

    private static String ownerMenu(MinecraftServer server, UUID owner) {
        if (owner == null) return "no_player_command_source";
        var player = server.getPlayerList().getPlayer(owner);
        return player == null ? "player_disconnected" : player.containerMenu.getClass().getName();
    }

    static String workloadFingerprint(CompoundTag configuration) {
        try {
            // SNBT's StringTagVisitor sorts compound keys while preserving list order.
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(configuration.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
