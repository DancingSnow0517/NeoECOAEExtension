package cn.dancingsnow.neoecoae.platform.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Forge 1.20 fluid predicates retain tag identity and optional strict/partial NBT matching. */
public final class FluidIngredient {
    private static final int MAX_ALTERNATIVES = 256;
    private static final FluidIngredient EMPTY = new FluidIngredient(List.of());
    private final List<Variant> variants;

    private FluidIngredient(List<Variant> variants) {
        if (variants.size() > MAX_ALTERNATIVES) throw new IllegalArgumentException("Too many fluid alternatives");
        this.variants = List.copyOf(variants);
    }

    public static FluidIngredient empty() { return EMPTY; }

    public static FluidIngredient of(Fluid... fluids) {
        return new FluidIngredient(Arrays.stream(fluids).map(fluid ->
            new Variant(Objects.requireNonNull(fluid), null, null, true)).toList());
    }

    public static FluidIngredient of(TagKey<Fluid> tag) {
        return new FluidIngredient(List.of(new Variant(null, Objects.requireNonNull(tag), null, true)));
    }

    public static FluidIngredient exact(FluidStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("An exact fluid predicate cannot be empty");
        // Empty NBT still means exact absence of NBT rather than an unconstrained fluid match.
        return new FluidIngredient(List.of(new Variant(stack.getFluid(), null,
            stack.hasTag() ? stack.getTag().copy() : new CompoundTag(), true)));
    }

    public boolean isEmpty() { return variants.isEmpty(); }

    public boolean isSimple() { return variants.stream().allMatch(v -> v.nbt == null); }

    public boolean test(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Variant variant : variants) {
            boolean fluidMatches = variant.fluid != null ? stack.getFluid() == variant.fluid
                : stack.getFluid().builtInRegistryHolder().is(variant.tag);
            if (!fluidMatches) continue;
            if (variant.nbt == null) return true;
            CompoundTag actual = stack.hasTag() ? stack.getTag() : new CompoundTag();
            if (variant.strict ? variant.nbt.equals(actual) : NbtUtils.compareNbt(variant.nbt, actual, true)) return true;
        }
        return false;
    }

    public FluidStack[] getStacks() {
        return variants.stream().flatMap(v -> {
            Stream<Fluid> fluids = v.fluid != null ? Stream.of(v.fluid)
                : BuiltInRegistries.FLUID.getTag(v.tag).stream().flatMap(set -> set.stream().map(holder -> holder.value()));
            return fluids.map(fluid -> new FluidStack(fluid, 1, v.nbt == null ? null : v.nbt.copy()));
        }).toArray(FluidStack[]::new);
    }

    public static FluidIngredient fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return empty();
        List<Variant> variants = new ArrayList<>();
        if (element.isJsonArray()) {
            if (element.getAsJsonArray().size() > MAX_ALTERNATIVES) throw new JsonSyntaxException("Too many fluid alternatives");
            for (JsonElement entry : element.getAsJsonArray()) variants.addAll(fromJson(entry).variants);
            return new FluidIngredient(variants);
        }
        JsonObject json = GsonHelper.convertToJsonObject(element, "fluid ingredient");
        if (json.size() == 0) return empty();
        for (String key : json.keySet()) {
            if (!List.of("fluid", "tag", "nbt", "strict").contains(key)) {
                throw new JsonSyntaxException("Unsupported fluid predicate field: " + key);
            }
        }
        if (json.has("fluid") == json.has("tag")) throw new JsonSyntaxException("Expected one fluid or tag");
        CompoundTag nbt = json.has("nbt") ? CraftingHelper.getNBT(json.get("nbt")) : null;
        boolean strict = GsonHelper.getAsBoolean(json, "strict", true);
        Fluid fluid = json.has("fluid") ? RecipeJson.fluid(GsonHelper.getAsString(json, "fluid")) : null;
        TagKey<Fluid> tag = json.has("tag") ? TagKey.create(Registries.FLUID,
            new ResourceLocation(GsonHelper.getAsString(json, "tag"))) : null;
        return new FluidIngredient(List.of(new Variant(fluid, tag, nbt, strict)));
    }

    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (Variant variant : variants) {
            JsonObject json = new JsonObject();
            if (variant.fluid != null) json.addProperty("fluid", BuiltInRegistries.FLUID.getKey(variant.fluid).toString());
            else json.addProperty("tag", variant.tag.location().toString());
            if (variant.nbt != null) {
                json.addProperty("nbt", variant.nbt.toString());
                json.addProperty("strict", variant.strict);
            }
            array.add(json);
        }
        return array.size() == 1 ? array.get(0) : array;
    }

    public void write(FriendlyByteBuf buffer) { buffer.writeUtf(toJson().toString(), 32767); }

    public static FluidIngredient read(FriendlyByteBuf buffer) {
        return fromJson(JsonParser.parseString(buffer.readUtf(32767)));
    }

    private record Variant(Fluid fluid, TagKey<Fluid> tag, CompoundTag nbt, boolean strict) {}
}
