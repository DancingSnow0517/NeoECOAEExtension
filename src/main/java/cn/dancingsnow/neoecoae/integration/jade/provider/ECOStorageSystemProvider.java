package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.api.capability.ECOFeature;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
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
        if (host.canUseHostDomainStorage()) data.putString("ecoStorageDomain", host.getInfiniteDomainText());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        String domain = data.getString("ecoStorageDomain");
        if (domain.isEmpty()) return;
        tooltip.add(Component.translatable("jade.neoecoae.storage.infinite_enabled").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("UUID: " + domain).withStyle(ChatFormatting.AQUA));
    }


    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_storage_system");
    }
}
