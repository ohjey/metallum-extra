package com.metallumextra.mixin;

import com.metallumextra.ExtraConfig;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fix: fast section re-centering. This one is in Minecraft itself, not Metallum.
 * <p>
 * Every time the camera enters a new chunk section, vanilla's RotatingSectionStorage.repositionCenter walks every
 * slot in the grid ((2r+1)^2 x world height) to check whether its position is still right. At render distance 256
 * that is about 6.3 million slots on the render thread, 20-90 ms per crossing. But when the centre moves by a few
 * sections, only the slices that wrapped around can have changed, so this updates just those. A move too big for
 * that (or the first one after creation) falls through to the vanilla loop.
 */
@Mixin(value = RotatingSectionStorage.class, remap = false)
public abstract class RotatingSectionStorageMixin {
    @Unique
    private static final ExtraConfig metallumExtra$CONFIG = ExtraConfig.get();

    @Shadow @Final private int radius;
    @Shadow @Final private int minY;
    @Shadow @Final private int sectionGridSizeY;
    @Shadow @Final private int sectionGridSizeXZ;
    @Shadow private SectionPos centerSectionPos;

    @Shadow
    public abstract RotatingSectionStorage.Value getValue(int sectionX, int sectionY, int sectionZ);

    @Inject(method = "repositionCenter", at = @At("HEAD"), cancellable = true)
    private void metallumExtra$repositionChangedSlices(final SectionPos newCenter, final CallbackInfoReturnable<Boolean> cir) {
        if (!metallumExtra$CONFIG.fastSectionRecenter) return;
        SectionPos oldCenter = this.centerSectionPos;
        if (newCenter.equals(oldCenter)) return;

        long dx = (long) newCenter.x() - oldCenter.x();
        long dz = (long) newCenter.z() - oldCenter.z();
        if (Math.abs(dx) >= this.sectionGridSizeXZ || Math.abs(dz) >= this.sectionGridSizeXZ) return;

        this.centerSectionPos = newCenter;
        int minX = newCenter.x() - this.radius;
        int maxX = newCenter.x() + this.radius;
        int minZ = newCenter.z() - this.radius;
        int maxZ = newCenter.z() + this.radius;

        // The x (then z) coordinates that just came into range; each reuses the slot of one that left.
        if (dx != 0) {
            int from = dx > 0 ? maxX - (int) dx + 1 : minX;
            int to = dx > 0 ? maxX : minX - (int) dx - 1;
            for (int x = from; x <= to; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    this.metallumExtra$assignColumn(x, z);
                }
            }
        }
        if (dz != 0) {
            int from = dz > 0 ? maxZ - (int) dz + 1 : minZ;
            int to = dz > 0 ? maxZ : minZ - (int) dz - 1;
            for (int z = from; z <= to; z++) {
                for (int x = minX; x <= maxX; x++) {
                    this.metallumExtra$assignColumn(x, z);
                }
            }
        }
        cir.setReturnValue(true);
    }

    @Unique
    private void metallumExtra$assignColumn(final int x, final int z) {
        for (int i = 0; i < this.sectionGridSizeY; i++) {
            int y = this.minY + i;
            RotatingSectionStorage.Value value = this.getValue(x, y, z);
            long node = SectionPos.asLong(x, y, z);
            if (value.getSectionNode() != node) {
                value.setSectionNode(node);
            }
        }
    }
}
