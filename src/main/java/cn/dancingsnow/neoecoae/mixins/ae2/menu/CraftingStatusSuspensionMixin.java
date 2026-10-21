package cn.dancingsnow.neoecoae.mixins.ae2.menu;
import appeng.menu.me.crafting.CraftingStatus;
import cn.dancingsnow.neoecoae.api.ECOCraftingStatusSuspension;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=CraftingStatus.class,remap=false)
public abstract class CraftingStatusSuspensionMixin implements ECOCraftingStatusSuspension {
    @Unique private boolean neoecoae$suspended;
    public boolean neoecoae$isSuspended(){return neoecoae$suspended;}
    public void neoecoae$setSuspended(boolean value){neoecoae$suspended=value;}
    @Inject(method="write",at=@At("TAIL"))
    private void neoecoae$write(FriendlyByteBuf buffer,CallbackInfo ci){buffer.writeBoolean(neoecoae$suspended);}
    @Inject(method="read",at=@At("RETURN"))
    private static void neoecoae$read(FriendlyByteBuf buffer,CallbackInfoReturnable<CraftingStatus> cir){((ECOCraftingStatusSuspension)cir.getReturnValue()).neoecoae$setSuspended(buffer.readBoolean());}
}
