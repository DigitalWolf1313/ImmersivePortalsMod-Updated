package qouteall.imm_ptl.peripheral.wand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.CustomTextOverlay;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The keyframe animation recording mode of the portal wand.
 * It reuses the drag wand for moving the portal.
 * Each side of the portal has its own track of keyframes with its own durations and easings.
 * Both tracks share the loop setting and the animation clock.
 * The portal's state when the session starts is the implicit first keyframe of both tracks.
 */
@Environment(EnvType.CLIENT)
public class ClientPortalWandPortalAnimate {
    private static final int DEFAULT_DURATION_TICKS = 20;
    private static final int MAX_DURATION_TICKS = 1200;
    private static final int MAX_KEYFRAMES = 64;
    
    private static final int SIDE_THIS = 0;
    private static final int SIDE_OTHER = 1;
    private static final String[] SIDE_NAME_KEYS = {
        "imm_ptl.wand.animate.side_this", "imm_ptl.wand.animate.side_other"
    };
    
    private static final int colorOfStart = 0xff55ff55;
    private static final int colorOfKeyframe = 0xff03fcfc;
    private static final int colorOfInactiveKeyframe = 0xffb36bff;
    private static final int colorOfSelected = 0xfffca903;
    private static final int colorOfPath = 0xffffffff;
    private static final int colorOfLinked = 0xffff6ec7;
    private static final int MAX_LINKED_PORTALS = 16;
    
    private static final TimingFunction[] EASINGS = TimingFunction.values();
    
    private record Keyframe(UnilateralPortalState state, int durationTicks, TimingFunction easing) {
    }
    
    private static class Track {
        final List<Keyframe> keyframes = new ArrayList<>();
        int selectedIndex = -1;
        
        boolean hasSelection() {
            return selectedIndex >= 0 && selectedIndex < keyframes.size();
        }
        
        int totalTicks() {
            int total = 0;
            for (Keyframe keyframe : keyframes) {
                total += keyframe.durationTicks();
            }
            return total;
        }
        
        void clear() {
            keyframes.clear();
            selectedIndex = -1;
        }
    }
    
    private static boolean active = false;
    @Nullable
    private static UUID portalId;
    @Nullable
    private static PortalState startState;
    
    // The index is the side of the portal that holds the animation.
    private static final Track[] tracks = {new Track(), new Track()};
    
    // The ids of the portals that face the same side as the session portal, including the flipped portal.
    private static final Set<UUID> sameSideIds = new HashSet<>();
    
    // The ids of the portals that face the other side of the session portal.
    // They can be in another dimension, so the client cannot always find them by geometry.
    private static final Set<UUID> otherSideIds = new HashSet<>();
    
    // The ids of the portals that get the same animation when the session finishes.
    // Each of them can belong to another cluster than the session portal.
    private static final Set<UUID> linkedIds = new LinkedHashSet<>();
    
    // The side whose track is edited. It follows the portal that the player right clicks.
    private static int activeSide = SIDE_THIS;
    
    private static boolean loop = true;
    
    // Holding right click repeats the item use, so one press must add only one keyframe.
    private static boolean keyframeLatch = false;
    
    // Counts down while waiting for the server to send the keyframes of an existing animation.
    private static int awaitingTicks = 0;
    
    private static boolean previewing = false;
    private static int previewTicksLeft = 0;
    @Nullable
    private static PortalState previewRestoreState;
    
    private static final Set<KeyMapping> heldKeys = new HashSet<>();
    
    public static boolean isActive() {
        return active;
    }
    
    public static void reset() {
        active = false;
        portalId = null;
        startState = null;
        tracks[SIDE_THIS].clear();
        tracks[SIDE_OTHER].clear();
        sameSideIds.clear();
        otherSideIds.clear();
        linkedIds.clear();
        activeSide = SIDE_THIS;
        loop = true;
        keyframeLatch = false;
        awaitingTicks = 0;
        previewing = false;
        previewTicksLeft = 0;
        previewRestoreState = null;
        heldKeys.clear();
    }
    
