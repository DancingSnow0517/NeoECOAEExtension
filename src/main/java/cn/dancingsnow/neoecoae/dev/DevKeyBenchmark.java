package cn.dancingsnow.neoecoae.dev;

import cn.dancingsnow.neoecoae.config.DevToolsConfig;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** One server-thread session at a time; reports are local to the test world. */
final class DevKeyBenchmark {
    private static Session session;

    private static final class Session {
        final MinecraftServer server;
        final UUID owner;
        final KeyFactoryBenchmark benchmark;
        Path report;
        boolean notified;

        Session(MinecraftServer server, UUID owner, KeyFactoryBenchmark benchmark) {
            this.server = server;
            this.owner = owner;
            this.benchmark = benchmark;
        }

        Path save() throws IOException {
            Path directory = server.getWorldPath(LevelResource.ROOT).resolve("neoecoae-dev-reports");
            Files.createDirectories(directory);
            Path file = directory.resolve("keys-" + UUID.randomUUID() + ".json");
            Files.writeString(file, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(benchmark.report()),
                StandardOpenOption.CREATE_NEW);
            report = file;
            return file;
        }
    }

    private DevKeyBenchmark() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(DevKeyBenchmark::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            if (session != null && session.server == event.getServer()) {
                session.benchmark.stop("cancelled", "Server stopped");
                finish(session);
                session = null;
            }
        });
    }

    static void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        var start = literal("keybench").executes(context -> start(context.getSource(), "both",
            KeyFactoryBenchmark.DEFAULT_CALLS, KeyFactoryBenchmark.DEFAULT_TICKS));
        for (String channel : new String[] {"item", "fluid", "both"}) {
            start.then(literal(channel).executes(context -> start(context.getSource(), channel,
                    KeyFactoryBenchmark.DEFAULT_CALLS, KeyFactoryBenchmark.DEFAULT_TICKS))
                .then(argument("calls", integer(1, KeyFactoryBenchmark.MAX_CALLS))
                    .executes(context -> start(context.getSource(), channel, getInteger(context, "calls"), KeyFactoryBenchmark.DEFAULT_TICKS))
                    .then(argument("ticks", integer(1, 1200))
                        .executes(context -> start(context.getSource(), channel, getInteger(context, "calls"), getInteger(context, "ticks"))))));
        }
        root.then(start);
        for (String action : new String[] {"keybench_status", "keybench_stop", "keybench_report"}) {
            root.then(literal(action).executes(context -> control(context.getSource(), action)));
        }
    }

    private static int start(CommandSourceStack source, String channel, int calls, int ticks) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!player.getMainHandItem().is(DevResourceGenerator.BLOCK.asItem())) {
            source.sendFailure(Component.literal("Hold a dev generator in your main hand; no network connection is needed"));
            return 0;
        }
        if (session != null && !session.benchmark.finished()) {
            source.sendFailure(Component.literal("A key benchmark is already running; use /neodev keybench_status or keybench_stop"));
            return 0;
        }
        try {
            session = new Session(source.getServer(), player.getUUID(), KeyFactoryBenchmark.create(channel, calls, ticks));
            source.sendSuccess(() -> Component.literal(session.benchmark.describe()
                + "; native AE2 vs ECO, 40 warmup ticks/resource + 3 measured rounds. No resources are moved. "
                + "Report is saved automatically; /neodev keybench_status checks progress."), false);
            return 1;
        } catch (RuntimeException | LinkageError problem) {
            source.sendFailure(Component.literal("Cannot start key benchmark: " + problem));
            return 0;
        }
    }

    private static int control(CommandSourceStack source, String action) {
        if (session == null || session.server != source.getServer()) {
            source.sendFailure(Component.literal("Start with /neodev keybench while holding a dev generator"));
            return 0;
        }
        if (action.equals("keybench_stop")) {
            session.benchmark.stop("cancelled", "Stopped by command");
            finish(session);
        }
        if (action.equals("keybench_report")) {
            try {
                Path file = session.benchmark.finished() && session.report != null ? session.report : session.save();
                source.sendSuccess(() -> Component.literal("Key benchmark report: " + file.toAbsolutePath()), false);
            } catch (IOException problem) {
                source.sendFailure(Component.literal("Could not write key benchmark report: " + problem));
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal(session.benchmark.describe()), false);
        return 1;
    }

    private static void tick(ServerTickEvent.Post event) {
        Session current = session;
        if (current == null || current.server != event.getServer() || current.notified) return;
        if (!DevToolsConfig.enabled()) current.benchmark.stop("cancelled", "Development tools were disabled");
        if (current.server.getPlayerList().getPlayer(current.owner) == null)
            current.benchmark.stop("cancelled", "Player disconnected");
        current.benchmark.tick();
        if (current.benchmark.finished()) finish(current);
    }

    private static void finish(Session current) {
        if (current.notified) return;
        current.notified = true;
        String message = current.benchmark.describe();
        try {
            message += "; report: " + current.save().toAbsolutePath();
        } catch (IOException problem) {
            message += "; report write failed: " + problem;
            LoggerFactory.getLogger(DevKeyBenchmark.class).warn("Could not write key benchmark report", problem);
        }
        var player = current.server.getPlayerList().getPlayer(current.owner);
        if (player != null) player.sendSystemMessage(Component.literal(message));
    }
}
