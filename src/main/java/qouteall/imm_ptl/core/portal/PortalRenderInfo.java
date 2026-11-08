package qouteall.imm_ptl.core.portal;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.GlQueryObject;
import qouteall.imm_ptl.core.render.QueryManager;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.lang.ref.Cleaner;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// A portal's rendering related things
// to access the package private field of Portal, this class is not in "render" package
@SuppressWarnings("resource")
@Environment(EnvType.CLIENT)
public class PortalRenderInfo implements AutoCloseable {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static class Visibility {
        // How many query results we are willing to keep waiting for.
        // Keeping a few lets us retrieve a result even if the GPU is a couple
        // of frames behind, without ever stalling the render thread.
        private static final int MAX_PENDING_QUERIES = 3;
        
        private final ArrayDeque<GlQueryObject> pendingQueries = new ArrayDeque<>();
        private Boolean lastKnownVisible;
        
        public Visibility() {
            lastKnownVisible = null;
        }
        
        // Fetch every query result the GPU has finished, then recycle those
        // query objects. Called once per frame, before a new query is issued.
        void update() {
            Boolean newestResult = null;
            
            Iterator<GlQueryObject> iterator = pendingQueries.iterator();
            while (iterator.hasNext()) {
                GlQueryObject query = iterator.next();
                
                Boolean result = query.fetchQueryResultIfAvailable();
                if (result == null) {
                    // The GPU is still working on it. Leave it pending and use
                    // the previous result as the prediction.
                    continue;
                }
                
                newestResult = result;
                iterator.remove();
                GlQueryObject.returnQueryObject(query);
            }
            
            if (newestResult != null) {
                lastKnownVisible = newestResult;
            }
            
            // Do not let the queue grow without bound if the GPU falls behind.
            while (pendingQueries.size() > MAX_PENDING_QUERIES) {
                GlQueryObject oldest = pendingQueries.pollFirst();
                if (oldest != null) {
                    GlQueryObject.returnQueryObject(oldest);
                }
            }
        }
        
        void submitQuery(GlQueryObject query) {
            pendingQueries.addLast(query);
        }
        
        void dispose() {
            for (GlQueryObject query : pendingQueries) {
                GlQueryObject.returnQueryObject(query);
            }
            pendingQueries.clear();
            lastKnownVisible = null;
        }
        
        boolean hasPendingQueries() {
            return !pendingQueries.isEmpty();
        }
        
        boolean getLastKnownVisible() {
            // If nothing has been measured yet, render conservatively so that a
            // portal never pops in a frame late on the first frame it appears.
            return lastKnownVisible == null || lastKnownVisible;
        }
    }
    
    private final Map<List<UUID>, Visibility> infoMap = new HashMap<>();
    
    public int thisFrameQueryFrameIndex = -1;
    
    public static void init() {
        Portal.CLIENT_PORTAL_TICK_SIGNAL.register(portal -> {
            PortalRenderInfo presentation = getOptional(portal);
            if (presentation != null) {
                presentation.tick(portal);
            }
        });
        
        Portal.PORTAL_DISPOSE_SIGNAL.register(portal -> {
            if (portal.level().isClientSide()) {
                PortalRenderInfo renderInfo = getOptional(portal);
                if (renderInfo != null) {
                    renderInfo.dispose();
                }
            }
        });
    }
    
    @Nullable
    public static PortalRenderInfo getOptional(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
        
        return portal.portalRenderInfo;
    }
    
    public static PortalRenderInfo get(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
        
        if (portal.portalRenderInfo == null) {
            portal.portalRenderInfo = new PortalRenderInfo();
        }
        return portal.portalRenderInfo;
    }
    
    public PortalRenderInfo() {
        CLEANER.register(this, getGcDirectedCleaningFunc());
    }
    
    private void tick(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
    }
    
    // disposing twice is fine
    public void dispose() {
        disposeInfoMap(infoMap);
    }
    
    private Runnable getGcDirectedCleaningFunc() {
        Map<List<UUID>, Visibility> infoMap1 = this.infoMap;
        // the disposal func should not reference this
        return () -> {
            LOGGER.debug("Running GC-directed PortalRenderInfo clean");
            
            // the cleaner runs on its own thread. Use the task list to avoid thread safety issue.
            IPGlobal.PRE_TOTAL_RENDER_TASK_LIST.addOneShotTask(() -> {
                disposeInfoMap(infoMap1);
            });
        };
    }
    
    @Override
    public void close() throws Exception {
        dispose();
    }
    
    // running it twice is fine
    private static void disposeInfoMap(Map<List<UUID>, Visibility> infoMap) {
        infoMap.values().forEach(Visibility::dispose);
        infoMap.clear();
    }
    
    // Visibility Predicting -----
    
    private void updateQuerySet() {
        if (RenderStates.frameIndex != thisFrameQueryFrameIndex) {
            
            if (RenderStates.frameIndex == thisFrameQueryFrameIndex + 1) {
                infoMap.entrySet().removeIf(
                    entry -> !entry.getValue().hasPendingQueries()
                );
                
                infoMap.values().forEach(Visibility::update);
            }
            else {
                disposeInfoMap(infoMap);
            }
            
            thisFrameQueryFrameIndex = RenderStates.frameIndex;
        }
    }
    
    @NotNull
    private Visibility getVisibility(List<UUID> desc) {
        updateQuerySet();
        
        return infoMap.computeIfAbsent(desc, k -> new Visibility());
    }
    
    public static boolean renderAndDecideVisibility(Portal portal, Runnable queryRendering) {
        boolean decision;
        if (IPGlobal.offsetOcclusionQuery) {
            PortalRenderInfo renderInfo = get(portal);
            
            List<UUID> renderingDescription = WorldRenderInfo.getRenderingDescription();
            
            Visibility visibility = renderInfo.getVisibility(renderingDescription);
            
            // Predict from the latest query result that the GPU has finished.
            // This never blocks the render thread waiting on the GPU.
            decision = visibility.getLastKnownVisible();
            
            GlQueryObject thisFrameQuery = GlQueryObject.acquireQueryObject();
            
            thisFrameQuery.performQueryAnySamplePassed(queryRendering);
            
            visibility.submitQuery(thisFrameQuery);
        }
        else {
            decision = QueryManager.renderAndGetDoesAnySamplePass(queryRendering);
        }
        return decision;
    }
    
    private static final Cleaner CLEANER = Cleaner.create();
}
