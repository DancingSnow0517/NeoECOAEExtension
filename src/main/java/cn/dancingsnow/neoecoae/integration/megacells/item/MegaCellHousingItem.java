package cn.dancingsnow.neoecoae.integration.megacells.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class MegaCellHousingItem extends Item {
    private final String storageTypeKey;

    public MegaCellHousingItem(Properties properties, String storageTypeKey) {
        super(properties);
        this.storageTypeKey = storageTypeKey;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
            List<Component> lines,
            TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.neoecoae.megacells.housing", Component.translatable(storageTypeKey))
                .withStyle(ChatFormatting.GRAY));
    }
}
