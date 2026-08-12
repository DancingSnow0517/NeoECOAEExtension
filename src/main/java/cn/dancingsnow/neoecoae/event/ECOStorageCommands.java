package cn.dancingsnow.neoecoae.event;

import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteDomainState;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ECOStorageCommands {
    private static final int DIAGNOSTIC_PAGE_SIZE = 8;
    private static final int MAX_ENCODED_KEY_TEXT_LENGTH = 180;

    private ECOStorageCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("neoecoae")
                .then(Commands.literal("storage")
                    .then(Commands.literal("migrate")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                            .suggests(ECOStorageCommands::suggestDomainIds)
                            .executes(context -> migrate(context.getSource(),
                                UuidArgument.getUuid(context, "uuid")))))
                    .then(Commands.literal("recover")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                            .suggests(ECOStorageCommands::suggestDomainIds)
                            .executes(context -> recover(context.getSource(),
                                UuidArgument.getUuid(context, "uuid")))))
                    .then(Commands.literal("diagnose")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                            .suggests(ECOStorageCommands::suggestDomainIds)
                            .executes(context -> diagnose(context.getSource(),
                                UuidArgument.getUuid(context, "uuid"), 1))
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> diagnose(context.getSource(),
                                    UuidArgument.getUuid(context, "uuid"),
                                    IntegerArgumentType.getInteger(context, "page"))))))
                    .then(Commands.literal("ignore-missing")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                            .suggests(ECOStorageCommands::suggestDomainIds)
                            .executes(context -> ignoreMissing(context.getSource(),
                                UuidArgument.getUuid(context, "uuid"))))))
        );
    }

    private static int migrate(CommandSourceStack source, UUID domainId) {
        ServerLevel level = source.getLevel();
        var engine = ECOInfiniteStorageDomains.migrateArchivedV1(level, domainId);
        if (engine.getState() == ECOInfiniteDomainState.READY) {
            source.sendSuccess(() -> Component.literal("V1 archive migrated to V2 for domain " + domainId), true);
            return 1;
        }
        source.sendFailure(Component.literal(
            "V1 archive migration failed: " + engine.getFailureReason().orElse(engine.getState().name())
        ));
        return 0;
    }

    private static int recover(CommandSourceStack source, UUID domainId) {
        var engine = ECOInfiniteStorageDomains.recover(source.getLevel(), domainId);
        if (engine.getState() == ECOInfiniteDomainState.READY && engine.isHealthy()) {
            ECOStorageSystemBlockEntity.refreshRecoveredDomain(source.getLevel(), domainId);
            source.sendSuccess(
                () -> Component.literal("无限存储已恢复，持久化校验通过。")
                    .withStyle(ChatFormatting.GREEN),
                false
            );
            reportOrphanedEntries(source, domainId, engine, 1);
            return 1;
        }
        reportRecoveryFailure(source, domainId, engine);
        return 0;
    }

    private static int diagnose(CommandSourceStack source, UUID domainId, int page) {
        ECOInfiniteStorageEngine engine = ECOInfiniteStorageDomains.openExisting(source.getLevel(), domainId);
        if (engine.getState() != ECOInfiniteDomainState.READY || !engine.isHealthy()) {
            reportRecoveryFailure(source, domainId, engine);
            return 0;
        }
        if (engine.getOrphanedStacks().isEmpty()) {
            source.sendSuccess(
                () -> Component.literal("无限存储诊断完成：没有因缺失模组而不可用的条目。")
                    .withStyle(ChatFormatting.GREEN),
                false
            );
            return 1;
        }
        return reportOrphanedEntries(source, domainId, engine, page) ? 1 : 0;
    }

    private static void reportRecoveryFailure(
        CommandSourceStack source,
        UUID domainId,
        ECOInfiniteStorageEngine engine
    ) {
        source.sendFailure(Component.literal("无限存储恢复失败").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        source.sendFailure(Component.empty()
            .append(Component.literal("域：").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(domainId.toString()).withStyle(ChatFormatting.DARK_GRAY)));
        source.sendFailure(Component.empty()
            .append(Component.literal("状态：").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(engine.getState().name()).withStyle(ChatFormatting.RED)));
        source.sendFailure(Component.empty()
            .append(Component.literal("原因：").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(engine.getFailureReason().orElse("未提供详细原因")).withStyle(ChatFormatting.GOLD)));
        if (!engine.getOrphanedStacks().isEmpty()) {
            reportOrphanedEntries(source, domainId, engine, 1);
        }
    }

    private static boolean reportOrphanedEntries(
        CommandSourceStack source,
        UUID domainId,
        ECOInfiniteStorageEngine engine,
        int page
    ) {
        List<ECOInfiniteStorageEngine.OrphanedStack> entries = engine.getOrphanedStacks().stream()
            .sorted(Comparator.comparing(stack -> describeEncodedKey(stack.encodedKey()).id()))
            .toList();
        if (entries.isEmpty()) {
            return true;
        }
        int totalPages = (entries.size() + DIAGNOSTIC_PAGE_SIZE - 1) / DIAGNOSTIC_PAGE_SIZE;
        if (page > totalPages) {
            source.sendFailure(Component.literal(
                "诊断页码超出范围：第 " + page + " 页不存在，当前共有 " + totalPages + " 页。"
            ).withStyle(ChatFormatting.RED));
            return false;
        }
        int firstEntry = (page - 1) * DIAGNOSTIC_PAGE_SIZE;
        int lastEntry = Math.min(firstEntry + DIAGNOSTIC_PAGE_SIZE, entries.size());
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("检测到 ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(Integer.toString(entries.size())).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" 个缺失模组条目；数据已保留，无法挂载。")
                .withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.empty()
            .append(Component.literal("域：").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(domainId.toString()).withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("  第 " + page + " / " + totalPages + " 页").withStyle(ChatFormatting.GRAY)), false);
        for (int index = firstEntry; index < lastEntry; index++) {
            ECOInfiniteStorageEngine.OrphanedStack entry = entries.get(index);
            EncodedKeyDescription description = describeEncodedKey(entry.encodedKey());
            source.sendSuccess(() -> Component.empty()
                .append(Component.literal(" - [" + description.type() + "] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(description.id()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" x ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(entry.amount().toString()).withStyle(ChatFormatting.GOLD)), false);
        }
        if (page < totalPages) {
            source.sendSuccess(() -> Component.literal(
                "使用 /neoecoae storage diagnose " + domainId + " " + (page + 1) + " 查看下一页。"
            ).withStyle(ChatFormatting.GRAY), false);
        }
        return true;
    }

    private static EncodedKeyDescription describeEncodedKey(CompoundTag encodedKey) {
        String type = encodedKey.getString("#");
        String id = encodedKey.getString("id");
        if (type.isBlank()) {
            type = "unknown";
        }
        if (id.isBlank()) {
            id = abbreviate(encodedKey.toString());
        }
        return new EncodedKeyDescription(type, id);
    }

    private static String abbreviate(String text) {
        return text.length() <= MAX_ENCODED_KEY_TEXT_LENGTH
            ? text
            : text.substring(0, MAX_ENCODED_KEY_TEXT_LENGTH - 3) + "...";
    }

    private record EncodedKeyDescription(String type, String id) {
    }

    private static int ignoreMissing(CommandSourceStack source, UUID domainId) {
        var engine = ECOInfiniteStorageDomains.openExisting(source.getLevel(), domainId);
        if (engine.getState() != ECOInfiniteDomainState.READY) {
            source.sendFailure(Component.literal(
                "Infinite storage is not ready: " + engine.getFailureReason().orElse(engine.getState().name())
            ));
            return 0;
        }
        if (!engine.hasOrphanedEntries()) {
            source.sendFailure(Component.literal("No missing-mod storage entries exist for domain " + domainId));
            return 0;
        }
        if (!engine.acknowledgeOrphanedEntries()) {
            source.sendFailure(Component.literal("Unable to acknowledge missing-mod entries for domain " + domainId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            "Missing-mod entries are now ignored for domain " + domainId + "; data remains preserved"
        ), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestDomainIds(
        CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
            ECOInfiniteStorageDomains.findExistingDomainIds(context.getSource().getLevel()).stream()
                .map(UUID::toString)
                .toList(),
            builder
        );
    }
}