    public static void onRightClick() {
        if (previewing || awaitingTicks > 0) {
            return;
        }
        
        Portal hovered = ClientPortalWandPortalDrag.getHoveredPortal();
        
        if (!active) {
            if (hovered == null) {
                return;
            }
            
            boolean hasAnimation = hovered.getAnimationHolder() != null;
            startSession(hovered);
            
            if (hasAnimation && active) {
                // The server replies with the keyframes after it moves the portal to the last one.
                awaitingTicks = 40;
                return;
            }
        }
        else if (hovered != null) {
            if (!isSessionPortal(hovered)) {
                showAlert("imm_ptl.wand.animate.wrong_portal");
                return;
            }
            
            // Clicking while dragging only drops the portal, so the track must not change then.
            if (!ClientPortalWandPortalDrag.isDragging()) {
                selectSideOf(hovered);
            }
        }
        
        ClientPortalWandPortalDrag.onRightClick();
    }
    
    public static void onShiftRightClick() {
        if (!active || keyframeLatch || previewing || awaitingTicks > 0) {
            return;
        }
        keyframeLatch = true;
        
        Track track = activeTrack();
        if (track.keyframes.size() >= MAX_KEYFRAMES) {
            showAlert("imm_ptl.wand.animate.too_many");
            return;
        }
        
        UnilateralPortalState state = captureActiveSideState();
        if (state == null) {
            return;
        }
        
        TimingFunction easing = TimingFunction.easeInOutCubic;
        if (track.hasSelection()) {
            easing = track.keyframes.get(track.selectedIndex).easing();
        }
        
        track.keyframes.add(new Keyframe(state, DEFAULT_DURATION_TICKS, easing));
        track.selectedIndex = track.keyframes.size() - 1;
    }
    
    public static void removeSelectedKeyframe() {
        if (previewing || awaitingTicks > 0 || ClientPortalWandPortalDrag.isDragging()) {
            return;
        }
        
        Track track = activeTrack();
        if (!track.hasSelection()) {
            showAlert("imm_ptl.wand.animate.select_first");
            return;
        }
        track.keyframes.remove(track.selectedIndex);
        track.selectedIndex = Math.min(track.selectedIndex, track.keyframes.size() - 1);
        
        UnilateralPortalState state = track.hasSelection() ?
            track.keyframes.get(track.selectedIndex).state() :
            (startState == null ? null : sideOf(startState, activeSide));
        if (state != null) {
            sendSideState(state);
        }
    }
    
    /**
     * Returns true if the scroll is consumed so that the hotbar does not change.
     */
    public static boolean onScroll(double yOffset) {
        if (!active || Minecraft.getInstance().screen != null) {
            return false;
        }
        
        if (yOffset != 0 && !previewing && awaitingTicks == 0) {
            adjustDuration(yOffset > 0 ? 1 : -1);
        }
        return true;
    }
    
    /**
     * Runs at the start of the client tick, before the vanilla key handling.
     */
    public static void tick() {
        if (!active) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null || mc.level == null) {
            reset();
            return;
        }
        
