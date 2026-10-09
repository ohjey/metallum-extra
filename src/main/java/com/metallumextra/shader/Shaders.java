package com.metallumextra.shader;

import com.metallumextra.ExtraConfig;
import com.metallumextra.MetallumExtra;
import com.metallumextra.ShaderKeys;
import com.metallumextra.shader.pack.PackManager;
import com.metallumextra.shader.pack.TranslationCache;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The built-in shader pipeline: state for the frame being drawn, and the points in the frame where it does work.
 * <p>
 * How it fits together: the game and Sodium keep their own pipelines, but while shaders are on their shader text
 * is replaced by this mod's ({@link ShaderSources}). Those shaders read one extra uniform block and a few extra
 * textures that no pipeline declares; Metallum's render pass hands them over ({@link ShaderBindings}). The world
 * is still drawn into the game's own image, in display colors, so anything this mod does not replace keeps working
 * and looks the way it always did.
 */
public final class Shaders {
    /** Not drawing the world: menus, the HUD, items in the inventory. Replaced shaders behave exactly like the originals. */
    public static final int PHASE_NONE = 0;
    public static final int PHASE_WORLD = 1;
    /** The held item and arm, drawn after the world with a projection of their own. */
    public static final int PHASE_HAND = 2;
    public static final int PHASE_SHADOW = 3;

    private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
    private static final boolean SHINE = FabricLoader.getInstance().isModLoaded("shine");
    private static final float QUARTER_TURN = (float) (Math.PI / 2.0);

    /** Development aid: {@code -Dmetallumextra.debugView=ao|lightcolor|rays} shows one effect by itself. */
    private static final float DEBUG_VIEW = switch (System.getProperty("metallumextra.debugView", "")) {
        case "ao" -> 1.0F;
        case "lightcolor" -> 2.0F;
        case "rays" -> 3.0F;
        default -> 0.0F;
    };

    private static final ShaderGlobals GLOBALS = new ShaderGlobals();
    private static final ShaderTargets TARGETS = new ShaderTargets();
    private static final LightColorGrid LIGHT_COLORS = new LightColorGrid();

    /** Read by chunk meshing threads too. */
    private static volatile boolean active;
    private static int phase = PHASE_NONE;
    private static boolean drawingWorld;
    /** Whether this dimension has a sun and moon to light it and cast shadows. */
    private static boolean celestialLight;
    private static boolean loggedUnavailable;
    /** Sky light at the camera, 0..1, eased over time so stepping under a roof does not snap. */
    private static float eyeSkyLight = 1.0F;
    private static long eyeSkyLightAt;

    private Shaders() {
    }

    /** Why the shaders cannot be used in this installation, in words for the player; null if they can. */
    public static @Nullable String unavailableReason() {
        if (!SODIUM) return "Needs Sodium, which is not installed.";
        // Shine replaces Sodium's terrain shader too, and draws terrain into images of its own; together the two
        // produce a broken picture.
        if (SHINE && !Boolean.getBoolean("metallumextra.allowShine")) return "Cannot be used together with Shine, which replaces the same terrain shader. Disable Shine to use this.";
        return null;
    }

    /** True while this mod's shaders are the ones being compiled and drawn with. */
    public static boolean active() {
        return active;
    }

    public static int phase() {
        return phase;
    }

    public static ShaderGlobals globals() {
        return GLOBALS;
    }

    public static ShaderTargets targets() {
        return TARGETS;
    }

    public static LightColorGrid lightColors() {
        return LIGHT_COLORS;
    }

    public static boolean celestialLight() {
        return celestialLight;
    }

    /** Whether a shadow map is being drawn this frame. */
    public static boolean castsShadows() {
        return active && GLOBALS.lightDir.w > 0.0F;
    }

    /** Whether the world is being drawn under an open sky, which the shader pipeline then draws itself. */
    public static boolean drawsSky() {
        return active && phase == PHASE_WORLD && GLOBALS.skyHorizon.w == 0.0F;
    }

