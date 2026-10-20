package cn.dancingsnow.neoecoae.platform.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

/** Rejects unsupported layout properties so a ported screen cannot quietly lose interactions or positioning. */
public final class UiLayoutJson {
    private UiLayoutJson() {}

    public static void apply(UiLayout.Style s, JsonObject layout) {
        for (var entry : layout.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            switch (key) {
                case "width" -> s.width = length(value);
                case "height" -> s.height = length(value);
                case "left" -> s.left = length(value);
                case "top" -> s.top = length(value);
                case "padding", "paddingAll" -> s.paddingLeft = s.paddingRight = s.paddingTop = s.paddingBottom = requiredLength(value);
                case "paddingLeft" -> s.paddingLeft = requiredLength(value);
                case "paddingRight" -> s.paddingRight = requiredLength(value);
                case "paddingTop" -> s.paddingTop = requiredLength(value);
                case "paddingBottom" -> s.paddingBottom = requiredLength(value);
                case "margin", "marginAll" -> s.marginLeft = s.marginRight = s.marginTop = s.marginBottom = requiredLength(value);
                case "marginLeft" -> s.marginLeft = requiredLength(value);
                case "marginRight" -> s.marginRight = requiredLength(value);
                case "marginTop" -> s.marginTop = requiredLength(value);
                case "marginBottom" -> s.marginBottom = requiredLength(value);
                case "gap", "gapAll" -> s.gap = requiredLength(value);
                case "position" -> {
                    String position = value.getAsString();
                    if (!position.equals("relative") && !position.equals("absolute")) throw unsupported(key, value);
                    s.absolute = position.equals("absolute");
                }
                case "display" -> {
                    String display = value.getAsString();
                    if (!display.equals("none") && !display.equals("flex")) throw unsupported(key, value);
                    s.display = !display.equals("none");
                }
                case "flexDirection" -> s.direction = UiLayout.Direction.valueOf(value.getAsString().toUpperCase(Locale.ROOT));
                case "justifyContent" -> {
                    s.justifyContent = align(value);
                    if (s.justifyContent == UiLayout.Align.STRETCH) throw unsupported(key, value);
                }
                case "alignItems" -> s.alignItems = align(value);
                default -> throw unsupported(key, value);
            }
        }
    }

    private static UiLayout.Align align(JsonElement value) {
        String name = value.getAsString().toUpperCase(Locale.ROOT).replace('-', '_');
        if (name.startsWith("FLEX_")) name = name.substring(5);
        return UiLayout.Align.valueOf(name);
    }

    public static UiLayout.Length length(JsonElement value) {
        if (!value.isJsonPrimitive()) throw new IllegalArgumentException("UI length must be a number, percent or auto");
        String text = value.getAsString().trim();
        if (text.equalsIgnoreCase("auto")) return null;
        boolean percent = text.endsWith("%");
        return new UiLayout.Length(Float.parseFloat(percent ? text.substring(0, text.length() - 1) : text), percent);
    }

    private static UiLayout.Length requiredLength(JsonElement value) {
        var length = length(value);
        if (length == null) throw new IllegalArgumentException("Automatic spacing is not implemented by the LDLib1 bridge");
        return length;
    }

    private static IllegalArgumentException unsupported(String key, JsonElement value) {
        return new IllegalArgumentException("Unsupported LDLib1 UI layout: " + key + "=" + value);
    }
}
