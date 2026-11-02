package cn.dancingsnow.neoecoae.integration.jei;

import cn.dancingsnow.neoecoae.crafting.display.format.BigNumberFormatter;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Compact count overlays for large-workstation slots, with the full count kept in the item tooltip. */
@SuppressWarnings("removal")
public final class WorkstationItemStackRenderer implements IIngredientRenderer<ItemStack> {
    private static final WorkstationItemStackRenderer INSTANCE = new WorkstationItemStackRenderer();
    private static final float COUNT_SCALE = 0.7f;

    private WorkstationItemStackRenderer() {}

    public static IRecipeSlotBuilder configure(IRecipeSlotBuilder slot) {
        return slot.setCustomRenderer(VanillaTypes.ITEM_STACK, INSTANCE);
    }

    @Override
    public void render(GuiGraphics graphics, ItemStack stack) {
        var font = Minecraft.getInstance().font;
        RenderSystem.enableDepthTest();
        graphics.renderFakeItem(stack, 0, 0);
        graphics.renderItemDecorations(font, stack, 0, 0, "");
        RenderSystem.disableBlend();
        if (stack.getCount() <= 1) return;
        String count = BigNumberFormatter.formatSI(BigInteger.valueOf(stack.getCount()), 1);
        float scale = Math.min(COUNT_SCALE, 15.0f / Math.max(1, font.width(count)));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(16 - font.width(count) * scale, 16 - font.lineHeight * scale, 200);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, count, 0, 0, 0xFFFFFFFF, true);
        pose.popPose();
    }

    @Override
    public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
        var minecraft = Minecraft.getInstance();
        return getTooltip(stack, Item.TooltipContext.of(minecraft.level), minecraft.player, flag);
    }

    @Override
    public List<Component> getTooltip(ItemStack stack, Item.TooltipContext context, @Nullable Player player, TooltipFlag flag) {
        var lines = new ArrayList<>(stack.getTooltipLines(context, player, flag));
        if (stack.getCount() > 1) {
            lines.add(Component.translatable("gui.neoecoae.large_integrated_working_station.recipe_amount",
                BigNumberFormatter.formatTooltip(BigInteger.valueOf(stack.getCount()), 1)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
