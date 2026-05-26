package cn.dancingsnow.neoecoae.compat.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public record FluidIngredient(Fluid fluid) {
    public static FluidIngredient empty() {
        return new FluidIngredient(Fluids.EMPTY);
    }

    public boolean isEmpty() {
        return fluid == Fluids.EMPTY;
    }

    public boolean test(FluidStack stack) {
        return isEmpty() || (!stack.isEmpty() && stack.getFluid() == fluid);
    }

    public static FluidIngredient fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return empty();
        }
        JsonObject object = json.getAsJsonObject();
        if (!object.has("fluid")) {
            return empty();
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(object.get("fluid").getAsString()));
        return new FluidIngredient(fluid == null ? Fluids.EMPTY : fluid);
    }

    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        if (id != null) {
            object.addProperty("fluid", id.toString());
        }
        return object;
    }

    public static FluidIngredient fromNetwork(FriendlyByteBuf buffer) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(buffer.readResourceLocation());
        return new FluidIngredient(fluid == null ? Fluids.EMPTY : fluid);
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        buffer.writeResourceLocation(id == null ? new ResourceLocation("minecraft", "empty") : id);
    }
}