    /** Start of every frame, before anything is drawn. Applies a change of the on/off setting. */
    public static void beginFrame() {
        // The shaders reach into Metallum's render pass, so they exist on the Metal backend only.
        String reason = unavailableReason();
        if (reason == null && !"Metal".equals(RenderSystem.getDevice().getDeviceInfo().backendName())) {
            reason = "The game is not running on the Metal backend.";
        }
        boolean wanted = ExtraConfig.get().shadersEnabled && reason == null;
        if (ExtraConfig.get().shadersEnabled && !wanted && !loggedUnavailable) {
            loggedUnavailable = true;
            MetallumExtra.LOGGER.warn("[Metallum Extra] Shaders stay off. {}", reason);
        }
        // Not while the game is still loading: until its resources are in, a pipeline thrown away here cannot be
        // compiled again, since the shader text it was built from is not there to ask for yet.
        if (Minecraft.getInstance().isGameLoadFinished() && Minecraft.getInstance().gui.overlay() == null) {
            // A pack chosen in the menu, or put aside because it was broken, takes effect here, between frames.
            boolean packChanged = PackManager.commitPending();
            if (wanted != active) {
                setActive(wanted);
            } else if (packChanged && active) {
                switchPack();
            }
        }
        TranslationCache.logIfSettled();
        ShaderKeys.tick(Minecraft.getInstance());
        if (active) {
            TARGETS.prepare(Minecraft.getInstance().gameRenderer.mainRenderTarget());
            LIGHT_COLORS.ensure();
        }
        DebugScript.tick();
    }

    /** Throws away every compiled pipeline and chunk mesh, since both come out differently with shaders on. */
    private static void setActive(final boolean value) {
        Minecraft minecraft = Minecraft.getInstance();
        active = value;
        phase = PHASE_NONE;
        drawingWorld = false;
        ShaderSources.clear();
        ShaderBindings.forget();
        RenderSystem.getDevice().clearPipelineCache();
        if (value) {
            GLOBALS.upload(PHASE_NONE);
        } else {
            TARGETS.close();
            ShadowPass.close();
            LIGHT_COLORS.close();
        }
        if (minecraft.level != null) {
            minecraft.levelExtractor.allChanged();
        }
        MetallumExtra.LOGGER.info("[Metallum Extra] Shaders {}", value ? "on" : "off");
    }

    /**
     * Another shader pack is in use: every compiled pipeline is thrown away, and the new pack's shaders are compiled as
     * they are first needed. What was converted to Metal text before stays in the disk cache. Chunk meshes are kept;
     * they do not depend on the pack.
     */
    private static void switchPack() {
        ShaderSources.clear();
        ShaderBindings.forget();
        RenderSystem.getDevice().clearPipelineCache();
    }

    /** Asks for the shaders to be read and compiled again; used after a setting that is baked into them changes. */
    public static void reload() {
        if (!active) return;
        RenderSystem.assertOnRenderThread();
        ShaderSources.clear();
        RenderSystem.getDevice().clearPipelineCache();
    }

    /** Just before the level is drawn. {@code projection} is the matrix the level is drawn with. */
    public static void beginWorld(final Matrix4fc projection) {
        if (!active) return;
        computeFrame(projection);
        drawingWorld = true;
        setPhase(PHASE_WORLD);
    }

    /** After the game has set up the frame's passes and before it runs them: no render pass is open. */
    public static void beforeWorldPasses(final FeatureRenderDispatcher.PreparedFrame features) {
        if (!active || !drawingWorld) return;
        ShadowPass.render(features);
    }

    /** Just before translucent terrain: what has been drawn so far is what water reflects and refracts. */
    public static void beforeTranslucentTerrain() {
        if (!active || !drawingWorld || phase != PHASE_WORLD) return;
        TARGETS.copyScene(Minecraft.getInstance().gameRenderer.mainRenderTarget());
    }

    /** The level has been drawn; the held item comes next. */
    public static void afterWorld() {
        if (!active || !drawingWorld) return;
        ExtraConfig config = ExtraConfig.get();
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (config.shaderAmbientOcclusion || (config.shaderSunRays && castsShadows())) {
            // The copy made for water is taken again now, so the effects see the finished world, water included.
            TARGETS.copyScene(main);
            PostPass.render(main);
        }
        if (config.shaderBloom) {
            BloomPass.render(main);
        }
        if (config.shaderSmoothEdges) {
            EdgePass.render(main);
        }
        setPhase(PHASE_HAND);
    }

    public static void endWorld() {
        if (!active || !drawingWorld) return;
        drawingWorld = false;
        setPhase(PHASE_NONE);
        ShadowPass.endFrame();
    }

    static void setPhase(final int value) {
        phase = value;
        GLOBALS.upload(value);
    }

