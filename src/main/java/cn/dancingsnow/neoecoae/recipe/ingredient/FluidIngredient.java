package cn.dancingsnow.neoecoae.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public record FluidIngredient(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag) {
    public static FluidIngredient empty() {
        return new FluidIngredient(null, null);
    }

    public boolean isEmpty() {
        return fluid == null && tag == null;
    }

    public boolean test(FluidStack stack) {
        if (isEmpty()) {
            return true;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (fluid != null) {
            return stack.getFluid() == fluid;
        }
        return stack.getFluid().builtInRegistryHolder().is(tag);
    }

    public static FluidIngredient fromJson(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return empty();
        }
        if (!json.isJsonObject()) {
            throw new JsonParseException("Fluid ingredient must be an object");
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("ingredient")) {
            return fromJson(object.get("ingredient"));
        }
        if (object.has("tag")) {
            return new FluidIngredient(
                    null,
                    TagKey.create(
                            Registries.FLUID,
                            ResourceLocation.parse(object.get("tag").getAsString())));
        }
        String field = object.has("fluid") ? "fluid" : object.has("id") ? "id" : null;
        if (field == null) {
            throw new JsonParseException("Fluid ingredient must contain 'fluid', 'id', or 'tag'");
        }
        ResourceLocation id = ResourceLocation.parse(object.get(field).getAsString());
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid == null || fluid == Fluids.EMPTY) {
            throw new JsonParseException("Unknown fluid '" + id + "'");
        }
        return new FluidIngredient(fluid, null);
    }

    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        if (tag != null) {
            object.addProperty("tag", tag.location().toString());
        } else if (fluid != null) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
            if (id == null) {
                return object;
            }
            object.addProperty("fluid", id.toString());
        }
        return object;
    }

    public static FluidIngredient fromNetwork(FriendlyByteBuf buffer) {
        int mode = buffer.readByte();
        return switch (mode) {
            case 0 -> empty();
            case 1 -> {
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(buffer.readResourceLocation());
                yield fluid == null || fluid == Fluids.EMPTY ? empty() : new FluidIngredient(fluid, null);
            }
            case 2 -> new FluidIngredient(null, TagKey.create(Registries.FLUID, buffer.readResourceLocation()));
            default -> throw new IllegalArgumentException("Unknown fluid ingredient network mode: " + mode);
        };
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        if (isEmpty()) {
            buffer.writeByte(0);
            return;
        }
        if (fluid != null) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
            buffer.writeByte(1);
            buffer.writeResourceLocation(id == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "empty") : id);
            return;
        }
        buffer.writeByte(2);
        buffer.writeResourceLocation(tag.location());
    }

    /** Returns FluidStack array for JEI/EMI display. Tags are expanded to matching fluids. */
    public FluidStack[] getFluids() {
        if (fluid != null) {
            return new FluidStack[] {new FluidStack(fluid, 1000)};
        }
        if (tag != null) {
            return ForgeRegistries.FLUIDS.getValues().stream()
                    .filter(f -> f != Fluids.EMPTY && f.builtInRegistryHolder().is(tag))
                    .map(f -> new FluidStack(f, 1000))
                    .toArray(FluidStack[]::new);
        }
        return new FluidStack[0];
    }
}
