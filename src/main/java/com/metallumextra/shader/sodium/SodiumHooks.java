package com.metallumextra.shader.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import org.jspecify.annotations.Nullable;

/** What the mixins add to Sodium's classes so the shadow pass can reach its chunk meshes. */
public final class SodiumHooks {
    private SodiumHooks() {
    }

    /** Added to SodiumWorldRenderer. */
    public interface WorldRenderer {
        @Nullable RenderSectionManager metallumExtra$sections();
    }

    /** Added to DefaultChunkRenderer. */
    public interface ChunkRenderer {
        SharedQuadIndexBuffer metallumExtra$sharedIndexBuffer();
    }
}
