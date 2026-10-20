package cn.dancingsnow.neoecoae.gui.json;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.utils.ResourceHelper;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/** Loads the small, deliberately non-executable NeoECOAE UI JSON format. */
public final class NeoUiJsonLoader {
    private NeoUiJsonLoader() {
    }

    /**
     * Loads {@code assets/&lt;namespace&gt;/&lt;path&gt;} from the active resource
     * manager.  The {@code .json} suffix is added when omitted.
     */
    public static UIElement load(ResourceLocation location, NeoUiJsonContext context) {
        if (location == null) {
            throw new IllegalArgumentException("UI resource location cannot be null");
        }
        NeoUiJsonContext safeContext = context == null ? NeoUiJsonContext.EMPTY : context;
        ResourceLocation resourceLocation = withJsonSuffix(location);
        try (Reader reader = openReader(resourceLocation)) {
            if (reader == null) {
                throw new NeoUiJsonException("UI resource not found: " + resourceLocation);
            }
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) {
                throw error(resourceLocation, "root must be a JSON object", null);
            }
            JsonObject document = json.getAsJsonObject();
            JsonObject root = rootNode(document, resourceLocation);
            return createNode(root, safeContext, resourceLocation);
        } catch (NeoUiJsonException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw error(resourceLocation, "could not read or parse JSON", e);
        }
    }

    /**
     * Uses LDLib2's active client/server resource manager when available, then
     * falls back to the mod's classpath resource.  The fallback keeps UI
     * construction safe for server-side menu setup and headless tests; it does
     * not permit a JSON file to load arbitrary classes or code.
     */
    private static Reader openReader(ResourceLocation location) {
        try {
            var resourceManager = ResourceHelper.getResourceManager();
            if (resourceManager != null) {
                Optional<Resource> resource = resourceManager.getResource(location);
                if (resource.isPresent()) {
                    return resource.get().openAsReader();
                }
            }
        } catch (IOException | RuntimeException | LinkageError ignored) {
            // Dedicated server and headless tests do not have a Minecraft client.
        }
        String classpathLocation = "assets/" + location.getNamespace() + "/" + location.getPath();
        InputStream stream = NeoUiJsonLoader.class.getClassLoader().getResourceAsStream(classpathLocation);
        return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    /** Loads a JSON object directly; useful for resource reloaders and tests. */
    public static UIElement load(JsonObject root, ResourceLocation source, NeoUiJsonContext context) {
        if (root == null) {
            throw new IllegalArgumentException("UI root cannot be null");
        }
        JsonObject definition = rootNode(root, source == null
            ? ResourceLocation.withDefaultNamespace("ui.json") : source);
        return createNode(definition, context == null ? NeoUiJsonContext.EMPTY : context,
            source == null ? ResourceLocation.withDefaultNamespace("ui.json") : source);
    }

    private static JsonObject rootNode(JsonObject document, ResourceLocation source) {
        JsonElement value = document.get("root");
        if (value == null || value.isJsonNull()) {
            return document;
        }
        if (!value.isJsonObject()) {
            throw error(source, "root must be a JSON object", null);
        }
        return value.getAsJsonObject();
    }

    private static UIElement createNode(JsonObject node, NeoUiJsonContext context, ResourceLocation source) {
        JsonElement typeValue = node.get("type");
        if (typeValue != null && (!typeValue.isJsonPrimitive() || !typeValue.getAsJsonPrimitive().isString())) {
            throw error(source, "type must be a string", null);
        }
        String type = string(node, "type").orElse("container").toLowerCase(Locale.ROOT);
        UIElement element;
        switch (type) {
            case "container", "element" -> element = new UIElement();
            case "texture" -> element = new UIElement();
            case "label" -> element = createLabel(node);
            case "button" -> element = createButton(node);
            case "item_slot" -> element = new ItemSlot();
            case "fluid_slot" -> element = new FluidSlot();
            case "player_inventory" -> element = new InventorySlots();
            case "custom" -> {
                element = context.createCustom(node);
                if (element == null) {
                    throw error(source, "custom element is not registered: " + string(node, "name").orElse("<unnamed>"), null);
                }
            }
            default -> throw error(source, "unsupported UI element type: " + type, null);
        }

        configureIdentity(element, node);
        applyLayout(element, node);
        String overflow = string(node, "overflow").orElseGet(() ->
            object(node, "layout").flatMap(layout -> string(layout, "overflow")).orElse(""));
        if ("visible".equalsIgnoreCase(overflow)) {
            element.setOverflowVisible(true);
        }
        applyTexture(element, node, source, context);
        addChildren(element, node, context, source);
        // This is the only dynamic hook.  It is supplied by the screen and is
        // therefore the only place where an action or binding can be attached.
        context.configure(node, element);
        return element;
    }

    private static TextElement createLabel(JsonObject node) {
        TextElement label = new TextElement();
        string(node, "text").ifPresent(text -> label.setText(text, bool(node, "translate", false)));
        return label;
    }

    private static Button createButton(JsonObject node) {
        Button button = new Button();
        string(node, "text").ifPresent(text -> button.setText(text, bool(node, "translate", false)));
        if (bool(node, "no_text", false)) {
            button.noText();
        }
        return button;
    }

    private static void addChildren(UIElement parent, JsonObject node, NeoUiJsonContext context,
                                    ResourceLocation source) {
        JsonElement children = node.get("children");
        if (children == null || children.isJsonNull()) {
            return;
        }
        if (!children.isJsonArray()) {
            throw error(source, "children must be an array", null);
        }
        for (JsonElement child : children.getAsJsonArray()) {
            if (!child.isJsonObject()) {
                throw error(source, "each child must be an object", null);
            }
            parent.addChild(createNode(child.getAsJsonObject(), context, source));
        }
    }

    private static void configureIdentity(UIElement element, JsonObject node) {
        string(node, "id").filter(s -> !s.isBlank()).ifPresent(element::setId);
        JsonElement classes = node.get("class");
        if (classes == null) {
            classes = node.get("classes");
        }
        if (classes != null) {
            if (classes.isJsonPrimitive()) {
                element.addClasses(classes.getAsString().trim().split("\\s+"));
            } else if (classes.isJsonArray()) {
                for (JsonElement value : classes.getAsJsonArray()) {
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        element.addClass(value.getAsString());
                    }
                }
            }
        }
        if (node.has("visible")) {
            element.setVisible(bool(node, "visible", true));
        }
    }

    private static void applyLayout(UIElement element, JsonObject node) {
        Optional<JsonObject> declaredLayout = object(node, "layout");
        JsonObject layout = declaredLayout.orElse(node);
        // A custom element may provide its own LDLib2 layout.  When the JSON
        // node has no layout fields, leave that runtime layout intact instead
        // of replacing an absolute toolbar/slot position with relative flow.
        if (declaredLayout.isEmpty() && !hasLayoutProperty(node)) {
            return;
        }
        if (declaredLayout.isPresent() && !hasLayoutProperty(declaredLayout.get())) {
            return;
        }
        element.layout(style -> {
            String position = string(layout, "position").orElse("relative");
            style.positionType("absolute".equalsIgnoreCase(position) ? TaffyPosition.ABSOLUTE : TaffyPosition.RELATIVE);
            dimension(layout, "width", style::width, style::widthPercent);
            dimension(layout, "height", style::height, style::heightPercent);
            position(layout, "left", style::left, style::leftPercent);
            position(layout, "top", style::top, style::topPercent);
            position(layout, "right", style::right, style::rightPercent);
            position(layout, "bottom", style::bottom, style::bottomPercent);
            length(layout, "padding", style::paddingAll, style::paddingAllPercent);
            length(layout, "paddingAll", style::paddingAll, style::paddingAllPercent);
            length(layout, "paddingLeft", style::paddingLeft, style::paddingLeftPercent);
            length(layout, "paddingTop", style::paddingTop, style::paddingTopPercent);
            length(layout, "paddingRight", style::paddingRight, style::paddingRightPercent);
            length(layout, "paddingBottom", style::paddingBottom, style::paddingBottomPercent);
            length(layout, "margin", style::marginAll, style::marginAllPercent);
            length(layout, "marginAll", style::marginAll, style::marginAllPercent);
            length(layout, "marginLeft", style::marginLeft, style::marginLeftPercent);
            length(layout, "marginTop", style::marginTop, style::marginTopPercent);
            length(layout, "marginRight", style::marginRight, style::marginRightPercent);
            length(layout, "marginBottom", style::marginBottom, style::marginBottomPercent);
            length(layout, "gap", style::gapAll, style::gapAllPercent);
            length(layout, "gapAll", style::gapAll, style::gapAllPercent);
            length(layout, "gapRow", style::gapRow, style::gapRowPercent);
            length(layout, "gapColumn", style::gapColumn, style::gapColumnPercent);
            string(layout, "flexDirection").ifPresent(direction -> {
                try {
                    style.flexDirection(FlexDirection.valueOf(direction.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    throw new NeoUiJsonException("layout flexDirection is invalid: " + direction);
                }
            });
            if (layout.has("flexGrow") && layout.get("flexGrow").isJsonPrimitive()) {
                style.flexGrow(layout.get("flexGrow").getAsFloat());
            }
            if (layout.has("flex" ) && layout.get("flex").isJsonPrimitive()) {
                style.flex(layout.get("flex").getAsFloat());
            }
            enumValue(layout, "display", TaffyDisplay.class).ifPresent(style::display);
            enumValue(layout, "justifyContent", AlignContent.class).ifPresent(style::justifyContent);
            enumValue(layout, "alignContent", AlignContent.class).ifPresent(style::alignContent);
            enumValue(layout, "alignItems", AlignItems.class).ifPresent(style::alignItems);
            enumValue(layout, "alignSelf", AlignItems.class).ifPresent(style::alignSelf);
            enumValue(layout, "justifyItems", AlignItems.class).ifPresent(style::justifyItems);
            enumValue(layout, "justifySelf", AlignItems.class).ifPresent(style::justifySelf);
        });
    }

    private static boolean hasLayoutProperty(JsonObject node) {
        String[] properties = {
            "position", "display", "left", "top", "right", "bottom", "width", "height",
            "padding", "paddingAll", "paddingLeft", "paddingTop", "paddingRight", "paddingBottom",
            "margin", "marginAll", "marginLeft", "marginTop", "marginRight", "marginBottom",
            "gap", "gapAll", "gapRow", "gapColumn", "flexDirection", "flex", "flexGrow",
            "justifyContent", "alignContent", "alignItems", "alignSelf", "justifyItems", "justifySelf"
        };
        for (String property : properties) {
            if (node.has(property)) return true;
        }
        return false;
    }

    private static void applyTexture(UIElement element, JsonObject node, ResourceLocation source, NeoUiJsonContext context) {
        JsonElement texture = node.get("texture");
        if (texture == null || texture.isJsonNull()) {
            return;
        }
        String textureName;
        JsonObject textureObject = texture.isJsonObject() ? texture.getAsJsonObject() : null;
        if (textureObject != null) {
            textureName = string(textureObject, "location").orElseGet(() -> string(textureObject, "texture").orElse(null));
        } else if (texture.isJsonPrimitive() && texture.getAsJsonPrimitive().isString()) {
            textureName = texture.getAsString();
        } else {
            throw error(source, "texture must be a resource location or object", null);
        }
        if (textureName == null || textureName.isBlank()) {
            throw error(source, "texture location is empty", null);
        }
        ResourceLocation textureLocation = parseLocation(textureName, source.getNamespace(), source);
        SpriteTexture sprite = NETextures.panelSprite(textureLocation, context::panelTier);
        JsonElement spriteElement = textureObject == null ? node.get("sprite") : textureObject.get("sprite");
        int[] rectangle = spriteRectangle(spriteElement, source);
        if (rectangle != null) {
            sprite.setSprite(rectangle[0], rectangle[1], rectangle[2], rectangle[3]);
        }
        element.style(style -> style.backgroundTexture(sprite));
    }

    private static int[] spriteRectangle(JsonElement value, ResourceLocation source) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            if (array.size() != 4) {
                throw error(source, "sprite must contain [x, y, width, height]", null);
            }
            return new int[] { integer(array.get(0), source), integer(array.get(1), source),
                integer(array.get(2), source), integer(array.get(3), source) };
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            return new int[] { integer(object, "x", source), integer(object, "y", source),
                integer(object, "width", source), integer(object, "height", source) };
        }
        throw error(source, "sprite must be an array or object", null);
    }

    private static void dimension(JsonObject object, String name,
                                  java.util.function.Consumer<Float> pixels,
                                  java.util.function.Consumer<Float> percent) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) {
            return;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            pixels.accept(value.getAsFloat());
            return;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String string = value.getAsString().trim();
            if (string.endsWith("%")) {
                percent.accept(parseFloat(string.substring(0, string.length() - 1), name));
            } else if ("auto".equalsIgnoreCase(string)) {
                // Omitting a dimension is equivalent to Taffy's auto value.
            } else {
                pixels.accept(parseFloat(string, name));
            }
            return;
        }
        throw new NeoUiJsonException("layout " + name + " must be a number, percent, or auto");
    }

    private static void position(JsonObject object, String name,
                                 java.util.function.Consumer<Float> pixels,
                                 java.util.function.Consumer<Float> percent) {
        dimension(object, name, pixels, percent);
    }

    private static void length(JsonObject object, String name,
                               java.util.function.Consumer<Float> pixels,
                               java.util.function.Consumer<Float> percent) {
        dimension(object, name, pixels, percent);
    }

    private static <T extends Enum<T>> Optional<T> enumValue(JsonObject object, String name, Class<T> type) {
        Optional<String> value = string(object, name);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type,
                value.get().trim().replace('-', '_').toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new NeoUiJsonException("layout " + name + " is invalid: " + value.get(), e);
        }
    }

    private static Optional<String> string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? Optional.of(value.getAsString()) : Optional.empty();
    }

    private static Optional<JsonObject> object(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? Optional.of(value.getAsJsonObject()) : Optional.empty();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
            ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject object, String key, ResourceLocation source) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw error(source, "sprite " + key + " must be a number", null);
        }
        return value.getAsInt();
    }

    private static int integer(JsonElement value, ResourceLocation source) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw error(source, "sprite values must be numbers", null);
        }
        return value.getAsInt();
    }

    private static float parseFloat(String value, String name) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new NeoUiJsonException("layout " + name + " has invalid number: " + value, e);
        }
    }

    private static ResourceLocation withJsonSuffix(ResourceLocation location) {
        return location.getPath().endsWith(".json") ? location
            : ResourceLocation.fromNamespaceAndPath(location.getNamespace(), location.getPath() + ".json");
    }

    private static ResourceLocation parseLocation(String value, String defaultNamespace, ResourceLocation source) {
        try {
            return value.indexOf(':') >= 0 ? ResourceLocation.parse(value)
                : ResourceLocation.fromNamespaceAndPath(defaultNamespace, value);
        } catch (RuntimeException e) {
            throw error(source, "invalid texture resource location: " + value, e);
        }
    }

    private static NeoUiJsonException error(ResourceLocation source, String message, Throwable cause) {
        String detail = "UI " + source + ": " + message;
        return cause == null ? new NeoUiJsonException(detail) : new NeoUiJsonException(detail, cause);
    }
}
