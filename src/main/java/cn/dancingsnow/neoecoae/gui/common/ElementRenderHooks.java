package cn.dancingsnow.neoecoae.gui.common;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElementRendererRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;

/**
 * Bridges the LDLib2 26.1 renderer registry back to per-element draw overrides.
 *
 * <p>LDLib2 1.21.1 let elements override {@code drawContents}, {@code drawBackgroundAdditional} and
 * {@code drawBackgroundOverlay} directly. In 26.1 those phases live in {@link UIElementRenderer}s
 * registered per class, and built-in renderers (Button, ItemSlot, FluidSlot, TextElement...) no
 * longer call the element's own {@code drawBackgroundAdditional}. Elements that need the old
 * behaviour implement one of the hook interfaces below and register their class once with
 * {@link #register(Class)} (typically from a static initializer).
 */
public final class ElementRenderHooks {
    private ElementRenderHooks() {
    }

    /** Replaces the whole contents phase (background additional + children). */
    public interface Contents {
        void drawContents(IGUIContext context, Runnable drawDefault);
    }

    /** Replaces the background-additional phase; {@code drawParent} runs the superclass renderer. */
    public interface Background {
        void drawBackgroundAdditional(IGUIContext context, Runnable drawParent);
    }

    /** Replaces the overlay phase; {@code drawParent} runs the superclass renderer. */
    public interface Overlay {
        void drawBackgroundOverlay(IGUIContext context, Runnable drawParent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(Class<? extends UIElement> type) {
        UIElementRendererRegistry.register((Class) type, new HookRenderer(type));
    }

    private static final class HookRenderer implements UIElementRenderer<UIElement> {
        private final Class<? extends UIElement> type;

        private HookRenderer(Class<? extends UIElement> type) {
            this.type = type;
        }

        @SuppressWarnings("unchecked")
        private UIElementRenderer<UIElement> parent() {
            return (UIElementRenderer<UIElement>) UIElementRendererRegistry.findParentRendererByType(type);
        }

        private void defaultContents(UIElement element, IGUIContext context) {
            UIElementRenderer.super.drawContents(element, context);
        }

        @Override
        public void drawContents(UIElement element, IGUIContext context) {
            if (element instanceof Contents contents) {
                contents.drawContents(context, () -> defaultContents(element, context));
            } else {
                defaultContents(element, context);
            }
        }

        @Override
        public void drawBackgroundAdditional(UIElement element, IGUIContext context) {
            if (element instanceof Background background) {
                background.drawBackgroundAdditional(context, () -> parent().drawBackgroundAdditional(element, context));
            } else {
                parent().drawBackgroundAdditional(element, context);
            }
        }

        @Override
        public void drawBackgroundOverlay(UIElement element, IGUIContext context) {
            if (element instanceof Overlay overlay) {
                overlay.drawBackgroundOverlay(context, () -> parent().drawBackgroundOverlay(element, context));
            } else {
                parent().drawBackgroundOverlay(element, context);
            }
        }
    }
}
