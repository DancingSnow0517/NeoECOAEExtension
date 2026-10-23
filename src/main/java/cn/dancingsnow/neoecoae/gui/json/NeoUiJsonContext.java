package cn.dancingsnow.neoecoae.gui.json;

import com.google.gson.JsonObject;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.api.IECOTier;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime hooks used by {@link NeoUiJsonLoader}.  JSON files only describe the
 * shape of a UI.  A caller supplies the small, whitelisted set of dynamic
 * components and actions that a particular screen is allowed to use.
 */
public interface NeoUiJsonContext {
    /** A context which creates no custom elements and performs no configuration. */
    NeoUiJsonContext EMPTY = new NeoUiJsonContext() { };

    /** Tier used by the six replaceable panel atlases in this UI instance. */
    @Nullable
    default IECOTier panelTier() {
        return null;
    }

    /**
     * Creates a dynamic element for a node whose {@code type} is {@code custom}.
     * Returning {@code null} means that this context does not support the named
     * custom element; the loader will report that as a resource error.
     */
    default UIElement createCustom(JsonObject node) {
        return null;
    }

    /**
     * Applies the caller's allow-listed actions and data bindings.  The loader
     * never interprets action or binding strings itself and never reflects over
     * a class named in JSON.
     */
    default void configure(JsonObject node, UIElement element) {
    }
}