    private static void computeFrame(final Matrix4fc projection) {
        Minecraft minecraft = Minecraft.getInstance();
        ExtraConfig config = ExtraConfig.get();
        GameRenderState state = minecraft.gameRenderer.gameRenderState();
        CameraRenderState camera = state.levelRenderState.cameraRenderState;
        SkyRenderState sky = state.levelRenderState.skyRenderState;
        LightmapRenderState lightmap = state.lightmapRenderState;
        ShaderGlobals g = GLOBALS;

        g.view.set(camera.viewRotationMatrix);
        g.view.invert(g.viewInverse);
        g.projection.set(projection);
        g.projection.invert(g.projectionInverse);

        float rain = minecraft.level == null ? 0.0F : minecraft.level.getRainLevel(1.0F);
        celestialLight = sky.skybox == DimensionType.Skybox.OVERWORLD;

        // The sun and moon ride the same wheel the sky renderer draws them on, tilted towards the south so the sun
        // never stands straight overhead (the sky renderer is tilted the same way; see SkyRendererShaderMixin).
        float tilt = sunPathTilt();
        Vector3f sun = new Vector3f(0, 1, 0).rotateX(sky.sunAngle).rotateY(-QUARTER_TURN).rotateX(tilt);
        Vector3f moon = new Vector3f(0, 1, 0).rotateX(sky.moonAngle).rotateY(-QUARTER_TURN).rotateX(tilt);
        float sunUp = smoothstep(-0.06F, 0.12F, sun.y);
        float moonUp = smoothstep(-0.06F, 0.12F, moon.y);
        float day = smoothstep(-0.12F, 0.25F, sun.y);
        float clear = 1.0F - 0.88F * rain;

        if (!celestialLight) {
            g.lightDir.set(0, 1, 0, 0);
            g.lightColor.set(0, 0, 0, 0);
            g.sunDir.set(0, 1, 0, 0);
        } else if (sun.y > -0.06F) {
            // Low sun is orange and weaker, having more air to come through; high sun is a warm white, which
            // against the blue of the light from the sky is what tells sunlit ground from shade.
            float high = smoothstep(0.0F, 0.4F, sun.y);
            float strength = Mth.lerp(high, 1.9F, 3.3F) * sunUp * clear;
            g.lightDir.set(sun.x, sun.y, sun.z, sunUp * clear);
            g.lightColor.set(strength, Mth.lerp(high, 0.56F, 0.93F) * strength, Mth.lerp(high, 0.30F, 0.80F) * strength, day);
            g.sunDir.set(sun.x, sun.y, sun.z, sunUp);
        } else {
            float strength = 0.34F * moonUp * clear;
            g.lightDir.set(moon.x, moon.y, moon.z, moonUp * clear);
            g.lightColor.set(0.46F * strength, 0.62F * strength, strength, day);
            g.sunDir.set(sun.x, sun.y, sun.z, 0.0F);
        }

        g.moonDir.set(moon.x, moon.y, moon.z, celestialLight ? moonUp : 0.0F);

        // How much of the sky reaches the camera. The sun rays follow it: see effects.fsh.
        long now = System.nanoTime();
        float seconds = Mth.clamp((now - eyeSkyLightAt) / 1.0e9F, 0.0F, 1.0F);
        eyeSkyLightAt = now;
        float skyLightHere = minecraft.level == null ? 1.0F : minecraft.level.getBrightness(LightLayer.SKY, BlockPos.containing(camera.pos)) / 15.0F;
        eyeSkyLight += (skyLightHere - eyeSkyLight) * (1.0F - (float) Math.exp(-seconds * 4.0F));
        g.params3.set(eyeSkyLight, config.shaderSmoothEdges ? 1.0F : 0.0F, 0.0F, 0.0F);
        g.features.set(config.shaderSunRays ? 1.0F : 0.0F, config.shaderAmbientOcclusion ? 1.0F : 0.0F, config.shaderColoredLight ? 1.0F : 0.0F, DEBUG_VIEW);
        if (config.shaderColoredLight && minecraft.level != null) {
            LIGHT_COLORS.tick(minecraft.level, camera.pos, g);
        }

        // The game's own sky and fog colors already follow the time of day, the weather and the biome.
        int skyColor = sky.skyColor;
        ShaderGlobals.setLinear(g.skyZenith, ARGB.redFloat(skyColor), ARGB.greenFloat(skyColor), ARGB.blueFloat(skyColor), 1.0F);
        g.skyZenith.w = sky.starBrightness;
        ShaderGlobals.setLinear(g.skyHorizon, camera.fogData.color.x, camera.fogData.color.y, camera.fogData.color.z, 1.0F);
        // NONE: the camera is in air, not in water, lava or powder snow.
        boolean atmosphere = celestialLight && camera.fogType == FogType.NONE && !camera.entityRenderState.doesMobEffectBlockSky;
        g.skyHorizon.w = atmosphere ? 0.0F : 1.0F;

        int sunset = sky.sunriseAndSunsetColor;
        ShaderGlobals.setLinear(g.sunsetColor, ARGB.redFloat(sunset), ARGB.greenFloat(sunset), ARGB.blueFloat(sunset), 1.0F);
        g.sunsetColor.w = celestialLight ? ARGB.alphaFloat(sunset) : 0.0F;

        // Light from the open sky: the sky's own color with a little of the color taken out (a deep blue light would
        // turn grass and leaves murky), and a floor so that a moonless night is dark blue instead of black.
        float skyRed = g.skyZenith.x * 1.25F + 0.5F * g.skyHorizon.x * day;
        float skyGreen = g.skyZenith.y * 1.25F + 0.5F * g.skyHorizon.y * day;
        float skyBlue = g.skyZenith.z * 1.25F + 0.5F * g.skyHorizon.z * day;
        // At sunrise and sunset the low sun lights the air itself, and that glow reaches the ground too.
        float glow = g.sunsetColor.w * 0.30F;
        skyRed += g.sunsetColor.x * glow;
        skyGreen += g.sunsetColor.y * glow;
        skyBlue += g.sunsetColor.z * glow;
        float skyGray = 0.2126F * skyRed + 0.7152F * skyGreen + 0.0722F * skyBlue;
        g.skyAmbient.set(
                Mth.lerp(0.25F, skyRed, skyGray) * 1.05F + 0.050F,
                Mth.lerp(0.25F, skyGreen, skyGray) * 1.05F + 0.066F,
                Mth.lerp(0.25F, skyBlue, skyGray) * 1.05F + 0.110F,
                rain);

        float flicker = lightmap.blockFactor / 1.5F;
        g.blockLight.set(3.3F * flicker, 1.55F * flicker, 0.58F * flicker, flicker);

        // What the game's light map adds everywhere: the dimension's ambient light (the Nether and the End are lit
        // by nothing else), lifted by the Brightness setting the same way the game lifts it, and night vision.
        float ambientRed = lightmap.ambientColor.x();
        float ambientGreen = lightmap.ambientColor.y();
        float ambientBlue = lightmap.ambientColor.z();
        float strongest = Math.max(ambientRed, Math.max(ambientGreen, ambientBlue));
        if (strongest > 0.0F) {
            float dim = 1.0F - strongest;
            float lifted = Mth.lerp(Mth.clamp(lightmap.brightness, 0.0F, 1.0F), 1.0F, (1.0F - dim * dim * dim * dim) / strongest);
            ambientRed *= lifted;
            ambientGreen *= lifted;
            ambientBlue *= lifted;
        }
        float floor = 0.0035F + 0.022F * lightmap.brightness;
        // A dimension with no sun (the Nether, the End) is lit by its ambient light alone, so that is given more
        // weight there, and the fog's own color is added as the glow of everything around.
        ShaderGlobals.setLinear(g.minAmbient, ambientRed, ambientGreen, ambientBlue, celestialLight ? 1.0F : 2.4F);
        if (!celestialLight) {
            g.minAmbient.x += g.skyHorizon.x * 0.12F;
            g.minAmbient.y += g.skyHorizon.y * 0.12F;
            g.minAmbient.z += g.skyHorizon.z * 0.12F;
        }
        float nightVision = lightmap.nightVisionEffectIntensity * 0.45F;
        g.minAmbient.x += floor + nightVision * lightmap.nightVisionColor.x();
        g.minAmbient.y += floor + nightVision * lightmap.nightVisionColor.y();
        g.minAmbient.z += floor + nightVision * lightmap.nightVisionColor.z();
        g.minAmbient.w = lightmap.darknessEffectScale;

        Vec3 position = camera.pos;
        g.cameraPos.set((float) wrap(position.x), (float) wrap(position.y), (float) wrap(position.z), (float) ((System.nanoTime() / 1.0e9) % 3600.0));

        ShaderTargets targets = TARGETS;
        g.screen.set(targets.width(), targets.height(), 1.0F / targets.width(), 1.0F / targets.height());
        // Glow is the only light of its own kind in a dimension without a sun, so it is let be a little stronger there.
        float bloom = config.shaderBloom ? (celestialLight ? 1.0F : 1.5F) : 0.0F;
        g.params.set(PHASE_WORLD, 1.0F, bloom, 1.0F / targets.shadowResolution());
        g.params2.set(config.shadowDistance * 16.0F, config.shaderWaving ? 1.0F : 0.0F, config.shaderWaterReflections ? 1.0F : 0.0F,
                camera.fogType == FogType.WATER ? 1.0F : 0.0F);

        ShadowPass.prepare(camera, g);
    }

    /** The tilt of the sun's path, in radians: a turn about the east-west axis. */
    public static float sunPathTilt() {
        return (float) Math.toRadians(ExtraConfig.get().sunPathRotation);
    }

    private static float smoothstep(final float from, final float to, final float value) {
        float t = Mth.clamp((value - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static double wrap(final double coordinate) {
        return coordinate - Math.floor(coordinate / 4096.0) * 4096.0;
    }
}
