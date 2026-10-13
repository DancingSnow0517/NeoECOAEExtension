package cn.dancingsnow.neoecoae.gui.json;

import com.google.gson.JsonObject;

/** Lenient scalar attribute lookup for ECO UI JSON nodes: any JSON primitive is read as text. */
public final class JsonAttrs {
    private JsonAttrs() {}

    public static String string(JsonObject node, String key, String fallback) {
        return node.has(key) && node.get(key).isJsonPrimitive() ? node.get(key).getAsString() : fallback;
    }
}
