package com.metallumextra.shader.sodium;

import com.metallumextra.MetallumExtra;
import com.metallumextra.mixin.shader.SodiumChunkRendererInvoker;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws Sodium's chunk meshes into the shadow map.
 * <p>
 * Sodium only keeps a list of the sections the player's camera can see. The sun sees others: behind the player,
 * off to the side, hidden behind a hill. So this builds its own list of every built section near the player and
 * draws those with Sodium's own machinery (its meshes, its shared index buffer, its routine for turning a list
 * of sections into draw commands) but a pipeline and a pass of its own.
 */
public final class ShadowTerrain {
    /** A section is 16 blocks across; this is a little more than the distance from its middle to a corner. */
    private static final float SECTION_RADIUS = 14.0F;
    /** Lists unused for this many frames are dropped; their regions have most likely been unloaded. */
    private static final int LIST_LIFETIME = 600;

    private static final BindGroupLayout BLOCK_TEXTURE = BindGroupLayout.builder().withSampler("u_BlockTex").build();
    private static final RenderPipeline SOLID = pipeline("shadow_terrain_solid", false);
    private static final RenderPipeline CUTOUT = pipeline("shadow_terrain_cutout", true);

    private static final Reference2ObjectOpenHashMap<RenderRegion, RegionList> LISTS = new Reference2ObjectOpenHashMap<>();
    private static final ObjectArrayList<RegionList> VISIBLE = new ObjectArrayList<>();
    private static @Nullable DrawContext drawContext;
    private static @Nullable MultiDrawBatch batch;
    private static int frame;

    private ShadowTerrain() {
    }

    private static final class RegionList {
        final ChunkRenderList list;
        int lastUsed;

        RegionList(final RenderRegion region) {
            this.list = new ChunkRenderList(region);
        }
    }

