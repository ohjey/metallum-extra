package com.metallumextra.mixin.shader;

import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Shaders: Sodium's own routine for turning a region's list of sections into draw commands, reused for the shadow pass. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public interface SodiumChunkRendererInvoker {
    @Invoker("fillCommandBuffer")
    static void metallumExtra$fillCommandBuffer(final MultiDrawBatch batch, final RenderRegion region, final SectionRenderDataStorage storage, final ChunkRenderList list,
                                                final CameraTransform camera, final TerrainRenderPass pass, final boolean useBlockFaceCulling, final boolean useIndexedTessellation) {
        throw new AssertionError();
    }
}
