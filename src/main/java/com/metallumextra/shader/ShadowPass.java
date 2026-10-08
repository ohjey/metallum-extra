package com.metallumextra.shader;

import com.metallumextra.ExtraConfig;
import com.metallumextra.shader.sodium.ShadowTerrain;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

/** The shadow map: the world drawn once more from the sun's (or moon's) point of view, keeping only depth. */
public final class ShadowPass {
    private static final Vector4f CLEAR_COLOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    /** The sun and moon travel east to west, so north is always at right angles to their light. */
    private static final Vector3f NORTH = new Vector3f(0.0F, 0.0F, -1.0F);

    private static final Matrix4f LIGHT_VIEW = new Matrix4f();
    private static final Vector3f TO_LIGHT = new Vector3f();
    private static Vec3 camera = Vec3.ZERO;
    private static float range;
    private static float depth;
    private static boolean wanted;

    private static final SubmitNodeStorage PLAYER_SUBMITS = new SubmitNodeStorage();
    private static @Nullable RenderBuffers playerBuffers;
    private static @Nullable FeatureRenderDispatcher playerFeatures;

    private ShadowPass() {
    }

    /** Works out this frame's shadow matrix. Sets the shadow strength to zero when no map will be drawn. */
    static void prepare(final CameraRenderState cameraState, final ShaderGlobals globals) {
        wanted = ExtraConfig.get().shaderShadows && Shaders.celestialLight() && globals.lightDir.w > 0.0F;
        if (!wanted) {
            globals.shadow.identity();
            globals.lightDir.w = 0.0F;
            return;
        }

        camera = cameraState.pos;
        range = ExtraConfig.get().shadowDistance * 16.0F;
        // Deep enough for anything that can cast a shadow into the map from above at the angles the sun is strong at.
        depth = range * 1.5F + 64.0F;
        TO_LIGHT.set(globals.lightDir.x, globals.lightDir.y, globals.lightDir.z).normalize();

        // Light space: looking along the light, so z grows towards the light.
        LIGHT_VIEW.setLookAlong(-TO_LIGHT.x, -TO_LIGHT.y, -TO_LIGHT.z, NORTH.x, NORTH.y, NORTH.z);

        // x and y: -1..1 across the map. z: 1 nearest the light, 0 furthest, like the game's own depth.
        // The camera sits exactly in the middle, which is where the map's fish-eye bend is centered. (A flat
        // shadow map is usually moved in whole texels to keep edges still; on a bent one that makes the picture
        // shake instead, because the bend then wobbles around the camera and jumps back a texel at a time as the
        // sun turns.)
        globals.shadow.translation(0.0F, 0.0F, 0.5F)
                .scale(1.0F / range, 1.0F / range, 0.5F / depth)
                .mul(LIGHT_VIEW);
    }

    /** @param features everything the game is about to draw this frame besides terrain: mobs, block entities, items */
    static void render(final FeatureRenderDispatcher.PreparedFrame features) {
        if (!wanted) return;
        RenderTarget target = Shaders.targets().shadow();
        Shaders.setPhase(Shaders.PHASE_SHADOW);
        clear(target);
        ShadowTerrain.render(target, camera, LIGHT_VIEW, TO_LIGHT, range, depth);

        // The game's own draw code for models, run once more with its output pointed at the shadow map. The
        // replaced model shaders place their vertices where the light sees them in this phase, and
        // ShaderBindings.skipsDraws drops whatever is drawn with any other shader (text, lines, outlines).
        // The translucent phase matters: a player's skin is drawn as a translucent model, for its outer layer.
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        try {
            features.executeSolid();
            features.executeTranslucent();
            drawPlayer();
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
        }
        Shaders.setPhase(Shaders.PHASE_WORLD);
    }

    /**
     * In first person the game never draws the player, so it is not among the models above and would cast no
     * shadow. Here the player is extracted and drawn the way any mob is, into the shadow map only, with a
     * feature dispatcher of this mod's own: the game's is busy with the frame being drawn.
     */
    private static void drawPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameRenderer.mainCamera().isDetached() || player.isSleeping()) return;
        if (playerFeatures == null) {
            playerBuffers = new RenderBuffers(0);
            playerFeatures = new FeatureRenderDispatcher(playerBuffers, minecraft.getModelManager(), minecraft.getAtlasManager(), minecraft.font, minecraft.gameRenderer.gameRenderState());
        }
        EntityRenderDispatcher entities = minecraft.levelRenderer.entityRenderDispatcher();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        EntityRenderState state = entities.extractEntity(player, partialTick);
        CameraRenderState cameraState = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Vec3 cameraPos = cameraState.pos;
        entities.submit(state, cameraState, state.x - cameraPos.x, state.y - cameraPos.y, state.z - cameraPos.z, new PoseStack(), PLAYER_SUBMITS);
        playerFeatures.renderAllFeatures(PLAYER_SUBMITS);
    }

    /** End of the frame: the player's vertex buffers are rotated the way the game rotates its own. */
    static void endFrame() {
        if (playerBuffers != null) playerBuffers.endFrame();
    }

    /**
     * Empties a shadow map: nothing in it casts a shadow. Done as a real pass and not through the game's clear
     * call, which Metallum only notes down and carries out when the texture is next used, possibly in the middle
     * of someone else's pass.
     */
    static void clear(final RenderTarget shadow) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Metallum Extra shadow clear", shadow.getColorTextureView(), Optional.of(CLEAR_COLOR),
                shadow.getDepthTextureView(), OptionalDouble.of(0.0))) {
        }
    }

    static void close() {
        ShadowTerrain.close();
        if (playerFeatures != null) {
            playerFeatures.close();
            playerFeatures = null;
        }
        if (playerBuffers != null) {
            playerBuffers.close();
            playerBuffers = null;
        }
    }
}
