package com.metallumextra.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The uniform block {@code MxGlobals} every shader of this mod can read (see {@code lib/globals.glsl}, which must
 * list the same fields in the same order). One buffer, rewritten whenever the frame moves from one phase to the
 * next: Metallum gives a uniform buffer fresh memory on each full write, so draws already recorded keep the values
 * they were recorded with.
 */
public final class ShaderGlobals {
    public static final String NAME = "MxGlobals";
    private static final int SIZE = 592;

    public final Matrix4f view = new Matrix4f();
    public final Matrix4f viewInverse = new Matrix4f();
    public final Matrix4f projection = new Matrix4f();
    public final Matrix4f projectionInverse = new Matrix4f();
    /** Camera-relative world space to shadow map clip space. */
    public final Matrix4f shadow = new Matrix4f();
    /** xyz: direction to the light that casts shadows (sun or moon). w: shadow strength, 0 when there is no shadow map. */
    public final Vector4f lightDir = new Vector4f(0, 1, 0, 0);
    /** rgb: linear color of that light. w: 1 by day, 0 by night. */
    public final Vector4f lightColor = new Vector4f();
    /** xyz: direction to the sun. w: how much of it is above the horizon. */
    public final Vector4f sunDir = new Vector4f(0, 1, 0, 0);
    /** rgb: linear light from the open sky. w: rain strength. */
    public final Vector4f skyAmbient = new Vector4f();
    /** rgb: linear color of block light at full strength. */
    public final Vector4f blockLight = new Vector4f();
    /** rgb: light that reaches everywhere (dimension ambient, night vision). w: darkness effect. */
    public final Vector4f minAmbient = new Vector4f();
    /** rgb: linear sky color overhead. w: star brightness. */
    public final Vector4f skyZenith = new Vector4f();
    /** rgb: linear sky color at the horizon. w: 0 = fog fades to the sky, 1 = fog fades to the game's fog color. */
    public final Vector4f skyHorizon = new Vector4f();
    /** rgb: linear sunrise or sunset tint. a: its strength. */
    public final Vector4f sunsetColor = new Vector4f();
    /** xyz: camera position, wrapped so it stays precise as a float. w: seconds, wrapped. */
    public final Vector4f cameraPos = new Vector4f();
    /** xy: size of the world image in pixels. zw: one pixel. */
    public final Vector4f screen = new Vector4f(1, 1, 1, 1);
    /** x: phase. y: exposure. z: bloom strength. w: one shadow map texel. */
    public final Vector4f params = new Vector4f();
    /** x: shadow distance in blocks. y: waving on. z: water reflections on. w: 1 while the camera is under water. */
    public final Vector4f params2 = new Vector4f();
    /** xyz: direction to the moon. w: how much of it is above the horizon. */
    public final Vector4f moonDir = new Vector4f(0, -1, 0, 0);
    /** xyz: where the light-color grid starts, camera-relative. w: the size of one of its cells in blocks. */
    public final Vector4f lightGrid = new Vector4f(0, 0, 0, 1);
    /** x: sun rays on. y: ambient occlusion on. z: colored block light on. w: debug view. */
    public final Vector4f features = new Vector4f();
    /** x: sky light where the camera is, 0..1, smoothed over time. y: edge smoothing on. zw: unused. */
    public final Vector4f params3 = new Vector4f();

    private final ByteBuffer data = ByteBuffer.allocateDirect(SIZE).order(ByteOrder.nativeOrder());
    private @Nullable GpuBuffer buffer;

    public GpuBuffer buffer() {
        if (this.buffer == null) {
            this.buffer = RenderSystem.getDevice().createBuffer(() -> "Metallum Extra shader globals", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SIZE);
            this.upload(Shaders.PHASE_NONE);
        }
        return this.buffer;
    }

    /** Writes every field with the given phase. Must not be called while a render pass is open. */
    public void upload(final int phase) {
        GpuBuffer target = this.buffer();
        ByteBuffer out = this.data;
        this.view.get(0, out);
        this.viewInverse.get(64, out);
        this.projection.get(128, out);
        this.projectionInverse.get(192, out);
        this.shadow.get(256, out);
        put(out, 320, this.lightDir);
        put(out, 336, this.lightColor);
        put(out, 352, this.sunDir);
        put(out, 368, this.skyAmbient);
        put(out, 384, this.blockLight);
        put(out, 400, this.minAmbient);
        put(out, 416, this.skyZenith);
        put(out, 432, this.skyHorizon);
        put(out, 448, this.sunsetColor);
        put(out, 464, this.cameraPos);
        put(out, 480, this.screen);
        out.putFloat(496, phase).putFloat(500, this.params.y).putFloat(504, this.params.z).putFloat(508, this.params.w);
        put(out, 512, this.params2);
        put(out, 528, this.moonDir);
        put(out, 544, this.lightGrid);
        put(out, 560, this.features);
        put(out, 576, this.params3);
        out.position(0).limit(SIZE);
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(target.slice(), out);
    }

    public void close() {
        if (this.buffer != null) {
            this.buffer.close();
            this.buffer = null;
        }
    }

    private static void put(final ByteBuffer out, final int offset, final Vector4f value) {
        out.putFloat(offset, value.x).putFloat(offset + 4, value.y).putFloat(offset + 8, value.z).putFloat(offset + 12, value.w);
    }

    static void setLinear(final Vector4f target, final float red, final float green, final float blue, final float scale) {
        target.x = (float) Math.pow(Math.max(red, 0.0F), 2.2) * scale;
        target.y = (float) Math.pow(Math.max(green, 0.0F), 2.2) * scale;
        target.z = (float) Math.pow(Math.max(blue, 0.0F), 2.2) * scale;
    }

    static void setLinear(final Vector4f target, final Vector3f srgb, final float scale) {
        setLinear(target, srgb.x, srgb.y, srgb.z, scale);
    }
}