    private static RenderPipeline pipeline(final String name, final boolean cutout) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "shadow_terrain"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "shadow_terrain"))
                // Which faces to draw is decided per section (only those turned to the light), so none are culled here.
                .withCull(false)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, CompactChunkVertex.VERTEX_FORMAT);
        if (cutout) {
            builder.withBindGroupLayout(BLOCK_TEXTURE).withShaderDefine("ALPHA_CUTOUT", 0.5F);
        }
        return builder.build();
    }

    /**
     * @param lightView camera-relative world space to light space (x and y across the map, z towards the light)
     * @param toLight   direction to the light, in world space
     * @param range     half the width of the map, in blocks
     * @param depth     half the depth of the map, in blocks
     */
    public static void render(final RenderTarget target, final Vec3 camera, final Matrix4fc lightView, final Vector3fc toLight, final float range, final float depth) {
        SodiumWorldRenderer worldRenderer = SodiumWorldRenderer.instanceNullable();
        if (worldRenderer == null) return;
        RenderSectionManager sections = ((SodiumHooks.WorldRenderer) worldRenderer).metallumExtra$sections();
        if (sections == null || !(sections.getChunkRenderer() instanceof SodiumHooks.ChunkRenderer chunkRenderer)) return;

        frame++;
        collect(sections, camera, lightView, range, depth);
        if (VISIBLE.isEmpty()) return;

        if (drawContext == null) {
            drawContext = DrawContext.create();
            batch = MultiDrawBatch.newBatch(ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE + 1);
        }
        drawContext.rotate();

        // The index buffer must be big enough before the pass starts: growing it replaces it.
        SharedQuadIndexBuffer indices = chunkRenderer.metallumExtra$sharedIndexBuffer();
        indices.ensureCapacity(largestSection());

        // Only faces turned towards the light can cast a shadow. Sodium picks the faces to draw from where the
        // camera is, so it is told the camera is very far away in the direction of the light.
        CameraTransform cameraTransform = new CameraTransform(camera.x, camera.y, camera.z);
        CameraTransform lightTransform = new CameraTransform(camera.x + toLight.x() * 1.0e6, camera.y + toLight.y() * 1.0e6, camera.z + toLight.z() * 1.0e6);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Metallum Extra shadow terrain",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            draw(pass, DefaultTerrainRenderPasses.SOLID, SOLID, indices, cameraTransform, lightTransform);
            draw(pass, DefaultTerrainRenderPasses.CUTOUT, CUTOUT, indices, cameraTransform, lightTransform);
        }
        drawContext.endDraw();
    }

    private static void draw(final RenderPass pass, final TerrainRenderPass terrainPass, final RenderPipeline pipeline, final SharedQuadIndexBuffer indices,
                             final CameraTransform camera, final CameraTransform light) {
        boolean started = false;
        for (RegionList entry : VISIBLE) {
            RenderRegion region = entry.list.getRegion();
            SectionRenderDataStorage storage = region.getStorage(terrainPass);
            RenderRegion.DeviceResources resources = region.getResources();
            if (storage == null || resources == null) continue;

            batch.clear();
            SodiumChunkRendererInvoker.metallumExtra$fillCommandBuffer(batch, region, storage, entry.list, light, terrainPass, true, false);
            if (batch.isEmpty()) continue;

            if (!started) {
                started = true;
                pass.setPipeline(pipeline);
                drawContext.setContext(pass, pipeline);
                pass.setIndexBuffer(indices.getBufferObject(), IndexType.INT);
                if (pipeline == CUTOUT) {
                    pass.bindTexture("u_BlockTex", terrainPass.getAtlas(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                }
            }
            pass.setVertexBuffer(0, resources.getGeometryBuffer().slice());
            drawContext.updateData(region, camera);
            batch.draw(drawContext);
        }
    }

    /** Fills {@link #VISIBLE} with a list, per region, of the built sections inside the shadow map's box. */
    private static void collect(final RenderSectionManager sections, final Vec3 camera, final Matrix4fc lightView, final float range, final float depth) {
        VISIBLE.clear();

        // The box is aligned to the light; this is the block-aligned box that contains it.
        float reachX = range * (Math.abs(lightView.m00()) + Math.abs(lightView.m01())) + depth * Math.abs(lightView.m02()) + SECTION_RADIUS;
        float reachY = range * (Math.abs(lightView.m10()) + Math.abs(lightView.m11())) + depth * Math.abs(lightView.m12()) + SECTION_RADIUS;
        float reachZ = range * (Math.abs(lightView.m20()) + Math.abs(lightView.m21())) + depth * Math.abs(lightView.m22()) + SECTION_RADIUS;
        int minX = floorRegion(camera.x - reachX, RenderRegion.REGION_WIDTH), maxX = floorRegion(camera.x + reachX, RenderRegion.REGION_WIDTH);
        int minY = floorRegion(camera.y - reachY, RenderRegion.REGION_HEIGHT), maxY = floorRegion(camera.y + reachY, RenderRegion.REGION_HEIGHT);
        int minZ = floorRegion(camera.z - reachZ, RenderRegion.REGION_LENGTH), maxZ = floorRegion(camera.z + reachZ, RenderRegion.REGION_LENGTH);

        float limitAcross = range + SECTION_RADIUS;
        float limitDeep = depth + SECTION_RADIUS;

        for (int regionX = minX; regionX <= maxX; regionX++) {
            for (int regionY = minY; regionY <= maxY; regionY++) {
                for (int regionZ = minZ; regionZ <= maxZ; regionZ++) {
                    RenderRegion region = sections.regions.getForChunk(regionX * RenderRegion.REGION_WIDTH, regionY * RenderRegion.REGION_HEIGHT, regionZ * RenderRegion.REGION_LENGTH);
                    if (region == null || region.isEmpty() || region.getResources() == null) continue;

                    RegionList entry = null;
                    // Middle of the region's first section, relative to the camera.
                    float baseX = (float) (region.getOriginX() + 8 - camera.x);
                    float baseY = (float) (region.getOriginY() + 8 - camera.y);
                    float baseZ = (float) (region.getOriginZ() + 8 - camera.z);
                    for (int index = 0; index < RenderRegion.REGION_SIZE; index++) {
                        if ((region.getSectionFlags(index) & RenderSectionFlags.MASK_HAS_BLOCK_GEOMETRY) == 0) continue;
                        float x = baseX + LocalSectionIndex.unpackX(index) * 16;
                        float y = baseY + LocalSectionIndex.unpackY(index) * 16;
                        float z = baseZ + LocalSectionIndex.unpackZ(index) * 16;
                        float across = lightView.m00() * x + lightView.m10() * y + lightView.m20() * z;
                        float up = lightView.m01() * x + lightView.m11() * y + lightView.m21() * z;
                        float deep = lightView.m02() * x + lightView.m12() * y + lightView.m22() * z;
                        if (Math.abs(across) > limitAcross || Math.abs(up) > limitAcross || Math.abs(deep) > limitDeep) continue;

                        if (entry == null) {
                            entry = LISTS.get(region);
                            if (entry == null) {
                                entry = new RegionList(region);
                                LISTS.put(region, entry);
                            }
                            entry.list.reset(frame);
                            entry.lastUsed = frame;
                            VISIBLE.add(entry);
                        }
                        entry.list.add(index);
                    }
                }
            }
        }

        if ((frame & 255) == 0) {
            LISTS.values().removeIf(entry -> frame - entry.lastUsed > LIST_LIFETIME);
        }
    }

    /** The most indices any one section in the lists can ask for in a single draw. */
    private static int largestSection() {
        long most = 0;
        for (RegionList entry : VISIBLE) {
            RenderRegion region = entry.list.getRegion();
            most = Math.max(most, largest(region.getStorage(DefaultTerrainRenderPasses.SOLID), entry.list));
            most = Math.max(most, largest(region.getStorage(DefaultTerrainRenderPasses.CUTOUT), entry.list));
        }
        return (int) Math.min((most >> 2) * 6, Integer.MAX_VALUE);
    }

    private static long largest(final @Nullable SectionRenderDataStorage storage, final ChunkRenderList list) {
        if (storage == null) return 0;
        var sections = list.sectionsWithGeometryIterator(false);
        if (sections == null) return 0;
        long most = 0;
        while (sections.hasNext()) {
            long data = storage.getDataPointer(sections.nextByteAsInt());
            long vertices = 0;
            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                vertices += SectionRenderDataUnsafe.getVertexCount(data, facing);
            }
            most = Math.max(most, vertices);
        }
        return most;
    }

    private static int floorRegion(final double block, final int sectionsPerRegion) {
        return Math.floorDiv((int) Math.floor(block), 16 * sectionsPerRegion);
    }

    /** Frees what was allocated for drawing. The lists are plain memory and go with the garbage collector. */
    public static void close() {
        LISTS.clear();
        VISIBLE.clear();
        if (drawContext != null) {
            drawContext.delete();
            drawContext = null;
        }
        if (batch != null) {
            batch.delete();
            batch = null;
        }
    }
}
