package cn.dancingsnow.neoecoae.api.rendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMaps;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;

public class DelegatedBufferSource extends MultiBufferSource.BufferSource {
    private final AddSectionGeometryEvent.SectionRenderingContext delegate;

    public DelegatedBufferSource(AddSectionGeometryEvent.SectionRenderingContext delegate) {
        super(null, Object2ObjectSortedMaps.emptyMap());
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return delegate.getOrCreateChunkBuffer(renderType);
    }

    @Override
    public void endBatch() {
    }

    @Override
    public void endLastBatch() {
    }

    @Override
    public void endBatch(RenderType renderType) {
    }


}