        if (!mc.options.keyUse.isDown()) {
            keyframeLatch = false;
        }
        
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
            finishSession();
            return;
        }
        
        if (!isHoldingAnimateWand(player)) {
            finishSession();
            return;
        }
        
        if (mc.screen != null) {
            return;
        }
        
        // Swallow the hotbar keys so that the number keys do not switch the item.
        for (var hotbarKey : mc.options.keyHotbarSlots) {
            while (hotbarKey.consumeClick()) {
            }
        }
        
        boolean togglePreview = pressedEdge(PortalWandKeys.TOGGLE_PREVIEW);
        boolean plus = pressedEdge(PortalWandKeys.DURATION_UP);
        plus |= pressedEdge(PortalWandKeys.DURATION_UP_ALT);
        boolean minus = pressedEdge(PortalWandKeys.DURATION_DOWN);
        minus |= pressedEdge(PortalWandKeys.DURATION_DOWN_ALT);
        boolean cycleEasing = pressedEdge(PortalWandKeys.CYCLE_EASING);
        boolean toggleLoop = pressedEdge(PortalWandKeys.TOGGLE_LOOP);
        boolean updateKeyframe = pressedEdge(PortalWandKeys.UPDATE_KEYFRAME);
        boolean previous = pressedEdge(PortalWandKeys.PREVIOUS);
        boolean next = pressedEdge(PortalWandKeys.NEXT);
        boolean delete = pressedEdge(PortalWandKeys.DELETE);
        boolean link = pressedEdge(PortalWandKeys.LINK_PORTAL);
        
        if (awaitingTicks > 0) {
            awaitingTicks--;
            return;
        }
        
        if (previewing) {
            previewTicksLeft--;
            if (togglePreview || previewTicksLeft <= 0) {
                stopPreview();
            }
            return;
        }
        
        if (link && !ClientPortalWandPortalDrag.isDragging()) {
            toggleLinkedPortal();
        }
        if (plus) {
            adjustDuration(20);
        }
        if (minus) {
            adjustDuration(-20);
        }
        if (cycleEasing) {
            cycleEasing();
        }
        if (toggleLoop) {
            loop = !loop;
        }
        if (updateKeyframe) {
            updateSelectedKeyframe();
        }
        
        // Moving the portal to a keyframe would fight with the drag.
        if (!ClientPortalWandPortalDrag.isDragging()) {
            if (togglePreview) {
                startPreview();
            }
            if (previous) {
                select(activeTrack().selectedIndex - 1);
            }
            if (next) {
                select(activeTrack().selectedIndex + 1);
            }
            if (delete) {
                removeSelectedKeyframe();
            }
        }
    }
    
    public static void updateDisplay() {
        ClientPortalWandPortalDrag.updateDisplay();
        
        Minecraft mc = Minecraft.getInstance();
        
        if (!active) {
            CustomTextOverlay.putText(Component.translatable("imm_ptl.wand.animate.hint"), "2_anim");
            return;
        }
        
        if (previewing) {
            CustomTextOverlay.putText(
                Component.translatable(
                    "imm_ptl.wand.animate.previewing",
                    PortalWandKeys.TOGGLE_PREVIEW.getTranslatedKeyMessage()
                ).withStyle(ChatFormatting.GOLD),
                "2_anim"
            );
            return;
        }
        
        MutableComponent text = Component.translatable(
            "imm_ptl.wand.animate.status",
            Component.translatable(loop ? "imm_ptl.wand.animate.loop_on" : "imm_ptl.wand.animate.loop_off")
        ).withStyle(ChatFormatting.GOLD);
        
        for (int side = 0; side < tracks.length; side++) {
            Track track = tracks[side];
            boolean editing = side == activeSide;
            
            text.append("\n").append(Component.translatable(
                editing ? "imm_ptl.wand.animate.track_editing" : "imm_ptl.wand.animate.track",
                Component.translatable(SIDE_NAME_KEYS[side]),
                track.keyframes.size(),
                "%.2f".formatted(track.totalTicks() / 20.0)
            ).withStyle(editing ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        
        Track track = activeTrack();
        if (track.hasSelection()) {
            Keyframe keyframe = track.keyframes.get(track.selectedIndex);
            text.append("\n").append(Component.translatable(
                "imm_ptl.wand.animate.selected",
                track.selectedIndex + 1,
                "%.2f".formatted(keyframe.durationTicks() / 20.0),
                keyframe.easing().name()
            ));
        }
        
        if (!linkedIds.isEmpty()) {
            text.append("\n").append(Component.translatable(
                "imm_ptl.wand.animate.linked", linkedIds.size()
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        
        text.append("\n").append(Component.translatable(
            "imm_ptl.wand.animate.controls",
            mc.options.keyShift.getTranslatedKeyMessage(),
            mc.options.keyUse.getTranslatedKeyMessage(),
            mc.options.keyAttack.getTranslatedKeyMessage(),
            PortalWandKeys.DURATION_UP.getTranslatedKeyMessage(),
            PortalWandKeys.DURATION_DOWN.getTranslatedKeyMessage(),
            PortalWandKeys.CYCLE_EASING.getTranslatedKeyMessage(),
            PortalWandKeys.TOGGLE_LOOP.getTranslatedKeyMessage(),
            PortalWandKeys.NEXT.getTranslatedKeyMessage(),
            PortalWandKeys.PREVIOUS.getTranslatedKeyMessage(),
            PortalWandKeys.UPDATE_KEYFRAME.getTranslatedKeyMessage(),
            PortalWandKeys.TOGGLE_PREVIEW.getTranslatedKeyMessage(),
            PortalWandKeys.DELETE.getTranslatedKeyMessage(),
            PortalWandKeys.LINK_PORTAL.getTranslatedKeyMessage()
        ));
        
        CustomTextOverlay.putText(text, "2_anim");
    }
    
    public static void render(
        PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
        double camX, double camY, double camZ
    ) {
        ClientPortalWandPortalDrag.render(poseStack, bufferSource, camX, camY, camZ);
        
        LocalPlayer player = Minecraft.getInstance().player;
        if (!active || player == null || PortalRendering.isRendering()) {
            return;
        }
        
        ResourceKey<Level> currDim = player.level().dimension();
        Vec3 cameraPos = new Vec3(camX, camY, camZ);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        
        for (UUID linkedId : linkedIds) {
            Portal linked = WandUtil.getClientPortalByUUIDInAnyWorld(linkedId);
            if (linked != null && linked.level().dimension() == currDim) {
                WireRenderingHelper.renderRectFrameFlow(
                    poseStack, cameraPos, vertexConsumer, linked.getThisSideState(), colorOfLinked, colorOfLinked
                );
            }
        }
        
        for (int side = 0; side < tracks.length; side++) {
            Track track = tracks[side];
            int keyframeColor = side == activeSide ? colorOfKeyframe : colorOfInactiveKeyframe;
            
            UnilateralPortalState previous = null;
            
            if (startState != null) {
                previous = sideOf(startState, side);
                if (previous.dimension() == currDim) {
                    WireRenderingHelper.renderRectFrameFlow(
                        poseStack, cameraPos, vertexConsumer, previous, colorOfStart, colorOfStart
                    );
                }
            }
            
            for (int i = 0; i < track.keyframes.size(); i++) {
                UnilateralPortalState rect = track.keyframes.get(i).state();
                
                if (rect.dimension() != currDim) {
                    previous = null;
                    continue;
                }
                
                int color = i == track.selectedIndex ? colorOfSelected : keyframeColor;
                WireRenderingHelper.renderRectFrameFlow(
                    poseStack, cameraPos, vertexConsumer, rect, color, color
                );
                
                if (previous != null && previous.dimension() == currDim) {
                    Vec3 from = previous.position();
                    WireRenderingHelper.renderLines(
                        vertexConsumer, cameraPos, from,
                        new Vec3[]{Vec3.ZERO, rect.position().subtract(from)},
                        1.0, DQuaternion.identity, colorOfPath, poseStack
                    );
                }
                previous = rect;
            }
        }
    }
    
    private static void startSession(Portal portal) {
        PortalState state = portal.getPortalState();
        if (state == null) {
            return;
        }
        
        reset();
        active = true;
        portalId = portal.getUUID();
        startState = state;
        
        // The server may switch the session to the portal that holds the existing animation.
        Portal holder = portal.getAnimationHolder();
        activeSide = holder != null && isOtherSideOf(holder, portal) ? SIDE_OTHER : SIDE_THIS;
        
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.startAnimationSession",
            portal.getUUID()
        );
    }
    
    private static void finishSession() {
        if (!active) {
            return;
        }
        
        // Stop the drag first so that no drag request arrives after the animation starts.
        if (ClientPortalWandPortalDrag.isDragging()) {
            ClientPortalWandPortalDrag.stopDragging();
        }
        
        CompoundTag data = buildAnimationData();
        
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.finishAnimationSession",
            data
        );
        
        reset();
    }
    
    private static CompoundTag buildAnimationData() {
        CompoundTag data = new CompoundTag();
        data.put("thisKeyframes", trackToTag(tracks[SIDE_THIS]));
        data.put("otherKeyframes", trackToTag(tracks[SIDE_OTHER]));
        
        ListTag linked = new ListTag();
        for (UUID id : linkedIds) {
            linked.add(NbtUtils.createUUID(id));
        }
        data.put("linkedIds", linked);
        data.putBoolean("loop", loop);
        return data;
    }
    
    private static ListTag trackToTag(Track track) {
        ListTag list = new ListTag();
        for (Keyframe keyframe : track.keyframes) {
            CompoundTag tag = new CompoundTag();
            tag.put("state", keyframe.state().toTag());
            tag.putInt("durationTicks", keyframe.durationTicks());
            tag.putString("easing", keyframe.easing().name());
            list.add(tag);
        }
        return list;
    }
    
    private static void loadTrack(Track track, ListTag list) {
        track.clear();
        
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            
            TimingFunction easing;
            try {
                easing = TimingFunction.valueOf(tag.getString("easing"));
            }
            catch (IllegalArgumentException ex) {
                easing = TimingFunction.easeInOutCubic;
            }
            
            track.keyframes.add(new Keyframe(
                UnilateralPortalState.fromTag(tag.getCompound("state")),
                Mth.clamp(tag.getInt("durationTicks"), 0, MAX_DURATION_TICKS),
                easing
            ));
        }
        
        track.selectedIndex = track.keyframes.size() - 1;
    }
    
    private static CompoundTag stateToTag(PortalState state) {
        CompoundTag tag = new CompoundTag();
        tag.put("thisSide", state.getThisSideState().toTag());
        tag.put("otherSide", state.getOtherSideState().toTag());
        return tag;
    }
    
    private static PortalState tagToState(CompoundTag tag) {
        return UnilateralPortalState.combine(
            UnilateralPortalState.fromTag(tag.getCompound("thisSide")),
            UnilateralPortalState.fromTag(tag.getCompound("otherSide"))
        );
    }
    
    private static void sendPortalState(PortalState state) {
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.setAnimationSessionPortalState",
            stateToTag(state)
        );
    }
    
    /**
     * Moves the active side of the portal to the state and leaves the other side where it is.
     */
    private static void sendSideState(UnilateralPortalState sideState) {
        UUID id = portalId;
        if (id == null) {
            return;
        }
        
        PortalState current = ClientPortalWandPortalDrag.getTargetPortalState(id);
        if (current == null) {
            return;
        }
        
        sendPortalState(withSide(current, activeSide, sideState));
    }
    
    private static void beginEdit(CompoundTag data) {
        if (!active) {
            return;
        }
        
        portalId = data.getUUID("portalId");
        loadClusterIds(data);
        startState = tagToState(data.getCompound("start"));
        loop = data.getBoolean("loop");
        
        loadTrack(tracks[SIDE_THIS], data.getList("thisKeyframes", Tag.TAG_COMPOUND));
        loadTrack(tracks[SIDE_OTHER], data.getList("otherKeyframes", Tag.TAG_COMPOUND));
        
        awaitingTicks = 0;
    }
    
    private static void startPreview() {
        UUID id = portalId;
        if (id == null || (tracks[SIDE_THIS].keyframes.isEmpty() && tracks[SIDE_OTHER].keyframes.isEmpty())) {
            showAlert("imm_ptl.wand.animate.select_first");
            return;
        }
        
        PortalState restoreState = ClientPortalWandPortalDrag.getTargetPortalState(id);
        if (restoreState == null) {
            return;
        }
        
        int totalTicks = 0;
        for (Track track : tracks) {
            if (track.keyframes.isEmpty()) {
                continue;
            }
            
            int trackTicks = track.totalTicks();
            if (loop) {
                // The looping animation has an extra phase that returns to the start.
                trackTicks += track.keyframes.get(track.keyframes.size() - 1).durationTicks();
            }
            totalTicks = Math.max(totalTicks, trackTicks);
        }
        
        previewRestoreState = restoreState;
        previewing = true;
        previewTicksLeft = totalTicks + 5;
        
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.previewAnimationSession",
            buildAnimationData()
        );
    }
    
    private static void stopPreview() {
        if (!previewing) {
            return;
        }
        
        previewing = false;
        PortalState restoreState = previewRestoreState;
        previewRestoreState = null;
        
        McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.stopAnimationPreview",
            restoreState == null ? new CompoundTag() : stateToTag(restoreState)
        );
    }
    
    private static void updateSelectedKeyframe() {
        Track track = activeTrack();
        if (!track.hasSelection()) {
            showAlert("imm_ptl.wand.animate.select_first");
            return;
        }
        
        UnilateralPortalState state = captureActiveSideState();
        if (state == null) {
            return;
        }
        
        Keyframe keyframe = track.keyframes.get(track.selectedIndex);
        track.keyframes.set(track.selectedIndex, new Keyframe(state, keyframe.durationTicks(), keyframe.easing()));
    }
    
    @Environment(EnvType.CLIENT)
    public static class RemoteCallables {
        public static void clientBeginEdit(CompoundTag data) {
            beginEdit(data);
        }
        
        public static void clientSessionCluster(CompoundTag data) {
            if (!active) {
                return;
            }
            
            loadClusterIds(data);
        }
        
        public static void clientAnimationNotEditable(CompoundTag data) {
            if (!active) {
                return;
            }
            
            startState = tagToState(data.getCompound("start"));
            awaitingTicks = 0;
            
            // The session portal is the one that the player clicked, so its own side is edited.
            activeSide = SIDE_THIS;
            loadClusterIds(data);
        }
    }
    
    /**
     * Links the aimed portal so that it gets the same animation, or unlinks it if it is already linked.
     * A portal counts as linked when any portal of its cluster is in the set.
     */
    private static void toggleLinkedPortal() {
        Portal hovered = ClientPortalWandPortalDrag.getHoveredPortal();
        if (hovered == null) {
            showAlert("imm_ptl.wand.animate.link_aim");
            return;
        }
        
        if (isSessionPortal(hovered)) {
            showAlert("imm_ptl.wand.animate.link_session");
            return;
        }
        
        if (linkedIds.removeAll(getClusterIds(hovered))) {
            showLinkAlert("imm_ptl.wand.animate.link_removed");
            return;
        }
        
        if (linkedIds.size() >= MAX_LINKED_PORTALS) {
            showAlert("imm_ptl.wand.animate.link_too_many");
            return;
        }
        
        linkedIds.add(hovered.getUUID());
        showLinkAlert("imm_ptl.wand.animate.link_added");
    }
    
    private static Set<UUID> getClusterIds(Portal portal) {
        Set<UUID> ids = new HashSet<>();
        ids.add(portal.getUUID());
        
        PortalExtension extension = PortalExtension.get(portal);
        if (extension.flippedPortal != null) {
            ids.add(extension.flippedPortal.getUUID());
        }
        if (extension.reversePortal != null) {
            ids.add(extension.reversePortal.getUUID());
        }
        if (extension.parallelPortal != null) {
            ids.add(extension.parallelPortal.getUUID());
        }
        return ids;
    }
    
    private static void showLinkAlert(String key) {
        CustomTextOverlay.putText(
            Component.translatable(key, linkedIds.size()).withStyle(ChatFormatting.GOLD),
            3.0,
            "0_alert"
        );
    }
    
    private static boolean isHoldingAnimateWand(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() == PortalWandItem.instance &&
            stack.getOrDefault(PortalWandItem.COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK)
                == PortalWandItem.Mode.ANIMATE_PORTAL;
    }
    
    private static void loadClusterIds(CompoundTag data) {
        loadIds(sameSideIds, data.getList("sameSideIds", Tag.TAG_INT_ARRAY));
        loadIds(otherSideIds, data.getList("otherSideIds", Tag.TAG_INT_ARRAY));
    }
    
    private static void loadIds(Set<UUID> ids, ListTag list) {
        ids.clear();
        for (Tag tag : list) {
            ids.add(NbtUtils.loadUUID(tag));
        }
    }
    
    private static boolean isSessionPortal(Portal hovered) {
        UUID id = hovered.getUUID();
        if (id.equals(portalId) || sameSideIds.contains(id) || otherSideIds.contains(id)) {
            return true;
        }
        
        // The server sends the ids shortly after the session starts, so the geometry is used until then.
        Portal sessionPortal = portalId == null ? null : WandUtil.getClientPortalByUUIDInAnyWorld(portalId);
        if (sessionPortal == null) {
            return false;
        }
        
        PortalExtension extension = PortalExtension.get(sessionPortal);
        return hovered == extension.flippedPortal
            || hovered == extension.reversePortal
            || hovered == extension.parallelPortal
            || Portal.isFlippedPortal(sessionPortal, hovered)
            || Portal.isReversePortal(sessionPortal, hovered);
    }
    
    /**
     * Returns true if the portal faces the other side of the holder.
     * The flipped portal faces the same side, so it does not count.
     * Portals of one cluster in different dimensions always face opposite sides.
     * The rotation of the portals is not checked because a rotated cross-dimension portal breaks the normal checks.
     */
    private static boolean isOtherSideOf(Portal holder, Portal portal) {
        if (holder == portal) {
            return false;
        }
        
        if (portal.level().dimension() != holder.level().dimension()) {
            return true;
        }
        
        // The flipped portal has its origin at the holder's origin.
        // The reverse and parallel portals have their origin at the holder's destination.
        return portal.getOriginPos().distanceToSqr(holder.getDestPos())
            < portal.getOriginPos().distanceToSqr(holder.getOriginPos());
    }
    
    private static void selectSideOf(Portal hovered) {
        UUID id = hovered.getUUID();
        if (otherSideIds.contains(id)) {
            activeSide = SIDE_OTHER;
            return;
        }
        if (id.equals(portalId) || sameSideIds.contains(id)) {
            activeSide = SIDE_THIS;
            return;
        }
        
        Portal sessionPortal = portalId == null ? null : WandUtil.getClientPortalByUUIDInAnyWorld(portalId);
        if (sessionPortal == null) {
            return;
        }
        
        activeSide = isOtherSideOf(sessionPortal, hovered) ? SIDE_OTHER : SIDE_THIS;
    }
    
    private static Track activeTrack() {
        return tracks[activeSide];
    }
    
    private static UnilateralPortalState sideOf(PortalState state, int side) {
        return side == SIDE_OTHER ? state.getOtherSideState() : state.getThisSideState();
    }
    
    /**
     * The portal has one scaling for both sides, so the size of the changed side drags the other side's size along.
     */
    private static PortalState withSide(PortalState state, int side, UnilateralPortalState sideState) {
        if (side == SIDE_OTHER) {
            UnilateralPortalState thisSide = new UnilateralPortalState.Builder()
                .from(state.getThisSideState())
                .width(sideState.width() / state.scaling)
                .height(sideState.height() / state.scaling)
                .thickness(sideState.thickness() / state.scaling)
                .build();
            return UnilateralPortalState.combine(thisSide, sideState);
        }
        return state.withThisSideUpdated(sideState, true);
    }
    
    @Nullable
    private static UnilateralPortalState captureActiveSideState() {
        UUID id = portalId;
        if (id == null) {
            return null;
        }
        
        PortalState state = ClientPortalWandPortalDrag.getTargetPortalState(id);
        return state == null ? null : sideOf(state, activeSide);
    }
    
    private static boolean pressedEdge(KeyMapping key) {
        boolean down = key.isDown();
        boolean wasDown = heldKeys.contains(key);
        
        if (down) {
            heldKeys.add(key);
        }
        else {
            heldKeys.remove(key);
        }
        
        return down && !wasDown;
    }
    
    private static void select(int index) {
        Track track = activeTrack();
        if (track.keyframes.isEmpty()) {
            return;
        }
        track.selectedIndex = Mth.clamp(index, 0, track.keyframes.size() - 1);
        sendSideState(track.keyframes.get(track.selectedIndex).state());
    }
    
    private static void adjustDuration(int deltaTicks) {
        Track track = activeTrack();
        if (!track.hasSelection()) {
            showAlert("imm_ptl.wand.animate.select_first");
            return;
        }
        
        Keyframe keyframe = track.keyframes.get(track.selectedIndex);
        int newDuration = Mth.clamp(keyframe.durationTicks() + deltaTicks, 0, MAX_DURATION_TICKS);
        track.keyframes.set(track.selectedIndex, new Keyframe(keyframe.state(), newDuration, keyframe.easing()));
    }
    
    private static void cycleEasing() {
        Track track = activeTrack();
        if (!track.hasSelection()) {
            showAlert("imm_ptl.wand.animate.select_first");
            return;
        }
        
        Keyframe keyframe = track.keyframes.get(track.selectedIndex);
        TimingFunction next = EASINGS[(keyframe.easing().ordinal() + 1) % EASINGS.length];
        track.keyframes.set(track.selectedIndex, new Keyframe(keyframe.state(), keyframe.durationTicks(), next));
    }
    
    private static void showAlert(String key) {
        CustomTextOverlay.putText(
            Component.translatable(key).withStyle(ChatFormatting.GOLD),
            3.0,
            "0_alert"
        );
    }
}
