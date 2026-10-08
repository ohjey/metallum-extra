package com.metallumextra.mixin.shader;

import com.metallumextra.shader.BlockTypes;
import com.metallumextra.shader.Shaders;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Shaders: where Sodium packs a quad into the chunk mesh, the kind of block it came from is written into the
 * alpha byte of each vertex's color (see {@link BlockTypes}). Only while shaders are on: Sodium's own terrain
 * shader multiplies that alpha into the pixel, and the chunks are rebuilt whenever shaders are switched.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder", remap = false)
public abstract class SodiumMeshBufferShaderMixin {
    @Redirect(method = "push([Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;I)V",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder;write(JI[Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;I)J"))
    private long metallumExtra$tagQuad(final ChunkVertexEncoder encoder, final long pointer, final int materialBits, final ChunkVertexEncoder.Vertex[] vertices, final int section) {
        if (Shaders.active()) {
            int alpha = BlockTypes.current() << 24;
            for (ChunkVertexEncoder.Vertex vertex : vertices) {
                vertex.color = (vertex.color & 0x00FFFFFF) | alpha;
            }
        }
        return encoder.write(pointer, materialBits, vertices, section);
    }
}
