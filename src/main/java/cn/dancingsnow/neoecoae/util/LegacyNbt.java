package cn.dancingsnow.neoecoae.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Bridges the 1.21.1 {@link CompoundTag} persistence code (kept as-is from main) to the 26.1
 * {@link ValueInput}/{@link ValueOutput} API. The on-disk layout is unchanged: keys written to the legacy
 * tag land directly in the block entity / parent compound.
 */
public final class LegacyNbt {
    private LegacyNbt() {
    }

    /** Returns the compound backing {@code input}. Mutating the result does not affect the input. */
    public static CompoundTag read(ValueInput input) {
        if (input instanceof TagValueInput tagInput) {
            return tagInput.input.copy();
        }
        // Every vanilla/NeoForge save path uses TagValueInput; other inputs carry no legacy data.
        return new CompoundTag();
    }

    /** Merges every key of {@code tag} into {@code output}. */
    public static void write(ValueOutput output, CompoundTag tag) {
        if (output instanceof TagValueOutput tagOutput) {
            tagOutput.output.merge(tag);
            return;
        }
        for (String key : tag.keySet()) {
            Tag value = tag.get(key);
            if (value != null) {
                output.store(key, ExtraCodecs.NBT, value);
            }
        }
    }

    public static void write(ValueOutput output, Consumer<CompoundTag> writer) {
        CompoundTag tag = new CompoundTag();
        writer.accept(tag);
        write(output, tag);
    }

    /** Wraps a legacy compound as a {@link ValueInput} (e.g. for AE2 26.1 APIs that only accept ValueInput). */
    public static ValueInput input(HolderLookup.Provider registries, CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
    }

    /** Runs a {@link ValueOutput} writer and returns the produced compound. */
    public static CompoundTag output(HolderLookup.Provider registries, Consumer<ValueOutput> writer) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        writer.accept(output);
        return output.buildResult();
    }

    /** Registry access for save paths that no longer receive a provider. */
    public static HolderLookup.Provider registries(@Nullable BlockEntity blockEntity) {
        if (blockEntity != null && blockEntity.getLevel() != null) {
            return blockEntity.getLevel().registryAccess();
        }
        return registries();
    }

    public static HolderLookup.Provider registries() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return RegistryAccess.EMPTY;
    }

    // ---- 1.21.1 ItemStack/FluidStack NBT helpers ----

    /** Replacement for 1.21.1 {@code ItemStack#saveOptional(Provider)}. */
    public static Tag saveItem(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
            .result().orElseGet(CompoundTag::new);
    }

    /** Replacement for 1.21.1 {@code ItemStack#save(Provider, Tag)}. */
    public static Tag saveItem(HolderLookup.Provider registries, ItemStack stack, Tag prefix) {
        return ItemStack.CODEC.encode(stack, registries.createSerializationContext(NbtOps.INSTANCE), prefix)
            .result().orElse(prefix);
    }

    /** Replacement for 1.21.1 {@code ItemStack#parseOptional(Provider, CompoundTag)}. */
    public static ItemStack parseItem(HolderLookup.Provider registries, @Nullable Tag tag) {
        if (tag == null || (tag instanceof CompoundTag compound && compound.isEmpty())) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
            .result().orElse(ItemStack.EMPTY);
    }

    /** Replacement for 1.21.1 {@code FluidStack#saveOptional(Provider)}. */
    public static Tag saveFluid(HolderLookup.Provider registries, FluidStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
            .result().orElseGet(CompoundTag::new);
    }

    /** Replacement for 1.21.1 {@code FluidStack#parseOptional(Provider, CompoundTag)}. */
    public static FluidStack parseFluid(HolderLookup.Provider registries, @Nullable Tag tag) {
        if (tag == null || (tag instanceof CompoundTag compound && compound.isEmpty())) {
            return FluidStack.EMPTY;
        }
        return FluidStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
            .result().orElse(FluidStack.EMPTY);
    }
}
