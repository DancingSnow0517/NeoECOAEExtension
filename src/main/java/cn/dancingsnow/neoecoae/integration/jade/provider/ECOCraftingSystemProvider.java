package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingSystemProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        CompoundTag data = blockAccessor.getServerData();
        if (data.contains("overclocked") && data.getBoolean("overclocked")) {
            iTooltip.add(Component.translatable("jade.neoecoae.overclocked"));
        }
        if (data.contains("activeCooling") && data.getBoolean("activeCooling")) {
            iTooltip.add(Component.translatable("jade.neoecoae.activeCooling"));
        }
        if (data.contains("coolant")) {
            iTooltip.add(Component.translatable("jade.neoecoae.coolant", data.getInt("coolant")));
        }
    }

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            compoundTag.putBoolean("overclocked", system.isOverclocked());
            compoundTag.putBoolean("activeCooling", system.isActiveCooling());
            compoundTag.putInt("coolant", system.getCoolant());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }
}
