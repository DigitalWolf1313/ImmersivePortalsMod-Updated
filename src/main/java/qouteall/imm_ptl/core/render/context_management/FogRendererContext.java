package qouteall.imm_ptl.core.render.context_management;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.mixin.client.multiworld_awareness.MixinFogRenderer;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.joml.Vector4f;

/**
 * {@link FogRenderer}
 * {@link qouteall.imm_ptl.core.mixin.client.multiworld_awareness.MixinFogRenderer}
 */
@SuppressWarnings("SpellCheckingInspection")
public class FogRendererContext {
    public int targetBiomeFog = -1;
    public int previousBiomeFog = -1;
    public long biomeChangedTime = -1L;
    
    public static Consumer<FogRendererContext> copyContextFromObject;
    public static Consumer<FogRendererContext> copyContextToObject;
    public static Supplier<Vec3> getCurrentFogColor;
    
    public static StaticFieldsSwappingManager<FogRendererContext> swappingManager;
    
    public static void init() {
        copyContextFromObject = context -> {
            context.targetBiomeFog = MixinFogRenderer.ip_getTargetBiomeFog();
            context.previousBiomeFog = MixinFogRenderer.ip_getPreviousBiomeFog();
            context.biomeChangedTime = MixinFogRenderer.ip_getBiomeChangedTime();
        };

        copyContextToObject = context -> {
            MixinFogRenderer.ip_setTargetBiomeFog(context.targetBiomeFog);
            MixinFogRenderer.ip_setPreviousBiomeFog(context.previousBiomeFog);
            MixinFogRenderer.ip_setBiomeChangedTime(context.biomeChangedTime);
        };

        getCurrentFogColor = FogRendererContext::getFogColorForCurrentView;
        
        swappingManager = new StaticFieldsSwappingManager<>(
            copyContextFromObject, copyContextToObject, false,
            FogRendererContext::new
        );
        
        
    }
    
    public static void update() {
        if (swappingManager == null) {
            init();
        }

        swappingManager.setOuterDimension(RenderStates.originalPlayerDimension);
        swappingManager.resetChecks();
        if (ClientWorldLoader.getIsInitialized()) {
            ClientWorldLoader.getClientWorlds().forEach(world -> {
                ResourceKey<Level> dimension = world.dimension();
                swappingManager.contextMap.computeIfAbsent(
                    dimension,
                    k -> new StaticFieldsSwappingManager.ContextRecord<>(
                        dimension,
                        new FogRendererContext(),
                        dimension != RenderStates.originalPlayerDimension
                    )
                );
            });
        }
    }
    
    /**
     * Returns the rendering portal's destination, so that biome-based colors
     * do not change when the virtual camera moves.
     * Returns the given position when no portal is rendering.
     */
    public static Vec3 getBiomeSamplingPos(Vec3 cameraPos) {
        if (PortalRendering.isRendering()) {
            Portal portal = PortalRendering.getRenderingPortal();
            if (portal != null) {
                return portal.getDestPos();
            }
        }
        return cameraPos;
    }
    
    public static Vec3 getFogColorOf(
        ClientLevel destWorld, Vec3 pos
    ) {
        Minecraft client = Minecraft.getInstance();
        
        
        ClientLevel oldWorld = client.level;
        
        ResourceKey<Level> newWorldKey = destWorld.dimension();
        
        swappingManager.contextMap.computeIfAbsent(
            newWorldKey,
            k -> new StaticFieldsSwappingManager.ContextRecord<>(
                k, new FogRendererContext(), true
            )
        );
        
        swappingManager.pushSwapping(newWorldKey);
        client.level = destWorld;
        
        Camera newCamera = new Camera();
        ((IECamera) newCamera).ip_resetState(pos, destWorld);
        ((IECamera) newCamera).portal_setFocusedEntity(client.cameraEntity);
        
        Profiler.get().push("get_fog_color");
        try {
            return computeFogColor(newCamera, destWorld);
        }
        finally {
            swappingManager.popSwapping();
            client.level = oldWorld;
            Profiler.get().pop();
            
        }
    }

    public static Vec3 getFogColorForCurrentView() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) {
            throw new IllegalStateException("Cannot compute fog color without a client world");
        }
        return computeFogColor(client.gameRenderer.getMainCamera(), world);
    }

    private static Vec3 computeFogColor(Camera camera, ClientLevel world) {
        Minecraft client = Minecraft.getInstance();
        float partialTick = RenderStates.getPartialTick();
        Vector4f color = FogRenderer.computeFogColor(
            camera,
            partialTick,
            world,
            client.options.getEffectiveRenderDistance(),
            client.gameRenderer.getDarkenWorldAmount(partialTick)
        );
        return new Vec3(color.x(), color.y(), color.z());
    }
    
    public static void onPlayerTeleport(ResourceKey<Level> from, ResourceKey<Level> to) {
        swappingManager.updateOuterDimensionAndChangeContext(to);
        
        snapBiomeTransition();
    }
    
    // Makes the underwater fog color adopt the current biome immediately instead of fading.
    public static void snapBiomeTransition() {
        MixinFogRenderer.ip_setBiomeChangedTime(-1L);
    }
    
}
