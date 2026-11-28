package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.RenderSectionVisitor;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.GraphDirectionSet;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.collections.WriteQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.PortalOpeningSections;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Mixin(OcclusionCuller.class)
public abstract class MixinSodiumOcclusionCuller {
    @Shadow(remap = false)
    protected abstract RenderSection getRenderSection(int x, int y, int z);
    
    @Shadow(remap = false)
    public static boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        throw new RuntimeException();
    }
    
    @Unique
    private @Nullable SectionPos ip_modifiedStartPoint;
    
    // The packed sections that the flat portal opening passes through.
    // It is the search start when not null.
    @Unique
    private long @Nullable [] ip_openingSections;
    
    @Unique
    private static boolean ip_tolerantInitialFrustumTestFail;
    
    // update the iteration start point modification value
    @SuppressWarnings("ConstantValue")
    @ModifyVariable(
        method = "findVisible", at = @At("HEAD"), argsOnly = true, remap = false
    )
    boolean modifyUseOcclusionCulling(
        boolean originalValue,
        RenderSectionVisitor visitor, Viewport viewport, float searchDistance, boolean useOcclusionCulling, int frame
    ) {
        // Portal rendering turns the smart cull setting off when cave culling is not suitable for the portal.
        // The original value is not used there, because Sodium disables culling for a spectator inside a solid block.
        // The search starts from the opening sections, so the block at the camera position does not matter.
        boolean doUseOcclusionCulling = PortalRendering.isRendering()
            ? Minecraft.getInstance().smartCull && PortalRendering.shouldEnableSodiumCaveCulling()
            : originalValue;
        
        ip_modifiedStartPoint = null;
        ip_openingSections = null;
        ip_tolerantInitialFrustumTestFail = false;
        SodiumInterface.lastSectionSearchWasForPortal = PortalRendering.isRendering();
        
        if (PortalRendering.isRendering()) {
            Portal portal = PortalRendering.getRenderingPortal();
            
            Vec3 cameraPos = CHelper.getCurrentCameraPos();
            ip_modifiedStartPoint = portal.getPortalShape().getModifiedVisibleSectionIterationOrigin(
                portal, cameraPos
            );
            if (ip_modifiedStartPoint != null) {
                doUseOcclusionCulling = false;
                
                RenderSection renderSection = getRenderSection(
                    ip_modifiedStartPoint.x(), ip_modifiedStartPoint.y(), ip_modifiedStartPoint.z()
                );
                if (renderSection != null && !isWithinFrustum(viewport, renderSection)) {
                    ip_tolerantInitialFrustumTestFail = true;
                }
            }
            else if (doUseOcclusionCulling) {
                ip_openingSections = PortalOpeningSections.getOpeningSections(portal);
                if (ip_openingSections == null) {
                    doUseOcclusionCulling = false;
                }
            }
        }
        
        return doUseOcclusionCulling;
    }
    
    // Starts the search from the opening sections.
    // The opening sections have all incoming directions, so every path out of them is considered.
    // The outward direction restriction still uses the camera section,
    // which is valid for rays from the camera.
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private void onInit(
        RenderSectionVisitor visitor, WriteQueue<RenderSection> queue,
        Viewport viewport, float searchDistance, boolean useOcclusionCulling, int frame,
        CallbackInfo ci
    ) {
        long[] openingSections = ip_openingSections;
        if (openingSections == null) {
            return;
        }
        
        for (long packed : openingSections) {
            RenderSection section = getRenderSection(
                SectionPos.x(packed), SectionPos.y(packed), SectionPos.z(packed)
            );
            if (section == null) {
                continue;
            }
            
            if (section.getLastVisibleFrame() != frame) {
                section.setLastVisibleFrame(frame);
                section.setIncomingDirections(GraphDirectionSet.NONE);
                queue.enqueue(section);
            }
            section.addIncomingDirections(GraphDirectionSet.ALL);
        }
        
        ci.cancel();
    }
    
    // apply start point modification
    @Redirect(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;getChunkCoord()Lnet/minecraft/core/SectionPos;",
            remap = true
        ),
        remap = false
    )
    private SectionPos redirectGetChunkCoordInInit(Viewport instance) {
        if (ip_modifiedStartPoint != null) {
            return ip_modifiedStartPoint;
        }
        
        return instance.getChunkCoord();
    }
    
    // apply start point modification
    @Redirect(
        method = "initWithinWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;getChunkCoord()Lnet/minecraft/core/SectionPos;"
        ),
        remap = false
    )
    private SectionPos redirectGetChunkCoordInInitWithinWorld(Viewport instance) {
        if (ip_modifiedStartPoint != null) {
            return ip_modifiedStartPoint;
        }
        
        return instance.getChunkCoord();
    }
    
    // when iteration start point become a position that's outside of frustum
    // make it tolerant early frustum test failures to avoid wrongly halting iteration
    @Inject(
        method = "isWithinFrustum", at = @At("RETURN"), cancellable = true,
        remap = false
    )
    private static void onIsOutsideFrustum(
        Viewport viewport, RenderSection section,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (ip_tolerantInitialFrustumTestFail) {
            boolean withinFrustum = cir.getReturnValueZ();
            if (withinFrustum) {
                // when found a section that's in frustum, frustum test become normal
                ip_tolerantInitialFrustumTestFail = false;
            }
            cir.setReturnValue(true);
        }
    }
}
