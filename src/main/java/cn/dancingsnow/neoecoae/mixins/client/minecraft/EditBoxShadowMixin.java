package cn.dancingsnow.neoecoae.mixins.client.minecraft;

import cn.dancingsnow.neoecoae.client.gui.widget.NEEditBoxStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 1.20 EditBox calls the default-shadow overloads; ECO text fields keep their explicit shadow style. */
@Mixin(EditBox.class)
public abstract class EditBoxShadowMixin {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int neoecoae$formattedShadow(GuiGraphics graphics, Font font, FormattedCharSequence text,
            int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof NEEditBoxStyle style
                ? graphics.drawString(font, text, x, y, color, style.neoecoae$textShadow())
                : original.call(graphics, font, text, x, y, color);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int neoecoae$componentShadow(GuiGraphics graphics, Font font, Component text,
            int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof NEEditBoxStyle style
                ? graphics.drawString(font, text, x, y, color, style.neoecoae$textShadow())
                : original.call(graphics, font, text, x, y, color);
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int neoecoae$stringShadow(GuiGraphics graphics, Font font, String text,
            int x, int y, int color, Operation<Integer> original) {
        return (Object) this instanceof NEEditBoxStyle style
                ? graphics.drawString(font, text, x, y, color, style.neoecoae$textShadow())
                : original.call(graphics, font, text, x, y, color);
    }
}
