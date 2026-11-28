package qouteall.imm_ptl.core.render;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the cloud mesh state of each portal view.
 * The vanilla cloud renderer keeps one mesh for the view it rendered last,
 * so every switch between the outer view and a same-dimension portal view
 * that are in different cloud cells rebuilds the whole mesh.
 * The mixin swaps the renderer's mesh state with the state of the view that is being rendered.
 * The outer view uses the renderer's own state.
 */
public class IPCloudViewStates {
    private static final int MAX_STATES = 8;
    
    public static class State {
        public boolean needsRebuild = true;
        public int prevCellX = Integer.MIN_VALUE;
        public int prevCellZ = Integer.MIN_VALUE;
        public CloudRenderer.RelativeCameraPos prevRelativeCameraPos = CloudRenderer.RelativeCameraPos.INSIDE_CLOUDS;
        public @Nullable CloudStatus prevType;
        public int quadCount = 0;
        public @Nullable MappableRingBuffer utb;
        
        // Used to detect that the cloud texture was reloaded.
        public @Nullable Object texture;
        
        public void dispose() {
            if (utb != null) {
                utb.close();
                utb = null;
            }
        }
    }
    
    private static final LinkedHashMap<List<UUID>, State> states = new LinkedHashMap<>(16, 0.75f, true);
    
    public static void init() {
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(IPCloudViewStates::cleanup);
        ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanup());
    }
    
    public static State get(List<UUID> viewKey) {
        List<UUID> key = new ArrayList<>(viewKey);
        State state = states.get(key);
        if (state != null) {
            return state;
        }
        
        state = new State();
        states.put(key, state);
        
        while (states.size() > MAX_STATES) {
            Iterator<Map.Entry<List<UUID>, State>> it = states.entrySet().iterator();
            Map.Entry<List<UUID>, State> eldest = it.next();
            eldest.getValue().dispose();
            it.remove();
        }
        
        return state;
    }
    
    public static void markAllForRebuild() {
        for (State state : states.values()) {
            state.needsRebuild = true;
        }
    }
    
    public static void cleanup() {
        for (State state : states.values()) {
            state.dispose();
        }
        states.clear();
    }
}
