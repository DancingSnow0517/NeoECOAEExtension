package cn.dancingsnow.neoecoae.integration.jade.provider;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum ECOCraftingSystemProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag compoundTag, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof ECOCraftingSystemBlockEntity system) {
            compoundTag.putBoolean("overclocked", system.isOverclocked());
            compoundTag.putBoolean("activeCooling", system.isActiveCooling());
            compoundTag.putInt("coolant", system.getCoolant());
            compoundTag.putInt("theoreticalOverclock", system.getOverlockTimes());
            compoundTag.putInt("effectiveOverclock", system.getEffectiveOverclockTimes());
            compoundTag.putInt("coolingMaxOverclock", system.getDisplayedCoolingMaxOverclock());
        }
    }

    @Override
    public Identifier getUid() {
        return NeoECOAE.id("eco_crafting_system");
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
            CompoundTag data = blockAccessor.getServerData();
            var overlocked = data.getBoolean("overlocked");
            var theoreticalOverclock = data.getInt("theoreticalOverclock");
            var effectiveOverclock = data.getInt("effectiveOverclock");
            if (overlocked.isPresent() && overlocked.get()) {
                iTooltip.add(Component.translatable("jade.neoecoae.overclocked"));
                if (theoreticalOverclock.isPresent() && effectiveOverclock.isPresent()) {
                    iTooltip.add(Component.translatable("jade.neoecoae.overclock_status", theoreticalOverclock.get(), effectiveOverclock.get()));
                }
            }

            var activeCooling = data.getBoolean("activeCooling");
            if (activeCooling.isPresent() && activeCooling.get()) {
                iTooltip.add(Component.translatable("jade.neoecoae.activeCooling"));
            }

            var coolant = data.getInt("coolant");
            coolant.ifPresent(integer -> iTooltip.add(Component.translatable("jade.neoecoae.coolant", integer)));

            var coolingMaxOverclock = data.getInt("coolingMaxOverclock");
            if (coolingMaxOverclock.isPresent()) {
                if (coolingMaxOverclock.get() >= 0) {
                    iTooltip.add(Component.translatable("jade.neoecoae.coolant_max_overclock", coolingMaxOverclock.get()));
                } else {
                    iTooltip.add(Component.translatable("jade.neoecoae.coolant_max_overclock.none"));
                }
            }
        }

        @Override
        public Identifier getUid() {
            return NeoECOAE.id("eco_crafting_system");
        }
    }
}
