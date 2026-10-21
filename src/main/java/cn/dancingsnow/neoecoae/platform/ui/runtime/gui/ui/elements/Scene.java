package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.world.level.Level;
import net.minecraft.core.*;
import java.util.*;
import java.util.function.BiConsumer;
public class Scene extends UIElement {
    private SceneWidget scene;
    public Scene createScene(Level world){if(scene!=null){scene.releaseCacheBuffer();removeWidget(scene);}scene=new SceneWidget(0,0,getSizeWidth(),getSizeHeight(),world);addWidget(scene);return this;}
    public Scene setDraggable(boolean v){scene.setDraggable(v);return this;}
    public Scene setScalable(boolean v){scene.setScalable(v);return this;}
    public Scene setRenderSelect(boolean v){scene.setRenderSelect(v);return this;}
    public Scene setRenderFacing(boolean v){scene.setRenderFacing(v);return this;}
    public Scene setShowHoverBlockTips(boolean v){scene.setHoverTips(v);return this;}
    public Scene useCacheBuffer(){scene.useCacheBuffer();return this;}
    public Scene setOnSelected(BiConsumer<BlockPos,Direction> callback){scene.setOnSelected(callback);return this;}
    public Scene setRenderedCore(Collection<BlockPos> positions){scene.setRenderedCore(positions);return this;}
    public SceneWidget getSceneWidget(){return scene;}
    public com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer getRenderer(){return scene.getRenderer();}
    public void needCompileCache(){scene.needCompileCache();}
    @Override public void applyBounds(float x,float y,float w,float h){super.applyBounds(x,y,w,h);if(scene!=null)scene.setSize(new Size(Math.round(w),Math.round(h)));}
}
