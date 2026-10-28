package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture;

import cn.dancingsnow.neoecoae.platform.ui.runtime.math.Size;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Retains pixel atlas rectangles and nine-slice borders, with the real LDLib1 texture renderer. */
public class SpriteTexture implements IGuiTexture {
    public enum WrapMode { STRETCH, REPEAT }
    public static final class Point {
        public float x, y;
        public Point(float x, float y) { this.x=x; this.y=y; }
        public int getX() { return Math.round(x); }
        public int getY() { return Math.round(y); }
    }
    public Point spritePosition = new Point(0, 0), borderLT = new Point(0, 0), borderRB = new Point(0, 0);
    public Size spriteSize = Size.of(0, 0);
    protected int color = -1;
    protected WrapMode wrapMode = WrapMode.STRETCH;
    private ResourceLocation image;
    private float rotation, scale = 1, offsetX, offsetY;
    private static final java.util.Map<ResourceLocation, int[]> DIMENSIONS = new java.util.HashMap<>();
    public static void clearDimensionCache() { DIMENSIONS.clear(); }
    public static SpriteTexture of(ResourceLocation location) { return new SpriteTexture().setImageLocation(location); }
    public static SpriteTexture of(String location) { return of(new ResourceLocation(location)); }
    public SpriteTexture setImageLocation(ResourceLocation location) { image=location; return this; }
    public ResourceLocation getImageLocation() { return image; }
    public SpriteTexture setSprite(float x, float y, float width, float height) {
        spritePosition = new Point(x,y); spriteSize = Size.of((int)width,(int)height); return this;
    }
    public SpriteTexture setSpriteSize(Size value) { spriteSize = value; return this; }
    public SpriteTexture setBorder(float value) { return setBorder(value,value,value,value); }
    public SpriteTexture setBorder(float left,float top,float right,float bottom) {
        borderLT = new Point(left,top); borderRB = new Point(right,bottom); return this;
    }
    public SpriteTexture setColor(int value) { color=value; return this; }
    public int getColor() { return color; }
    public SpriteTexture setWrapMode(WrapMode value) { wrapMode=value; return this; }
    public WrapMode getWrapMode() { return wrapMode; }
    public SpriteTexture rotate(float angle) { rotation=angle; return this; }
    public SpriteTexture scale(float factor) { scale=factor; return this; }
    public SpriteTexture transform(float x,float y) { offsetX=x; offsetY=y; return this; }
    public void copyTransform(SpriteTexture source) { rotation=source.rotation; scale=source.scale; offsetX=source.offsetX; offsetY=source.offsetY; }
    @Override public SpriteTexture copy() {
        var result=of(image).setSprite(spritePosition.x,spritePosition.y,spriteSize.width,spriteSize.height)
            .setBorder(borderLT.x,borderLT.y,borderRB.x,borderRB.y).setColor(color).setWrapMode(wrapMode);
        result.copyTransform(this); return result;
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @Override public void draw(GuiGraphics graphics,float mouseX,float mouseY,float x,float y,float width,float height,float partialTicks) {
        graphics.pose().pushPose();
        graphics.pose().translate(x+width/2+offsetX,y+height/2+offsetY,0);
        graphics.pose().scale(scale,scale,1);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));
        drawInternal(graphics,mouseX,mouseY,-width/2,-height/2,width,height,partialTicks);
        graphics.pose().popPose();
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics,float mouseX,float mouseY,float x,float y,float width,float height,float partialTicks) {
        if (image == null || width <= 0 || height <= 0) return;
        int[] dimensions=DIMENSIONS.computeIfAbsent(image, location -> {
            try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).open();
                var input=javax.imageio.ImageIO.createImageInputStream(stream)) {
                var readers=javax.imageio.ImageIO.getImageReaders(input);
                if(!readers.hasNext())throw new java.io.IOException("Unsupported image");
                var reader=readers.next();
                try { reader.setInput(input); return new int[]{reader.getWidth(0),reader.getHeight(0)}; }
                finally { reader.dispose(); }
            } catch(java.io.IOException ex) { throw new IllegalStateException("Cannot read sprite atlas "+location,ex); }
        });
        int atlasWidth=dimensions[0],atlasHeight=dimensions[1];
        float sw=spriteSize.width>0?spriteSize.width:atlasWidth;
        float sh=spriteSize.height>0?spriteSize.height:atlasHeight;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f);
        try {
            float factorX=Math.min(1,sw/Math.max(1,borderLT.x+borderRB.x));
            float factorY=Math.min(1,sh/Math.max(1,borderLT.y+borderRB.y));
            float l=borderLT.x*factorX,r=borderRB.x*factorX,t=borderLT.y*factorY,b=borderRB.y*factorY;
            float dx=Math.min(1,width/Math.max(1,l+r)),dy=Math.min(1,height/Math.max(1,t+b));
            float[] us={0,l,sw-r,sw}, vs={0,t,sh-b,sh};
            float[] xs={x,x+l*dx,x+width-r*dx,x+width},ys={y,y+t*dy,y+height-b*dy,y+height};
            for(int row=0;row<3;row++) for(int col=0;col<3;col++) {
                float uw=us[col+1]-us[col],vh=vs[row+1]-vs[row];
                float dw=xs[col+1]-xs[col],dh=ys[row+1]-ys[row];
                if(uw<=0||vh<=0||dw<=0||dh<=0)continue;
                var tex=new ResourceTexture(image,(spritePosition.x+us[col])/atlasWidth,
                    (spritePosition.y+vs[row])/atlasHeight,uw/atlasWidth,vh/atlasHeight);
                if(wrapMode==WrapMode.REPEAT&&(row==1||col==1)) {
                    float tw=col==1?uw:dw,th=row==1?vh:dh;
                    for(float yy=0;yy<dh;yy+=th)for(float xx=0;xx<dw;xx+=tw){
                        float cw=Math.min(tw,dw-xx),ch=Math.min(th,dh-yy);
                        tex.drawSubArea(graphics,xs[col]+xx,ys[row]+yy,cw,ch,0,0,cw/tw,ch/th);
                    }
                } else tex.draw(graphics,(int)mouseX,(int)mouseY,xs[col],ys[row],Math.round(dw),Math.round(dh));
            }
        } finally { RenderSystem.setShaderColor(1,1,1,1); }
    }
}
