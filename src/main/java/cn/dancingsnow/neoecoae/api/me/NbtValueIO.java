package cn.dancingsnow.neoecoae.api.me;

import java.util.function.Consumer;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

final class NbtValueIO {
    private NbtValueIO() {
    }

    static ValueInput input(HolderLookup.Provider registries, CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
    }

    static TagValueOutput output(HolderLookup.Provider registries) {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
    }

    static CompoundTag write(HolderLookup.Provider registries, Consumer<ValueOutput> writer) {
        TagValueOutput output = output(registries);
        writer.accept(output);
        return output.buildResult();
    }
}
