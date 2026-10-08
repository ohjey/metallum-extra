package com.metallumextra.shader;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The uniform block and textures this mod's shaders read without any pipeline declaring them.
 * <p>
 * The pipelines being drawn belong to the game, to Sodium or to other mods, and their draw code binds only what
 * they declared. So the extra names are let through when a shader is compiled, and Metallum's render pass binds
 * them itself for any pipeline whose shaders turned out to use them.
 */
public final class ShaderBindings {
    public static final String SHADOW_MAP = "MxShadowMap";
    public static final String SCENE_COLOR = "MxSceneColor";
    public static final String SCENE_DEPTH = "MxSceneDepth";

    /** In mask order: bit 0 is the uniform block, then the textures. */
    public static final String[] NAMES = {ShaderGlobals.NAME, SHADOW_MAP, SCENE_COLOR, SCENE_DEPTH, LightColorGrid.SAMPLER};

    /** The same masks, by the game's own pipeline object, for when only that is at hand. */
    private static final Map<RenderPipeline, Integer> MASKS = new IdentityHashMap<>();
    private static final Identifier BLOB_SHADOW = Identifier.withDefaultNamespace("pipeline/entity_shadow");

    private static final int GLOBALS = 1;
    private static final int SHADOW = 1 << 1;
    private static final int COLOR = 1 << 2;
    private static final int DEPTH = 1 << 3;
    private static final int LIGHT_COLORS = 1 << 4;

    private ShaderBindings() {
    }

    /** Added to Metallum's compiled pipeline: which of {@link #NAMES} its shaders use, as a bit mask. */
    public interface Pipeline {
        int metallumExtra$shaderBindings();
    }

    public static void record(final RenderPipeline pipeline, final int mask) {
        MASKS.put(pipeline, mask);
    }

    public static void forget() {
        MASKS.clear();
    }

    /**
     * Whether a pass should drop its draws with this pipeline.
     * <p>
     * While the shadow map is drawn, the game's own draw code runs a second time with its output pointed at the
     * map. Only shaders that know about the shadow phase put their vertices where the light sees them; anything
     * else (text, lines, beams) would scribble over the map from the camera's point of view.
     * And once the sun casts real shadows, the round blob the game draws under each mob is left out.
     */
    public static boolean skipsDraws(final RenderPipeline pipeline) {
        int phase = Shaders.phase();
        if (phase == Shaders.PHASE_SHADOW) {
            return (MASKS.getOrDefault(pipeline, 0) & GLOBALS) == 0;
        }
        return phase == Shaders.PHASE_WORLD && Shaders.castsShadows() && pipeline.getLocation().equals(BLOB_SHADOW);
    }

    public static void addUniforms(final List<BindGroupLayout.UniformDescription> uniforms) {
        if (Shaders.active()) uniforms.add(new BindGroupLayout.UniformDescription(ShaderGlobals.NAME, UniformType.UNIFORM_BUFFER));
    }

    public static void addSamplers(final List<String> samplers) {
        if (!Shaders.active()) return;
        for (int i = 1; i < NAMES.length; i++) samplers.add(NAMES[i]);
    }

    /** Called by the render pass when it switches to a pipeline that uses any of the extra names. */
    public static void bind(final RenderPassBackend pass, final int mask) {
        ShaderTargets targets = Shaders.targets();
        if ((mask & GLOBALS) != 0) pass.setUniform(ShaderGlobals.NAME, Shaders.globals().buffer());
        if (targets.scene() == null) return;
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        if ((mask & SHADOW) != 0) {
            // A texture cannot be read while it is being drawn into.
            TextureTarget shadow = Shaders.phase() == Shaders.PHASE_SHADOW ? targets.emptyShadow() : targets.shadow();
            pass.bindTexture(SHADOW_MAP, shadow.getDepthTextureView(), nearest);
        }
        if ((mask & COLOR) != 0) pass.bindTexture(SCENE_COLOR, targets.scene().getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if ((mask & DEPTH) != 0) pass.bindTexture(SCENE_DEPTH, targets.scene().getDepthTextureView(), nearest);
        if ((mask & LIGHT_COLORS) != 0) pass.bindTexture(LightColorGrid.SAMPLER, Shaders.lightColors().view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
}
