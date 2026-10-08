package com.metallumextra.mixin.shader;

import com.metallumextra.shader.sodium.SodiumHooks;
import net.caffeinemc.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Shaders: the shadow pass draws Sodium's chunk meshes itself and needs the index buffer they all share. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumChunkRendererShaderMixin implements SodiumHooks.ChunkRenderer {
    @Shadow @Final private SharedQuadIndexBuffer sharedIndexBuffer;

    @Override
    public SharedQuadIndexBuffer metallumExtra$sharedIndexBuffer() {
        return this.sharedIndexBuffer;
    }
}
