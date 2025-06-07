package cn.dancingsnow.neoecoae.gui.widget;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

@Accessors(chain = true)
public class ScalableTextBoxWidget extends Widget {

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> STREAM_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Setter
    private Supplier<List<Component>> textSupplier = Collections::emptyList;
    private int space = 1;
    @Setter
    private int fontColor = -1;
    @Setter
    private boolean isShadow = false;
    @Setter
    private boolean isCenter = false;

    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    private List<Component> lastText;

    public ScalableTextBoxWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public ScalableTextBoxWidget setSpace(int space) {
        Preconditions.checkArgument(space >= 0, "space must be greater than or equal to 0");
        this.space = space;
        return this;
    }

    private void calculate() {
        if (lastText != null && !lastText.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int maxWidth = lastText.stream().mapToInt(font::width).max().orElse(0);
            float scaleWidth = (float) getSizeWidth() / maxWidth;
            float scaleHeight = (float) (getSizeHeight() - space * (lastText.size() - 1)) / (lastText.size() * font.lineHeight);
            this.scale = Math.min(scaleWidth, scaleHeight);
            if (isCenter) {
                this.offsetX = (getSizeWidth() - maxWidth * scale) / 2;
                this.offsetY = (getSizeHeight() - font.lineHeight * scale) / 2;
            } else {
                this.offsetX = 0f;
                this.offsetY = 0f;
            }
        }
    }

    @Override
    public void writeInitialData(RegistryFriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        if (isClientSideWidget) return;
        this.lastText = textSupplier.get();
        STREAM_CODEC.encode(buffer, lastText);
    }

    @Override
    public void readInitialData(RegistryFriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        this.lastText = STREAM_CODEC.decode(buffer);
        calculate();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (isClientSideWidget) return;
        var text = textSupplier.get();
        if (!text.equals(lastText)) {
            this.lastText = text;
            writeUpdateInfo(0, buffer -> STREAM_CODEC.encode(buffer, lastText));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, RegistryFriendlyByteBuf buffer) {
        if (id == 0) {
            this.lastText = STREAM_CODEC.decode(buffer);
            calculate();
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        if (isClientSideWidget) {
            var text = textSupplier.get();
            if (!text.equals(lastText)) {
                this.lastText = text;
                calculate();
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (lastText != null && !lastText.isEmpty()) {
            Position position = getPosition();
            Font font = Minecraft.getInstance().font;
            graphics.pose().pushPose();
            graphics.pose().translate(position.x, position.y, 0);
            graphics.pose().translate(offsetX, offsetY, 0);
            graphics.pose().scale(scale, scale, 1);
            float y = 0;
            float ySpace = font.lineHeight + space / scale;
            for (var textLine : lastText) {
                graphics.drawString(font, textLine.getString(), 0, (int) y, fontColor, isShadow);
                y += ySpace;
            }
            graphics.pose().popPose();
        }
        drawOverlay(graphics, mouseX, mouseY, partialTicks);
    }
}
