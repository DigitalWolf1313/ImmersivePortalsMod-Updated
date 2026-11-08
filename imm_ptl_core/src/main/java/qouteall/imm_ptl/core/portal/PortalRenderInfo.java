package qouteall.imm_ptl.core.portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.render.GlQueryObject;
import qouteall.imm_ptl.core.render.PortalGroup;
import qouteall.imm_ptl.core.render.QueryManager;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// A portal's rendering related things
// to access the package private field of Portal, this class is not in "render" package
@Environment(EnvType.CLIENT)
public class PortalRenderInfo {
    
    public static class Visibility {
        private static final int MAX_PENDING_QUERIES = 3;
        
        private final ArrayDeque<GlQueryObject> pendingQueries = new ArrayDeque<>();
        private Boolean lastKnownVisible;
        
        public Visibility() {
            lastKnownVisible = null;
        }
        
        void update() {
            Boolean newestResult = null;
            
            Iterator<GlQueryObject> iterator = pendingQueries.iterator();
            while (iterator.hasNext()) {
                GlQueryObject query = iterator.next();
                
                Boolean result = query.fetchQueryResultIfAvailable();
                if (result == null) {
                    continue;
                }
                
                newestResult = result;
                iterator.remove();
                GlQueryObject.returnQueryObject(query);
            }
            
            if (newestResult != null) {
                lastKnownVisible = newestResult;
            }
            
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
            // Render conservatively until the first GPU result is available.
            return lastKnownVisible == null || lastKnownVisible;
        }
    }
    
    private final Map<List<UUID>, Visibility> infoMap = new HashMap<>();
    
    public int thisFrameQueryFrameIndex = -1;
    
    private boolean needsGroupingUpdate = true;
    @Nullable
    private PortalGroup renderingGroup;
    
    public static void init() {
        Portal.clientPortalTickSignal.connect(portal -> {
            PortalRenderInfo presentation = getOptional(portal);
            if (presentation != null) {
                presentation.tick(portal);
            }
        });
        
        Portal.portalCacheUpdateSignal.connect(portal -> {
            if (portal.level().isClientSide()) {
                PortalRenderInfo renderInfo = getOptional(portal);
                if (renderInfo != null) {
                    renderInfo.onPortalCacheUpdate(portal);
                }
            }
        });
        
        Portal.portalDisposeSignal.connect(portal -> {
            if (portal.level().isClientSide()) {
                PortalRenderInfo renderInfo = getOptional(portal);
                if (renderInfo != null) {
                    renderInfo.dispose();
                    renderInfo.setGroup(portal, null);
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
    
    }
    
    private void tick(Portal portal) {
        Validate.isTrue(portal.level().isClientSide());
        
        if (needsGroupingUpdate) {
            needsGroupingUpdate = false;
            updateGrouping(portal);
        }
        
        if (renderingGroup != null) {
            renderingGroup.purge();
            if (renderingGroup.portals.size() <= 1) {
                setGroup(portal, null);
            }
        }
    }
    
    // disposing twice is fine
    public void dispose() {
        disposeInfoMap((Map<List<UUID>, Visibility>) this.infoMap);
    }
    
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
    
    public static boolean renderAndDecideVisibility(PortalLike portalLike, Runnable queryRendering) {
        boolean decision;
        if (IPGlobal.offsetOcclusionQuery && portalLike instanceof Portal portal) {
            PortalRenderInfo renderInfo = get(portal);
            
            List<UUID> renderingDescription = WorldRenderInfo.getRenderingDescription();
            
            Visibility visibility = renderInfo.getVisibility(renderingDescription);
            
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
    
    // Grouping -----
    
    private void onPortalCacheUpdate(Portal portal) {
        needsGroupingUpdate = true;
        setGroup(portal, null);
    }
    
    private void setGroup(Portal portal, @Nullable PortalGroup group) {
        if (renderingGroup != null) {
            renderingGroup.removePortal(portal);
        }
        
        renderingGroup = group;
        if (renderingGroup != null) {
            renderingGroup.addPortal(portal);
        }
    }
    
    private void updateGrouping(Portal portal) {
        Validate.isTrue(!portal.isGlobalPortal);
        
        if (!IPGlobal.enablePortalRenderingMerge) {
            return;
        }
        
        if (!canMerge(portal)) {
            return;
        }
        
        List<Portal> nearbyPortals = McHelper.findEntitiesByBox(
            Portal.class,
            portal.getOriginWorld(),
            portal.getBoundingBox().inflate(0.5),
            Math.min(64, portal.getSizeEstimation()) * 2 + 5,
            p -> p != portal && !Portal.isFlippedPortal(p, portal) && canMerge(p)
        );
        
        Portal.TransformationDesc thisDesc = portal.getTransformationDesc();
        
        for (Portal that : nearbyPortals) {
            PortalRenderInfo nearbyPortalPresentation = get(that);
            
            PortalGroup itsGroup = nearbyPortalPresentation.renderingGroup;
            if (itsGroup != null) {
                //flipped portal pairs cannot be in the same group
                if (itsGroup.portals.stream().noneMatch(p -> Portal.isFlippedPortal(p, portal))) {
                    if (Portal.TransformationDesc.isRoughlyEqual(itsGroup.transformationDesc, thisDesc)) {
                        if (renderingGroup == null) {
                            // this is not in group, put into its group
                            setGroup(portal, itsGroup);
                        }
                        else {
                            // this and that are both in group, merge
                            mergeGroup(renderingGroup, itsGroup);
                        }
                        return;
                    }
                }
            }
            else {
                Portal.TransformationDesc itsDesc = that.getTransformationDesc();
                if (Portal.TransformationDesc.isRoughlyEqual(thisDesc, itsDesc)) {
                    if (renderingGroup == null) {
                        // this and that are not in any group
                        PortalGroup newGroup = new PortalGroup(thisDesc);
                        setGroup(portal, newGroup);
                        get(that).setGroup(that, newGroup);
                    }
                    else {
                        // this is in group and that is not in group
                        get(that).setGroup(that, renderingGroup);
                    }
                    
                    return;
                }
            }
            
        }
        
        setGroup(portal, null);
    }
    
    private static boolean canMerge(Portal p) {
        if (IPGlobal.forceMergePortalRendering) {
            return true;
        }
        if (!p.isVisible()) {
            return false;
        }
        
        return p.isRenderingMergable();
    }
    
    @Nullable
    public static PortalGroup getGroupOf(Portal portal) {
        Validate.isTrue(!portal.getIsGlobal());
        
        PortalRenderInfo portalRenderInfo = getOptional(portal);
        
        if (portalRenderInfo == null) {
            return null;
        }
        
        return portalRenderInfo.renderingGroup;
    }
    
    private static void mergeGroup(PortalGroup g1, PortalGroup g2) {
        if (g1 == g2) {
            return;
        }
        
        ArrayList<Portal> g2Portals = new ArrayList<>(g2.portals);
        for (Portal portal : g2Portals) {
            get(portal).setGroup(portal, g1);
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        
        // normally if a portal is removed by calling remove() it will dispose normally
        // but that cannot be guaranteed
        // use this to avoid potential resource leak
        IPGlobal.preTotalRenderTaskList.addTask(() -> {
//            if (!infoMap.isEmpty()) {
//                Helper.err("A PortalRenderInfo is not being deterministically disposed");
//            }
            dispose();
            return true;
        });
    }
}
