package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public final class NEAeButtons {
    private NEAeButtons() {
    }

    public static Button toolbarIcon(Supplier<NEAeSprite> icon) {
        Button button = new IconButton(icon, false);
        button.buttonStyle(NEAeButtons::toolbarStyle);
        return button;
    }

    public static Button aeToolbarIcon(Supplier<NEAeSprite> icon) {
        Button button = new IconButton(icon, () -> ItemStack.EMPTY, true);
        button.buttonStyle(NEAeButtons::transparentStyle);
        return button;
    }

    public static Button aeToolbarItem(Supplier<ItemStack> item) {
        Button button = new IconButton(() -> null, item, true);
        button.buttonStyle(NEAeButtons::transparentStyle);
        return button;
    }

    public static Button aeToolbarContent(Supplier<NEAeSprite> icon, Supplier<ItemStack> item) {
        Button button = new IconButton(icon, item, true);
        button.buttonStyle(NEAeButtons::transparentStyle);
        return button;
    }

    public static Button tabIcon(Supplier<NEAeSprite> icon) {
        Button button = new TabIconButton(icon);
        button.buttonStyle(NEAeButtons::transparentStyle);
        return button;
    }

    public static void transparentStyle(Button.ButtonStyle style) {
        style.baseTexture(IGuiTexture.EMPTY)
            .hoverTexture(IGuiTexture.EMPTY)
            .pressedTexture(IGuiTexture.EMPTY);
    }

    private static void toolbarStyle(Button.ButtonStyle style) {
        style.baseTexture(NEAeSprite.TOOLBAR_BUTTON_BACKGROUND.texture())
            .hoverTexture(NEAeSprite.TOOLBAR_BUTTON_BACKGROUND_HOVER.texture())
            .pressedTexture(NEAeSprite.TOOLBAR_BUTTON_BACKGROUND_HOVER.texture());
    }

    private static final class IconButton extends Button {
        private final Supplier<NEAeSprite> icon;
        private final Supplier<ItemStack> item;
        private final boolean aeLayout;

        private IconButton(Supplier<NEAeSprite> icon, boolean aeLayout) {
            this(icon, () -> ItemStack.EMPTY, aeLayout);
        }

        private IconButton(Supplier<NEAeSprite> icon, Supplier<ItemStack> item, boolean aeLayout) {
            this.icon = icon;
            this.item = item;
            this.aeLayout = aeLayout;
            noText();
            setOverflowVisible(true);
            layout(layout -> layout.paddingAll(0));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            NEAeSprite sprite = icon.get();
            if (aeLayout) {
                boolean hovered = getState() != State.DEFAULT;
                int yOffset = hovered ? 1 : 0;
                NEAeSprite background = hovered
                        ? NEAeSprite.TOOLBAR_BUTTON_BACKGROUND_HOVER
                        : isFocused() ? NEAeSprite.TOOLBAR_BUTTON_BACKGROUND_FOCUS : NEAeSprite.TOOLBAR_BUTTON_BACKGROUND;
                background.draw(context, getPositionX() - 1.0F, getPositionY() + yOffset, 18, 20);
                ItemStack stack = item.get();
                if (stack != null && !stack.isEmpty()) {
                    context.graphics.renderItem(stack, Math.round(getPositionX()), Math.round(getPositionY() + 1.0F + yOffset));
                } else if (sprite != null) {
                    sprite.draw(context, getPositionX(), getPositionY() + 1.0F + yOffset);
                }
                return;
            }
            if (sprite != null) {
                sprite.draw(context,
                        getPositionX() + (getSizeWidth() - sprite.width()) / 2.0F,
                        getPositionY() + (getSizeHeight() - sprite.height()) / 2.0F);
            }
        }
    }

    private static final class TabIconButton extends Button {
        private final Supplier<NEAeSprite> icon;

        private TabIconButton(Supplier<NEAeSprite> icon) {
            this.icon = icon;
            noText();
            setOverflowVisible(true);
            layout(layout -> layout.paddingAll(0));
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            NEAeSprite background = isFocused() ? NEAeSprite.TAB_BUTTON_BACKGROUND_FOCUS : NEAeSprite.TAB_BUTTON_BACKGROUND;
            background.draw(context, getPositionX(), getPositionY());
            NEAeSprite sprite = icon.get();
            sprite.draw(context, getPositionX() + 2.0F, getPositionY() + 1.0F);
        }
    }
}
