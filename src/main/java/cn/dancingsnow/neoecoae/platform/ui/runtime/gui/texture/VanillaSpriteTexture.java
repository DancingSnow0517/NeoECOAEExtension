package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
/** 1.20.1 has no vanilla GUI sprite atlas; register these sprites in minecraft:blocks. */
public final class VanillaSpriteTexture implements IGuiTexture {
    private final ResourceLocation sprite;
    private VanillaSpriteTexture(ResourceLocation sprite){this.sprite=sprite;}
    public static VanillaSpriteTexture of(ResourceLocation sprite){return new VanillaSpriteTexture(sprite);}
    public ResourceLocation getSprite(){return sprite;}
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @Override public void draw(GuiGraphics g,float mx,float my,float x,float y,float w,float h,float tick){
        var texture=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
        g.blit(Math.round(x),Math.round(y),0,Math.round(w),Math.round(h),texture);
    }
}
