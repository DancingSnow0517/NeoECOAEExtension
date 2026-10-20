package cn.dancingsnow.neoecoae.platform.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;

/** Explicit 1.20 recipe boundaries; large stack counts never pass through ItemStack's byte Count. */
public final class RecipeJson {
    private RecipeJson() {}

    public static ItemStack item(JsonElement value) {
        if (value == null || value.isJsonNull()) return ItemStack.EMPTY;
        JsonObject json = GsonHelper.convertToJsonObject(value, "item stack");
        if (json.size() == 0) return ItemStack.EMPTY;
        if (json.has("components")) throw new JsonSyntaxException("1.21 item components need an explicit NBT conversion");
        String id = GsonHelper.getAsString(json, json.has("item") ? "item" : "id");
        var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(id))
            .orElseThrow(() -> new JsonSyntaxException("Unknown recipe item " + id));
        int count = GsonHelper.getAsInt(json, "count", 1);
        if (count <= 0) throw new JsonSyntaxException("Item output count must be positive");
        ItemStack stack = new ItemStack(item, count);
        if (stack.isEmpty()) throw new JsonSyntaxException("Recipe output cannot be air");
        if (json.has("nbt")) stack.setTag(CraftingHelper.getNBT(json.get("nbt")));
        return stack;
    }

    public static JsonObject item(ItemStack stack) {
        JsonObject json = new JsonObject();
        if (!stack.isEmpty()) {
            json.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            json.addProperty("count", stack.getCount());
            if (stack.hasTag()) json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }

    public static Fluid fluid(String id) {
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(new ResourceLocation(id))
            .orElseThrow(() -> new JsonSyntaxException("Unknown recipe fluid " + id));
        if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) throw new JsonSyntaxException("Empty recipe fluid");
        return fluid;
    }

    public static FluidStack fluid(JsonElement value) {
        if (value == null || value.isJsonNull()) return FluidStack.EMPTY;
        JsonObject json = GsonHelper.convertToJsonObject(value, "fluid stack");
        if (json.size() == 0) return FluidStack.EMPTY;
        if (json.has("components")) throw new JsonSyntaxException("1.21 fluid components need an explicit NBT conversion");
        Fluid fluid = fluid(GsonHelper.getAsString(json, json.has("fluid") ? "fluid" : "id"));
        int amount = GsonHelper.getAsInt(json, "amount");
        if (amount <= 0) throw new JsonSyntaxException("Fluid output amount must be positive");
        FluidStack stack = new FluidStack(fluid, amount);
        if (json.has("nbt")) stack.setTag(CraftingHelper.getNBT(json.get("nbt")));
        return stack;
    }

    public static JsonObject fluid(FluidStack stack) {
        JsonObject json = new JsonObject();
        if (!stack.isEmpty()) {
            json.addProperty("fluid", BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
            json.addProperty("amount", stack.getAmount());
            if (stack.hasTag()) json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }

    public static void writeItem(FriendlyByteBuf buffer, ItemStack stack) {
        buffer.writeVarInt(stack.isEmpty() ? 0 : stack.getCount());
        if (!stack.isEmpty()) buffer.writeItem(stack.copyWithCount(1));
    }

    public static ItemStack readItem(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0) throw new IllegalArgumentException("Negative item count");
        if (count == 0) return ItemStack.EMPTY;
        ItemStack stack = buffer.readItem();
        if (stack.isEmpty()) throw new IllegalArgumentException("Nonzero item count without an item");
        stack.setCount(count);
        return stack;
    }
}
