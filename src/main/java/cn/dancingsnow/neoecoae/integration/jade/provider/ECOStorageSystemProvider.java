package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

public enum ECOStorageSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ECOStorageSystemBlockEntity host)
            || !host.getTier().supportsFeature(ECOFeature.INFINITE_STORAGE_HOST)) {
            return;
        }
        if (host.isFormedInfiniteMode()) {
            data.putString("ecoStorageDomain", host.getInfiniteDomainText());
        } else if (host.isMigratingToInfinite()) {
            data.putInt("ecoStorageMigration", host.getInfiniteMigrationProgressPercent());
            ECOInfiniteStorageEngine.MigrationBlock block = host.getInfiniteMigrationBlock();
            if (block != null) {
                // Kind and detail only: the client builds the translated line itself.
                data.putString("ecoStorageMigrationBlock", block.kind().name());
                data.putString("ecoStorageMigrationBlockDetail", block.detail());
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains("ecoStorageMigration")) {
            tooltip.add(Component.translatable("gui.neoecoae.storage.ui.migration_progress",
                data.getInt("ecoStorageMigration")).withStyle(ChatFormatting.AQUA));
            Component reason = blockReason(data.getString("ecoStorageMigrationBlock"),
                data.getString("ecoStorageMigrationBlockDetail"));
            if (reason != null) {
                tooltip.add(Component.translatable("gui.neoecoae.storage.ui.migration_blocked", reason)
                    .withStyle(ChatFormatting.RED));
            }
            return;
        }
        String domain = data.getString("ecoStorageDomain");
        if (domain.isEmpty()) return;
        tooltip.add(Component.translatable("jade.neoecoae.storage.infinite_enabled").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("UUID: " + domain).withStyle(ChatFormatting.AQUA));
    }

    private static Component blockReason(String kind, String detail) {
        if (kind.isEmpty()) return null;
        try {
            return new ECOInfiniteStorageEngine.MigrationBlock(
                ECOInfiniteStorageEngine.MigrationBlock.Kind.valueOf(kind), detail).toComponent();
        } catch (IllegalArgumentException unknown) {
            return Component.literal(kind);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_storage_system");
    }
}
