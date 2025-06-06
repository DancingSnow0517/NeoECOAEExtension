package cn.dancingsnow.neoecoae.integration.ponder.instructions;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.WorldModifyInstruction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

public class ModifyBlockEntityInstruction<T extends BlockEntity> extends WorldModifyInstruction {
    private final Consumer<T> cons;

    public ModifyBlockEntityInstruction(Selection selection, Consumer<T> cons) {
        super(selection);
        this.cons = cons;
    }

    @Override
    protected void runModification(Selection selection, PonderScene scene) {
        PonderLevel level = scene.getWorld();
        selection.forEach(it -> {
            BlockEntity blockEntity = level.getBlockEntity(it);
            //noinspection unchecked
            cons.accept((T) (blockEntity));
        });
    }

    @Override
    protected boolean needsRedraw() {
        return true;
    }

    public static <T extends BlockEntity> ModifyBlockEntityInstruction<T> of(Selection selection, Consumer<T> cons) {
        return new ModifyBlockEntityInstruction<>(selection, cons);
    }
}
