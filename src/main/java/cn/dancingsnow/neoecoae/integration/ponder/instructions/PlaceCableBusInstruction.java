package cn.dancingsnow.neoecoae.integration.ponder.instructions;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.parts.CableBusContainer;
import appeng.parts.networking.CablePart;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.WorldModifyInstruction;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PlaceCableBusInstruction extends WorldModifyInstruction {
    private final Consumer<CableBusBlockEntity> modifier;

    public PlaceCableBusInstruction(Selection selection, Consumer<CableBusBlockEntity> blockEntity) {
        super(selection);
        this.modifier = blockEntity;
    }

    @Override
    protected void runModification(Selection selection, PonderScene scene) {
        PonderLevel level = scene.getWorld();
        selection.forEach(blockPos -> {
            level.setBlockAndUpdate(blockPos, AEBlocks.CABLE_BUS.block().defaultBlockState());
            modifier.accept((CableBusBlockEntity) level.getBlockEntity(blockPos));
        });
    }

    @Override
    protected boolean needsRedraw() {
        return true;
    }

    public static Builder builder(Selection selection) {
        return new Builder(selection);
    }

    public static class Builder {
        private final Selection selection;
        private final List<Consumer<CableBusContainer>> consumers = new ArrayList<>();
        private final CompoundTag cableState = new CompoundTag();

        public Builder(Selection selection) {
            this.selection = selection;
        }

        public <T extends IPart> Builder cable(IPartItem<T> partItem) {
            consumers.add(it -> it.addPart(partItem, null, null));
            return this;
        }

        public <T extends IPart> Builder part(IPartItem<T> partItem, Direction side) {
            consumers.add(it -> it.addPart(partItem, side, null));
            return this;
        }

        public Builder cableChannels(int channels) {
            for (Direction value : Direction.values()) {
                cableState.putInt("channels" + StringUtils.capitalize(value.getSerializedName()), channels);
            }
            return this;
        }

        public Builder cableConnect(Direction... sides) {
            ListTag dirs = new ListTag();
            for (Direction side : sides) {
                dirs.add(StringTag.valueOf(side.getSerializedName()));
            }
            cableState.put("connections", dirs);
            return this;
        }

        public Builder powered(boolean powered) {
            cableState.putBoolean("powered", powered);
            return this;
        }

        public Builder applyCableState() {
            consumers.add(it -> {
                IPart part = it.getPart(null);
                if (part instanceof CablePart cablePart) {
                    cablePart.readVisualStateFromNBT(cableState);
                }
            });
            return this;
        }

        public PlaceCableBusInstruction build() {
            return new PlaceCableBusInstruction(
                selection,
                it -> {
                    CableBusContainer cableBus = it.getCableBus();
                    for (Consumer<CableBusContainer> consumer : consumers) {
                        consumer.accept(cableBus);
                    }
                }
            );
        }
    }
}
