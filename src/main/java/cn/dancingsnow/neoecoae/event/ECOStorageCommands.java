package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteDomainState;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import com.mojang.brigadier.CommandDispatcher;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Administrative recovery command for archived V1 storage domains. */
public final class ECOStorageCommands {
    private ECOStorageCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("neoecoae")
                .then(Commands.literal("storage")
                        .then(Commands.literal("migrate")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .executes(context -> migrate(
                                                context.getSource(), UuidArgument.getUuid(context, "uuid")))))));
    }

    private static int migrate(CommandSourceStack source, UUID domainId) {
        ServerLevel level = source.getLevel();
        var engine = ECOInfiniteStorageDomains.migrateArchivedV1(level, domainId);
        if (engine.getState() == ECOInfiniteDomainState.READY) {
            source.sendSuccess(() -> Component.literal("V1 archive migrated to V2 for domain " + domainId), true);
            return 1;
        }
        source.sendFailure(Component.literal("V1 archive migration failed: "
                + engine.getFailureReason().orElse(engine.getState().name())));
        return 0;
    }
}
