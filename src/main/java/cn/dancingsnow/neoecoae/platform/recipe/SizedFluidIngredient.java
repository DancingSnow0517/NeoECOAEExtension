package cn.dancingsnow.neoecoae.platform.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import java.util.Objects;

public record SizedFluidIngredient(FluidIngredient ingredient, int amount) {
    public SizedFluidIngredient {
        Objects.requireNonNull(ingredient);
        if (amount <= 0) throw new IllegalArgumentException("Fluid ingredient amount must be positive");
    }

    public static SizedFluidIngredient of(Fluid fluid, int amount) {
        return new SizedFluidIngredient(FluidIngredient.of(fluid), amount);
    }

    public static SizedFluidIngredient of(TagKey<Fluid> tag, int amount) {
        return new SizedFluidIngredient(FluidIngredient.of(tag), amount);
    }

    public static SizedFluidIngredient of(FluidStack stack) {
        return new SizedFluidIngredient(FluidIngredient.exact(stack), stack.getAmount());
    }

    public boolean test(FluidStack stack) { return ingredient.test(stack) && stack.getAmount() >= amount; }

    public FluidStack[] getFluids() {
        return java.util.Arrays.stream(ingredient.getStacks()).map(stack -> {
            var display = stack.copy();
            display.setAmount(amount);
            return display;
        }).toArray(FluidStack[]::new);
    }

    public static SizedFluidIngredient fromJson(JsonElement element) {
        JsonObject json = GsonHelper.convertToJsonObject(element, "sized fluid ingredient");
        JsonObject predicate = json.deepCopy();
        predicate.remove("amount");
        return new SizedFluidIngredient(FluidIngredient.fromJson(predicate.has("ingredient") ? predicate.get("ingredient") : predicate),
            GsonHelper.getAsInt(json, "amount", 1));
    }

    public JsonObject toJson() {
        JsonElement predicate = ingredient.toJson();
        JsonObject json = predicate.isJsonObject() ? predicate.getAsJsonObject().deepCopy() : new JsonObject();
        if (!predicate.isJsonObject() && !ingredient.isEmpty()) json.add("ingredient", predicate);
        json.addProperty("amount", amount);
        return json;
    }

    public void write(FriendlyByteBuf buffer) {
        ingredient.write(buffer);
        buffer.writeVarInt(amount);
    }

    public static SizedFluidIngredient read(FriendlyByteBuf buffer) {
        return new SizedFluidIngredient(FluidIngredient.read(buffer), buffer.readVarInt());
    }
}
