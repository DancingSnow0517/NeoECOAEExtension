package cn.dancingsnow.neoecoae.platform.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;

/** Reads the retained ECO JSON format into LDLib1 widgets; unsupported screen features fail explicitly. */
public final class NEUiJsonBridge {
    public interface Context {
        NEUiElement createCustom(JsonObject node);
        NEUiElement playerInventory();
    }

    private NEUiJsonBridge() {}

    public static NEUiElement load(ResourceLocation source, Context context) {
        String path = "assets/" + source.getNamespace() + "/" + source.getPath();
        if (!path.endsWith(".json")) path += ".json";
        try (var input = NEUiJsonBridge.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("UI resource missing: " + source);
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return load(JsonParser.parseReader(reader).getAsJsonObject(), context);
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Cannot construct LDLib1 UI " + source, ex);
        }
    }

    public static NEUiElement load(JsonObject document, Context context) {
        NEUiElement root = node(document.has("root") ? document.getAsJsonObject("root") : document, context);
        root.arrange();
        return root;
    }

    private static NEUiElement node(JsonObject definition, Context context) {
        for (String property : definition.keySet()) {
            if (!java.util.Set.of("type", "id", "class", "classes", "layout", "children", "custom", "visible")
                    .contains(property)) {
                throw new IllegalArgumentException("Unsupported LDLib1 UI property: " + property);
            }
        }
        String type = definition.has("type") ? definition.get("type").getAsString() : "container";
        NEUiElement element = switch (type) {
            case "container", "element" -> new NEUiElement();
            case "custom" -> context.createCustom(definition);
            case "player_inventory" -> context.playerInventory();
            default -> throw new IllegalArgumentException("Unsupported LDLib1 UI element: " + type);
        };
        if (element == null) throw new IllegalArgumentException("Unbound LDLib1 UI element: " + type);
        if (definition.has("id")) element.setId(definition.get("id").getAsString());
        boolean visible = !definition.has("visible") || definition.get("visible").getAsBoolean();
        element.setVisible(visible);
        element.layoutNode().style.display = visible;
        if (definition.has("layout")) UiLayoutJson.apply(element.layoutNode().style, definition.getAsJsonObject("layout"));
        var classes = definition.has("class") ? definition.get("class") : definition.get("classes");
        if (classes != null) {
            if (classes.isJsonArray()) classes.getAsJsonArray().forEach(value -> applyClass(element, value.getAsString()));
            else for (String name : classes.getAsString().trim().split("\\s+")) applyClass(element, name);
        }
        if (definition.has("children")) {
            for (var child : definition.getAsJsonArray("children")) element.addChild(node(child.getAsJsonObject(), context));
        }
        return element;
    }

    private static void applyClass(NEUiElement element, String name) {
        if (name.isBlank()) return;
        if (name.equals("panel_bg")) {
            element.setBackground(new ResourceBorderTexture("neoecoae:textures/gui/common/background.png", 16, 16, 2, 2));
        } else {
            throw new IllegalArgumentException("Unsupported LDLib1 UI style class: " + name);
        }
    }
}
