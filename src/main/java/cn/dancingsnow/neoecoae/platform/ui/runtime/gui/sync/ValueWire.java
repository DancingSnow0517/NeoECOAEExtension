package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Bounded menu wire values. Decoding happens only through LDLib1's active container route. */
public final class ValueWire {
    private ValueWire() {}
    public static void write(FriendlyByteBuf buffer,Class<?> type,Object value) {
        buffer.writeBoolean(value!=null);if(value==null)return;
        if(type==Integer.class)buffer.writeVarInt((Integer)value);
        else if(type==Boolean.class)buffer.writeBoolean((Boolean)value);
        else if(type==Float.class)buffer.writeFloat((Float)value);
        else if(type==String.class)buffer.writeUtf((String)value,32767);
        else if(Component.class.isAssignableFrom(type))buffer.writeComponent((Component)value);
        else if(Tag.class.isAssignableFrom(type)){var root=new CompoundTag();root.put("value",((Tag)value).copy());buffer.writeNbt(root);}
        else if(type==BlockPos.class)buffer.writeBlockPos((BlockPos)value);
        else if(type==UUID.class)buffer.writeUUID((UUID)value);
        else if(type==ItemStack.class)buffer.writeItem((ItemStack)value);
        else throw new IllegalArgumentException("Unsupported menu wire type "+type);
    }
    public static Object read(FriendlyByteBuf buffer,Class<?> type) {
        if(!buffer.readBoolean())return null;
        if(type==Integer.class)return buffer.readVarInt();
        if(type==Boolean.class)return buffer.readBoolean();
        if(type==Float.class)return buffer.readFloat();
        if(type==String.class)return buffer.readUtf(32767);
        if(Component.class.isAssignableFrom(type))return buffer.readComponent();
        if(Tag.class.isAssignableFrom(type)){var tag=buffer.readNbt();return tag==null?null:tag.get("value");}
        if(type==BlockPos.class)return buffer.readBlockPos();
        if(type==UUID.class)return buffer.readUUID();
        if(type==ItemStack.class)return buffer.readItem();
        throw new IllegalArgumentException("Unsupported menu wire type "+type);
    }
    public static Object copy(Object value) {
        if(value instanceof Tag tag)return tag.copy();if(value instanceof ItemStack stack)return stack.copy();return value;
    }
}
