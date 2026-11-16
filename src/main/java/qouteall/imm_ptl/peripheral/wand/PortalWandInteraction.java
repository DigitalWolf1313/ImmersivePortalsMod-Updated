package qouteall.imm_ptl.peripheral.wand;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.DeltaUnilateralPortalState;
import qouteall.imm_ptl.core.portal.animation.NormalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimationDriver;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.util.PortalLocalXYNormalized;
import qouteall.imm_ptl.peripheral.CommandStickItem;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Range;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

public class PortalWandInteraction {
    
    private static final double SIZE_LIMIT = 64;
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final WeakHashMap<ServerPlayer, DraggingSession> draggingSessionMap = new WeakHashMap<>();
    
    public PortalWandInteraction() {
    
    }
    
    public static PortalWandInteraction of(MinecraftServer server) {
        return IPPerServerInfo.of(server).portalWandInteraction;
    }
    
    public static class RemoteCallables {
        public static void finishPortalCreation(
            ServerPlayer player,
            ProtoPortal protoPortal
        ) {
            if (!checkPermission(player)) return;
            
            handleFinishPortalCreation(player, protoPortal);
        }
        
        public static void requestApplyDrag(
            ServerPlayer player,
            UUID portalId,
            Vec3 cursorPos,
            DraggingInfo draggingInfo
        ) {
            if (!checkPermission(player)) return;
            
            Entity entity = ((ServerLevel) player.level()).getEntity(portalId);
            
            if (!(entity instanceof Portal portal)) {
                LOGGER.error("Cannot find portal {}", portalId);
                return;
            }
            
            if (!draggingInfo.isValid()) {
                player.sendSystemMessage(Component.literal("Invalid dragging info"));
                LOGGER.error("Invalid dragging info {}", draggingInfo);
                return;
            }
            
            handleDraggingRequest(player, portalId, cursorPos, draggingInfo, portal);
        }
        
        public static void undoDrag(ServerPlayer player) {
            if (!checkPermission(player)) return;
            
            handleUndoDrag(player);
        }
        
        public static void finishDragging(ServerPlayer player) {
            if (!checkPermission(player)) return;
            
            handleFinishDrag(player);
        }
        
        public static void copyCutPortal(ServerPlayer player, UUID portalId, boolean isCut) {
            if (!checkPermission(player)) return;
            
            handleCopyCutPortal(player, portalId, isCut);
        }
        
        public static void confirmCopyCut(ServerPlayer player, Vec3 origin, DQuaternion orientation) {
            if (!checkPermission(player)) return;
            
            handleConfirmCopyCut(player, origin, orientation);
        }
        
        public static void startAnimationSession(ServerPlayer player, UUID portalId) {
            if (!checkPermission(player)) return;
            
            handleStartAnimationSession(player, portalId);
        }
        
        public static void finishAnimationSession(ServerPlayer player, CompoundTag data) {
            if (!checkPermission(player)) return;
            
            handleFinishAnimationSession(player, data);
        }
        
        public static void previewAnimationSession(ServerPlayer player, CompoundTag data) {
            if (!checkPermission(player)) return;
            
            handlePreviewAnimationSession(player, data);
        }
        
        public static void stopAnimationPreview(ServerPlayer player, CompoundTag state) {
            if (!checkPermission(player)) return;
            
            handleSetAnimationSessionPortalState(player, state, true);
        }
        
        public static void setAnimationSessionPortalState(ServerPlayer player, CompoundTag state) {
            if (!checkPermission(player)) return;
            
            handleSetAnimationSessionPortalState(player, state, false);
        }
        
        public static void clearPortalClipboard(ServerPlayer player) {
            if (!checkPermission(player)) return;
            
            handleClearPortalClipboard(player);
        }
    }
    
    private static void handleFinishPortalCreation(ServerPlayer player, ProtoPortal protoPortal) {
        Validate.isTrue(protoPortal.firstSide != null);
        Validate.isTrue(protoPortal.secondSide != null);
        
        Vec3 firstSideLeftBottom = protoPortal.firstSide.leftBottom;
        Vec3 firstSideRightBottom = protoPortal.firstSide.rightBottom;
        Vec3 firstSideLeftUp = protoPortal.firstSide.leftTop;
        Vec3 secondSideLeftBottom = protoPortal.secondSide.leftBottom;
        Vec3 secondSideRightBottom = protoPortal.secondSide.rightBottom;
        Vec3 secondSideLeftUp = protoPortal.secondSide.leftTop;
        
        Validate.notNull(firstSideLeftBottom);
        Validate.notNull(firstSideRightBottom);
        Validate.notNull(firstSideLeftUp);
        Validate.notNull(secondSideLeftBottom);
        Validate.notNull(secondSideRightBottom);
        Validate.notNull(secondSideLeftUp);
        
        ResourceKey<Level> firstSideDimension = protoPortal.firstSide.dimension;
        ResourceKey<Level> secondSideDimension = protoPortal.secondSide.dimension;
        
        Vec3 firstSideHorizontalAxis = firstSideRightBottom.subtract(firstSideLeftBottom);
        Vec3 firstSideVerticalAxis = firstSideLeftUp.subtract(firstSideLeftBottom);
        double firstSideWidth = firstSideHorizontalAxis.length();
        double firstSideHeight = firstSideVerticalAxis.length();
        Vec3 firstSideHorizontalUnitAxis = firstSideHorizontalAxis.normalize();
        Vec3 firstSideVerticalUnitAxis = firstSideVerticalAxis.normalize();
        
        if (Math.abs(firstSideWidth) < 0.001 || Math.abs(firstSideHeight) < 0.001) {
            player.sendSystemMessage(Component.literal("The first side is too small"));
            LOGGER.error("The first side is too small");
            return;
        }
        
        if (firstSideHorizontalUnitAxis.dot(firstSideVerticalUnitAxis) > 0.001) {
            player.sendSystemMessage(Component.literal("The horizontal and vertical axis are not perpendicular in first side"));
            LOGGER.error("The horizontal and vertical axis are not perpendicular in first side");
            return;
        }
        
        if (firstSideWidth > SIZE_LIMIT || firstSideHeight > SIZE_LIMIT) {
            player.sendSystemMessage(Component.literal("The first side is too large"));
            LOGGER.error("The first side is too large");
            return;
        }
        
        Vec3 secondSideHorizontalAxis = secondSideRightBottom.subtract(secondSideLeftBottom);
        Vec3 secondSideVerticalAxis = secondSideLeftUp.subtract(secondSideLeftBottom);
        double secondSideWidth = secondSideHorizontalAxis.length();
        double secondSideHeight = secondSideVerticalAxis.length();
        Vec3 secondSideHorizontalUnitAxis = secondSideHorizontalAxis.normalize();
        Vec3 secondSideVerticalUnitAxis = secondSideVerticalAxis.normalize();
        
        if (Math.abs(secondSideWidth) < 0.001 || Math.abs(secondSideHeight) < 0.001) {
            player.sendSystemMessage(Component.literal("The second side is too small"));
            LOGGER.error("The second side is too small");
            return;
        }
        
        if (secondSideHorizontalUnitAxis.dot(secondSideVerticalUnitAxis) > 0.001) {
            player.sendSystemMessage(Component.literal("The horizontal and vertical axis are not perpendicular in second side"));
            LOGGER.error("The horizontal and vertical axis are not perpendicular in second side");
            return;
        }
        
        if (secondSideWidth > SIZE_LIMIT || secondSideHeight > SIZE_LIMIT) {
            player.sendSystemMessage(Component.literal("The second side is too large"));
            LOGGER.error("The second side is too large");
            return;
        }
        
        if (Math.abs((firstSideHeight / firstSideWidth) - (secondSideHeight / secondSideWidth)) > 0.001) {
            player.sendSystemMessage(Component.literal("The two sides have different aspect ratio"));
            LOGGER.error("The two sides have different aspect ratio");
            return;
        }
        
        boolean overlaps = false;
        if (firstSideDimension == secondSideDimension) {
            Vec3 firstSideNormal = firstSideHorizontalUnitAxis.cross(firstSideVerticalUnitAxis);
            Vec3 secondSideNormal = secondSideHorizontalUnitAxis.cross(secondSideVerticalUnitAxis);
            
            // check orientation overlap
            if (Math.abs(firstSideNormal.dot(secondSideNormal)) > 0.99) {
                // check plane overlap
                if (Math.abs(firstSideLeftBottom.subtract(secondSideLeftBottom).dot(firstSideNormal)) < 0.001) {
                    // check portal area overlap
                    
                    Vec3 coordCenter = firstSideLeftBottom;
                    Vec3 coordX = firstSideHorizontalAxis;
                    Vec3 coordY = firstSideVerticalAxis;
                    
                    Range firstSideXRange = Range.createUnordered(
                        firstSideLeftBottom.subtract(coordCenter).dot(coordX),
                        firstSideRightBottom.subtract(coordCenter).dot(coordX)
                    );
                    Range firstSideYRange = Range.createUnordered(
                        firstSideLeftBottom.subtract(coordCenter).dot(coordY),
                        firstSideLeftUp.subtract(coordCenter).dot(coordY)
                    );
                    
                    Range secondSideXRange = Range.createUnordered(
                        secondSideLeftBottom.subtract(coordCenter).dot(coordX),
                        secondSideRightBottom.subtract(coordCenter).dot(coordX)
                    );
                    Range secondSideYRange = Range.createUnordered(
                        secondSideLeftBottom.subtract(coordCenter).dot(coordY),
                        secondSideLeftUp.subtract(coordCenter).dot(coordY)
                    );
                    
                    if (firstSideXRange.intersect(secondSideXRange) != null &&
                        firstSideYRange.intersect(secondSideYRange) != null
                    ) {
                        overlaps = true;
                    }
                }
            }
        }
        
        Portal portal = Portal.ENTITY_TYPE.create(McHelper.getServerWorld(firstSideDimension));
        Validate.notNull(portal);
        portal.setOriginPos(
            firstSideLeftBottom
                .add(firstSideHorizontalAxis.scale(0.5))
                .add(firstSideVerticalAxis.scale(0.5))
        );
        portal.setWidth(firstSideWidth);
        portal.setHeight(firstSideHeight);
        portal.setAxisW(firstSideHorizontalUnitAxis);
        portal.setAxisH(firstSideVerticalUnitAxis);
        
        portal.setDestDim(secondSideDimension);
        portal.setDestination(
            secondSideLeftBottom
                .add(secondSideHorizontalAxis.scale(0.5))
                .add(secondSideVerticalAxis.scale(0.5))
        );
        
        portal.setScaling(secondSideWidth / firstSideWidth);
        
        DQuaternion secondSideOrientation = DQuaternion.matrixToQuaternion(
            secondSideHorizontalUnitAxis,
            secondSideVerticalUnitAxis,
            secondSideHorizontalUnitAxis.cross(secondSideVerticalUnitAxis)
        );
        portal.setOtherSideOrientation(secondSideOrientation);
        
        Portal flippedPortal = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
        Portal reversePortal = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
        Portal parallelPortal = PortalManipulation.createFlippedPortal(reversePortal, Portal.ENTITY_TYPE);
        
        McHelper.spawnServerEntity(portal);
        
        if (overlaps) {
            player.sendSystemMessage(Component.translatable("imm_ptl.wand.overlap"));
        }
        else {
            McHelper.spawnServerEntity(flippedPortal);
            McHelper.spawnServerEntity(reversePortal);
            McHelper.spawnServerEntity(parallelPortal);
        }
        
        player.sendSystemMessage(Component.translatable("imm_ptl.wand.finished"));
        
        giveCommandStick(player, "/portal eradicate_portal_cluster");
    }
    
    private static class DraggingSession {
        private final ResourceKey<Level> dimension;
        private final UUID portalId;
        private final PortalState originalState;
        private final DraggingInfo lastDraggingInfo;
        
        // The holder of the animation that this drag paused.
        // Both fields are null when the drag did not pause an animation.
        @Nullable
        private ResourceKey<Level> pausedHolderDimension;
        @Nullable
        private UUID pausedHolderId;
        
        public DraggingSession(
            ResourceKey<Level> dimension, UUID portalId,
            PortalState originalState, DraggingInfo lastDraggingInfo
        ) {
            this.dimension = dimension;
            this.portalId = portalId;
            this.originalState = originalState;
            this.lastDraggingInfo = lastDraggingInfo;
        }
        
        @Nullable
        public Portal getPortal() {
            Entity entity = McHelper.getServerWorld(dimension).getEntity(portalId);
            
            if (entity instanceof Portal) {
                return (Portal) entity;
            }
            else {
                return null;
            }
        }
    }
    
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            of(server).draggingSessionMap.entrySet().removeIf(
                e -> {
                    ServerPlayer player = e.getKey();
                    if (player.isRemoved()) {
                        resumeAnimationPausedByDrag(e.getValue(), false);
                        return true;
                    }
                    
                    if (player.getMainHandItem().getItem() != PortalWandItem.instance) {
                        resumeAnimationPausedByDrag(e.getValue(), false);
                        return true;
                    }
                    
                    return false;
                }
            );
            
            animationSessionMap.entrySet().removeIf(e -> {
                if (!e.getKey().isRemoved()) {
                    return false;
                }
                
                abandonAnimationSession(e.getValue());
                restoreLinkedPreview(e.getKey());
                return true;
            });
            
            copyingSessionMap.entrySet().removeIf(
                e -> {
                    ServerPlayer player = e.getKey();
                    return player.isRemoved();
                }
            );
        });
        
        IPGlobal.SERVER_CLEANUP_EVENT.register(s -> of(s).draggingSessionMap.clear());
        IPGlobal.SERVER_CLEANUP_EVENT.register(s -> copyingSessionMap.clear());
        IPGlobal.SERVER_CLEANUP_EVENT.register(s -> animationSessionMap.clear());
        IPGlobal.SERVER_CLEANUP_EVENT.register(s -> previewLinkedMap.clear());
    }
    
    public static final class DraggingInfo {
        public final @Nullable PortalLocalXYNormalized lockedAnchor;
        public final PortalLocalXYNormalized draggingAnchor;
        public @Nullable Vec3 previousRotationAxis;
        public final boolean lockWidth;
        public final boolean lockHeight;
        
        public DraggingInfo(
            @Nullable PortalLocalXYNormalized lockedAnchor,
            PortalLocalXYNormalized draggingAnchor,
            @Nullable Vec3 previousRotationAxis,
            boolean lockWidth,
            boolean lockHeight
        ) {
            this.lockedAnchor = lockedAnchor;
            this.draggingAnchor = draggingAnchor;
            this.previousRotationAxis = previousRotationAxis;
            this.lockWidth = lockWidth;
            this.lockHeight = lockHeight;
        }
        
        public boolean shouldLockScale() {
            return lockWidth || lockHeight;
        }
        
        public boolean isValid() {
            if (lockedAnchor != null) {
                if (!lockedAnchor.isValid()) {
                    return false;
                }
            }
            if (!draggingAnchor.isValid()) {
                return false;
            }
            return true;
        }
    }
    
    @Nullable
    public static UnilateralPortalState applyDrag(
        UnilateralPortalState originalState, Vec3 cursorPos, DraggingInfo info,
        boolean updateInternalState
    ) {
        if (info.lockedAnchor == null) {
            Vec3 offset = info.draggingAnchor.getOffset(originalState);
            Vec3 newPos = cursorPos.subtract(offset);
            
            return new UnilateralPortalState.Builder()
                .from(originalState)
                .position(newPos)
                .build();
        }
        
        OneLockDraggingResult r = performDragWithOneLockedAnchor(
            originalState, info.lockedAnchor, info.draggingAnchor, cursorPos, info.previousRotationAxis,
            info.lockWidth, info.lockHeight
        );
        
        if (r == null) {
            return null;
        }
        
        if (updateInternalState) {
            info.previousRotationAxis = r.rotationAxis();
        }
        return r.newState();
    }
    
    /**
     * Pauses the animation so that the drag can move the portal.
     * The animation only holds one driver set for the whole cluster, so the holder is the portal that is paused.
     */
    private static void pauseAnimationForDrag(ServerPlayer player, Portal portal, DraggingSession session) {
        // The animation session of the animate wand drives the portal by itself.
        if (isAnimating(player)) {
            return;
        }
        
        Portal holder = portal.getAnimationHolder();
        if (holder == null || holder.animation.isPaused()) {
            return;
        }
        
        holder.pauseAnimation();
        session.pausedHolderDimension = holder.level().dimension();
        session.pausedHolderId = holder.getUUID();
    }
    
    /**
     * Resuming applies the movement made during the pause to the reference states of the animation.
     * That moves the whole animation together with the portal.
     */
    private static void resumeAnimationPausedByDrag(DraggingSession session, boolean undone) {
        ResourceKey<Level> dimension = session.pausedHolderDimension;
        UUID holderId = session.pausedHolderId;
        if (dimension == null || holderId == null) {
            return;
        }
        
        session.pausedHolderDimension = null;
        session.pausedHolderId = null;
        
        if (!(McHelper.getServerWorld(dimension).getEntity(holderId) instanceof Portal holder)) {
            return;
        }
        
        if (undone) {
            // Otherwise the rounding difference from the paused state would shift the animation.
            holder.animation.setBackToPausingState(holder);
            holder.rectifyClusterPortals(true);
        }
        
        holder.resumeAnimation();
    }
    
    private static void handleFinishDrag(ServerPlayer player) {
        DraggingSession session = of(player.server).draggingSessionMap.remove(player);
        
        if (session == null) {
            return;
        }
        
        Portal portal = session.getPortal();
        
        if (portal != null) {
            portal.reloadAndSyncToClientNextTick();
        }
        
        resumeAnimationPausedByDrag(session, false);
    }
    
    private static void handleUndoDrag(ServerPlayer player) {
        PortalWandInteraction portalWandInteraction = of(player.server);
        DraggingSession session = portalWandInteraction.draggingSessionMap.get(player);
        
        if (session == null) {
//            player.sendSystemMessage(Component.literal("Cannot undo"));
            return;
        }
        
        Portal portal = session.getPortal();
        
        if (portal == null) {
            LOGGER.error("Cannot find portal {}", session.portalId);
            resumeAnimationPausedByDrag(session, false);
            return;
        }
        
        portal.setPortalState(session.originalState);
        portal.reloadAndSyncToClientNextTick();
        portal.rectifyClusterPortals(true);
        
        resumeAnimationPausedByDrag(session, true);
        portalWandInteraction.draggingSessionMap.remove(player);
    }
    
    private static void handleDraggingRequest(
        ServerPlayer player, UUID portalId, Vec3 cursorPos, DraggingInfo draggingInfo, Portal portal
    ) {
        PortalWandInteraction portalWandInteraction = of(player.server);
        
        DraggingSession session = portalWandInteraction.draggingSessionMap.get(player);
        
        if (session != null && session.portalId.equals(portalId)) {
            // reuse session
        }
        else {
            if (session != null) {
                resumeAnimationPausedByDrag(session, false);
            }
            
            session = new DraggingSession(
                player.level().dimension(),
                portalId,
                portal.getPortalState(),
                draggingInfo
            );
            pauseAnimationForDrag(player, portal, session);
            portalWandInteraction.draggingSessionMap.put(player, session);
        }
        
        UnilateralPortalState newThisSideState = applyDrag(
            session.originalState.getThisSideState(), cursorPos, draggingInfo,
            true
        );
        if (validateDraggedPortalState(session.originalState, newThisSideState, player)) {
            portal.setThisSideState(newThisSideState, draggingInfo.shouldLockScale());
            portal.reloadAndSyncToClientNextTick();
            portal.rectifyClusterPortals(true);
        }
        else {
            player.sendSystemMessage(Component.literal("Invalid dragging"));
        }
    }
    
    private static boolean checkPermission(ServerPlayer player) {
        if (!canPlayerUsePortalWand(player)) {
            player.sendSystemMessage(Component.literal("You cannot use portal wand"));
            LOGGER.error("Player cannot use portal wand {}", player);
            return false;
        }
        return true;
    }
    
    public static boolean validateDraggedPortalState(
        PortalState originalState, UnilateralPortalState newThisSideState,
        Player player
    ) {
        if (newThisSideState == null) {
            return false;
        }
        if (newThisSideState.width() > 64.1) {
            return false;
        }
        if (newThisSideState.height() > 64.1) {
            return false;
        }
        if (newThisSideState.width() < 0.05) {
            return false;
        }
        if (newThisSideState.height() < 0.05) {
            return false;
        }
        
        if (originalState.fromWorld != newThisSideState.dimension()) {
            return false;
        }
        
        if (newThisSideState.position().distanceTo(player.position()) > 64) {
            return false;
        }
        
        return true;
    }
    
    private static boolean canPlayerUsePortalWand(ServerPlayer player) {
        return player.hasPermissions(2)
            || (IPGlobal.easeCreativePermission && player.isCreative())
            || (IPConfig.getConfig().portalWandUsableOnSurvivalMode
            && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL);
    }
    
    private static void giveCommandStick(ServerPlayer player, String command) {
        CommandStickItem.Data data = CommandStickItem.BUILT_IN_COMMAND_STICK_TYPES.get(command);
        
        if (data == null) {
            data = new CommandStickItem.Data(command, command, List.of());
        }
        
        ItemStack stack = new ItemStack(CommandStickItem.instance);
        stack.set(CommandStickItem.COMPONENT_TYPE, data);
        
        if (!player.getInventory().contains(stack)) {
            player.getInventory().add(stack);
        }
    }
    
    public static boolean isDragging(ServerPlayer player) {
        return of(player.server).draggingSessionMap.containsKey(player);
    }
    
    @Nullable
    public static PortalWandInteraction.OneLockDraggingResult performDragWithOneLockedAnchor(
        UnilateralPortalState originalState,
        PortalLocalXYNormalized lockedLocalPos,
        PortalLocalXYNormalized draggingLocalPos, Vec3 draggedPos,
        @Nullable Vec3 previousRotationAxis,
        boolean lockWidth, boolean lockHeight
    ) {
        Vec3 draggedPosOriginalPos = draggingLocalPos.getPos(originalState);
        Vec3 lockedPos = lockedLocalPos.getPos(originalState);
        Vec3 originalOffset = draggedPosOriginalPos.subtract(lockedPos);
        Vec3 newOffset = draggedPos.subtract(lockedPos);
        
        double newOffsetLen = newOffset.length();
        double originalOffsetLen = originalOffset.length();
        
        if (newOffsetLen < 0.00001 || originalOffsetLen < 0.00001) {
            return null;
        }
        
        Vec3 originalOffsetN = originalOffset.normalize();
        Vec3 newOffsetN = newOffset.normalize();
        
        double dot = originalOffsetN.dot(newOffsetN);
        
        DQuaternion rotation;
        if (Math.abs(dot) < 0.99999) {
            rotation = DQuaternion.getRotationBetween(originalOffset, newOffset)
                .fixFloatingPointErrorAccumulation();
        }
        else {
            // the originalOffset and newOffset are colinear
            
            if (dot > 0) {
                // the two offsets are roughly equal. no dragging
                rotation = DQuaternion.identity;
            }
            else {
                // the two offsets are opposite.
                // we cannot determine the rotation axis. the possible axises are on a plane
                
                Plane planeOfPossibleAxis = new Plane(Vec3.ZERO, originalOffsetN);
                
                // to improve user-friendliness, use the axis from the previous rotation
                if (previousRotationAxis != null) {
                    // project the previous axis onto the plane of possible axis
                    Vec3 projected = planeOfPossibleAxis.getProjection(previousRotationAxis);
                    if (projected.lengthSqr() < 0.00001) {
                        // the previous axis is perpendicular to the plane
                        // cannot determine axis
                        return null;
                    }
                    Vec3 axis = projected.normalize();
                    rotation = DQuaternion.rotationByDegrees(axis, 180)
                        .fixFloatingPointErrorAccumulation();
                }
                else {
                    rotation = DQuaternion.identity;
                }
            }
        }
        
        DQuaternion newOrientation = rotation.hamiltonProduct(originalState.orientation())
            .fixFloatingPointErrorAccumulation();
        
        PortalLocalXYNormalized deltaLocalXY = draggingLocalPos.subtract(lockedLocalPos);
        
        double newWidth;
        double newHeight;
        if (lockWidth && lockHeight) {
            newWidth = originalState.width();
            newHeight = originalState.height();
        }
        else {
            Vec3 newNormal = rotation.rotate(originalState.getNormal());
            if (lockWidth) {
                assert !lockHeight;
                newWidth = originalState.width();
                if (Math.abs(deltaLocalXY.ny()) < 0.001) {
                    newHeight = originalState.height();
                }
                else {
                    double subWidth = Math.abs(deltaLocalXY.nx()) * newWidth;
                    double diff = newOffsetLen * newOffsetLen - subWidth * subWidth;
                    if (diff < 0.000001) {
                        return null;
                    }
                    double subHeight = Math.sqrt(diff);
                    newHeight = subHeight / Math.abs(deltaLocalXY.ny());
                    if (Math.abs(subWidth) > 0.001) {
                        // if dragging on non-axis-aligned direction and aspect ratio changes,
                        // the simple rotation will not match the dragging cursor.
                        // recalculate the orientation
                        newOrientation = getOrientationByNormalDiagonalWidthHeight(
                            newNormal, newOffset, subWidth, subHeight,
                            Math.signum(deltaLocalXY.nx()), Math.signum(deltaLocalXY.ny())
                        );
                    }
                }
            }
            else if (lockHeight) {
                assert !lockWidth;
                newHeight = originalState.height();
                if (Math.abs(deltaLocalXY.nx()) < 0.001) {
                    newWidth = originalState.width();
                }
                else {
                    double subHeight = Math.abs(deltaLocalXY.ny()) * newHeight;
                    double diff = newOffsetLen * newOffsetLen - subHeight * subHeight;
                    if (diff < 0.000001) {
                        return null;
                    }
                    double subWidth = Math.sqrt(diff);
                    newWidth = subWidth / Math.abs(deltaLocalXY.nx());
                    if (Math.abs(subHeight) > 0.001) {
                        // if dragging on non-axis-aligned direction and aspect ratio changes,
                        // the simple rotation will not match the dragging cursor.
                        // recalculate the orientation
                        newOrientation = getOrientationByNormalDiagonalWidthHeight(
                            newNormal, newOffset, subWidth, subHeight,
                            Math.signum(deltaLocalXY.nx()), Math.signum(deltaLocalXY.ny())
                        );
                    }
                }
            }
            else {
                double scaling = newOffsetLen / originalOffsetLen;
                newWidth = originalState.width() * scaling;
                newHeight = originalState.height() * scaling;
            }
        }
        
        // get the new unilateral portal state by scaling and rotation
        Vec3 newLockedPosOffset = newOrientation.rotate(new Vec3(
            (lockedLocalPos.nx() - 0.5) * newWidth,
            (lockedLocalPos.ny() - 0.5) * newHeight,
            0
        ));
        Vec3 newOrigin = lockedPos.subtract(newLockedPosOffset);
        
        UnilateralPortalState newPortalState = new UnilateralPortalState(
            originalState.dimension(), newOrigin, newOrientation, newWidth, newHeight
        );
        return new OneLockDraggingResult(
            newPortalState, rotation.getRotatingAxis()
        );
    }
    
    /**
     * The sub-portal defined by the locked pos and the cursor pos
     * has the same orientation as the outer portal.
     * Get the sub-portal's orientation by normal, diagonal, width and height.
     */
    private static DQuaternion getOrientationByNormalDiagonalWidthHeight(
        Vec3 normal, Vec3 diagonal,
        double width, double height,
        double sigX, double sigY
    ) {
        Vec3 newOffsetN = diagonal.normalize();
        double newOffsetLen = diagonal.length();
        
        // the side vec is on the left side of the diagonal (right-hand rule)
        Vec3 sideVecN = normal.cross(newOffsetN).normalize();
        
        // the triangle area can be represented by two ways:
        // diagonalLen * sideVecLen / 2, or width * height / 2
        double sideVecLen = (width * height) / newOffsetLen;
        
        // the tangent can be represented by two ways:
        // width / height, or sideVecLen / wFront
        double wFront = sideVecLen * width / height;
        double hFront = sideVecLen * height / width;
        
        // if sigX and sigY are both 1,
        // then the axisW is on the right side of the diagonal (right-hand rule),
        // and axisH is on the left side of the diagonal.
        // if either sigX or sigY flips, this flips, so multiply the signum.
        Vec3 sideVecW = sideVecN.scale(-sideVecLen * sigX * sigY);
        Vec3 sideVecH = sideVecW.scale(-1);
        
        Vec3 newAxisW = newOffsetN.scale(wFront)
            .add(sideVecW).normalize()
            .scale(sigX); // make it the same direction of the outer portal
        
        Vec3 newAxisH = newOffsetN.scale(hFront)
            .add(sideVecH).normalize()
            .scale(sigY);
        
        DQuaternion newOrientation = DQuaternion.fromFacingVecs(newAxisW, newAxisH)
            .fixFloatingPointErrorAccumulation();
        return newOrientation;
    }
    
    public static record OneLockDraggingResult(
        UnilateralPortalState newState,
        Vec3 rotationAxis
    ) {
    }
    
    private record CopyingSession(
        PortalState portalState, CompoundTag portalData, boolean isCut,
        boolean hasFlipped, boolean hasReverse, boolean hasParallel
        // TODO store animation view
    ) {
    }
    
    private static final WeakHashMap<ServerPlayer, CopyingSession> copyingSessionMap = new WeakHashMap<>();
    
    public static void handleCopyCutPortal(ServerPlayer player, UUID portalId, boolean isCut) {
        Portal portal = WandUtil.getPortalByUUID(player.level(), portalId);
        
        if (portal == null) {
            player.sendSystemMessage(Component.literal("Cannot find portal " + portalId));
            return;
        }
        
        PortalState portalState = portal.getPortalState();
        CompoundTag portalData = portal.writePortalDataToNbt();
        
        Portal flipped = null;
        Portal reverse = null;
        Portal parallel = null;
        
        PortalExtension ext = PortalExtension.get(portal);
        if (ext.flippedPortal != null) {
            flipped = ext.flippedPortal;
        }
        if (ext.reversePortal != null) {
            reverse = ext.reversePortal;
        }
        if (ext.parallelPortal != null) {
            parallel = ext.parallelPortal;
        }
        
        CopyingSession copyingSession = new CopyingSession(
            portalState, portalData, isCut,
            flipped != null, reverse != null, parallel != null
        );
        copyingSessionMap.put(player, copyingSession);
        
        if (isCut) {
            portal.remove(Entity.RemovalReason.KILLED);
            if (flipped != null) {
                flipped.remove(Entity.RemovalReason.KILLED);
            }
            if (reverse != null) {
                reverse.remove(Entity.RemovalReason.KILLED);
            }
            if (parallel != null) {
                parallel.remove(Entity.RemovalReason.KILLED);
            }
        }
    }
    
    private static void handleConfirmCopyCut(ServerPlayer player, Vec3 origin, DQuaternion rawOrientation) {
        CopyingSession copyingSession = copyingSessionMap.remove(player);
        
        if (copyingSession == null) {
            player.sendSystemMessage(Component.literal("Missing copying session"));
            return;
        }
        
        DQuaternion orientation = rawOrientation.fixFloatingPointErrorAccumulation();
        
        if (player.position().distanceToSqr(origin) > 64 * 64) {
            player.sendSystemMessage(Component.literal("Too far away from the portal"));
            return;
        }
        
        Portal portal = Portal.ENTITY_TYPE.create(player.level());
        assert portal != null;
        
        portal.readPortalDataFromNbt(copyingSession.portalData);
        
        PortalState originalPortalState = copyingSession.portalState;
        
        UnilateralPortalState originalThisSide = originalPortalState.getThisSideState();
        UnilateralPortalState originalOtherSide = originalPortalState.getOtherSideState();
        
        UnilateralPortalState newThisSide = new UnilateralPortalState(
            player.level().dimension(),
            origin,
            orientation,
            originalThisSide.width(), originalThisSide.height()
        );
        
        portal.setPortalState(UnilateralPortalState.combine(
            newThisSide, originalOtherSide
        ));
        portal.resetAnimationReferenceState(true, false);
        
        if (copyingSession.isCut()) {
            McHelper.spawnServerEntity(portal);
            
            if (copyingSession.hasFlipped) {
                Portal flippedPortal = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
                flippedPortal.resetAnimationReferenceState(true, false);
                McHelper.spawnServerEntity(flippedPortal);
            }
            
            if (copyingSession.hasReverse) {
                Portal reversePortal = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
                reversePortal.resetAnimationReferenceState(false, true);
                McHelper.spawnServerEntity(reversePortal);
                
                if (copyingSession.hasParallel) {
                    Portal parallelPortal = PortalManipulation.createFlippedPortal(reversePortal, Portal.ENTITY_TYPE);
                    parallelPortal.resetAnimationReferenceState(false, true);
                    McHelper.spawnServerEntity(parallelPortal);
                }
            }
        }
        else {
            PortalExtension.get(portal).bindCluster = false;
            McHelper.spawnServerEntity(portal);
            
            if (copyingSession.hasFlipped || copyingSession.hasReverse || copyingSession.hasParallel) {
                player.sendSystemMessage(Component.translatable("imm_ptl.wand.copy.not_copying_cluster"));
                giveCommandStick(player, "/portal complete_bi_way_bi_faced_portal");
            }
        }
    }
    
    private record AnimationSession(
        ResourceKey<Level> dimension, UUID portalId, PortalState initialState, boolean editsExisting
    ) {
    }
    
    private static final int MAX_ANIMATION_KEYFRAMES = 64;
    private static final int MAX_KEYFRAME_TICKS = 1200;
    
    private static final WeakHashMap<ServerPlayer, AnimationSession> animationSessionMap = new WeakHashMap<>();
    
    public static boolean isAnimating(ServerPlayer player) {
        return animationSessionMap.containsKey(player);
    }
    
    @Nullable
    private static Portal getAnimationSessionPortal(AnimationSession session) {
        Entity entity = McHelper.getServerWorld(session.dimension()).getEntity(session.portalId());
        return entity instanceof Portal portal ? portal : null;
    }
    
    private static void clearAnimationDriversOf(Portal portal) {
        Portal holder = portal.getAnimationHolder();
        if (holder != null) {
            holder.clearAnimationDrivers(true, true);
            holder.reloadAndSyncClusterToClientNextTick();
        }
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
    
    /**
     * Puts the portal back to where the session started.
     */
    private static void abandonAnimationSession(AnimationSession session) {
        Portal portal = getAnimationSessionPortal(session);
        if (portal == null) {
            return;
        }
        
        clearAnimationDriversOf(portal);
        portal.setPortalState(session.initialState());
        portal.rectifyClusterPortals(true);
        portal.reloadAndSyncClusterToClientNextTick();
    }
    
    private static void handleStartAnimationSession(ServerPlayer player, UUID portalId) {
        Portal portal = WandUtil.getPortalByUUID(player.level(), portalId);
        
        if (portal == null) {
            player.sendSystemMessage(Component.literal("Cannot find portal " + portalId));
            return;
        }
        
        Portal holder = portal.getAnimationHolder();
        if (holder != null) {
            if (startEditingExistingAnimation(player, holder)) {
                return;
            }
            
            // The animation was not made by the wand, so it is replaced by the new one.
            holder.clearAnimationDrivers(true, true);
            holder.reloadAndSyncClusterToClientNextTick();
        }
        
        PortalState state = portal.getPortalState();
        if (state == null) {
            return;
        }
        
        animationSessionMap.put(
            player, new AnimationSession(portal.level().dimension(), portalId, state, false)
        );
        
        CompoundTag data = new CompoundTag();
        putClusterIds(data, portal);
        
        if (holder != null) {
            // The client is waiting for keyframes that the replaced animation cannot give.
            data.put("start", stateToTag(state));
            McRemoteProcedureCall.tellClientToInvoke(
                player,
                "qouteall.imm_ptl.peripheral.wand.ClientPortalWandPortalAnimate.RemoteCallables.clientAnimationNotEditable",
                data
            );
        }
        else {
            McRemoteProcedureCall.tellClientToInvoke(
                player,
                "qouteall.imm_ptl.peripheral.wand.ClientPortalWandPortalAnimate.RemoteCallables.clientSessionCluster",
                data
            );
        }
    }
    
    @Nullable
    private static NormalAnimation getSingleNormalAnimation(List<PortalAnimationDriver> drivers) {
        if (drivers.size() == 1 && drivers.get(0) instanceof NormalAnimation animation && !animation.isBuilding) {
            return animation;
        }
        return null;
    }
    
    /**
     * Returns true if the last phase is the one that the wand adds to make a looping animation return to the start.
     */
    private static boolean isGeneratedLoopBackPhase(List<NormalAnimation.Phase> phases) {
        int last = phases.size() - 1;
        
        return last >= 1
            && isLoopBackPhase(phases.get(last), phases.get(last - 1))
            && !phases.get(last - 1).delta().isIdentity();
    }
    
    private static boolean isLoopBackPhase(NormalAnimation.Phase last, NormalAnimation.Phase previous) {
        return last.delta().isIdentity()
            && last.durationTicks() == previous.durationTicks()
            && last.timingFunction() == previous.timingFunction();
    }
    
    /**
     * Stops the animation, moves the portal to its last keyframe and sends the keyframes to the client.
     * Returns false if the animation cannot be edited by the wand.
     */
    /**
     * Puts the ids of the portals that share the animation with the portal.
     * The portals on the other side can be in another dimension, so the client cannot always find them.
     */
    private static void putClusterIds(CompoundTag data, Portal portal) {
        PortalExtension extension = PortalExtension.get(portal);
        
        ListTag sameSide = new ListTag();
        ListTag otherSide = new ListTag();
        
        sameSide.add(NbtUtils.createUUID(portal.getUUID()));
        if (extension.flippedPortal != null) {
            sameSide.add(NbtUtils.createUUID(extension.flippedPortal.getUUID()));
        }
        if (extension.reversePortal != null) {
            otherSide.add(NbtUtils.createUUID(extension.reversePortal.getUUID()));
        }
        if (extension.parallelPortal != null) {
            otherSide.add(NbtUtils.createUUID(extension.parallelPortal.getUUID()));
        }
        
        data.put("sameSideIds", sameSide);
        data.put("otherSideIds", otherSide);
    }
    
    private static boolean startEditingExistingAnimation(ServerPlayer player, Portal holder) {
        PortalAnimation animation = holder.animation;
        PortalState currentState = holder.getPortalState();
        
        // A side without drivers has no reference state, and its current state is not animated.
        UnilateralPortalState thisReference = animation.thisSideReferenceState;
        UnilateralPortalState otherReference = animation.otherSideReferenceState;
        if (currentState != null) {
            if (thisReference == null && animation.thisSideAnimations.isEmpty()) {
                thisReference = currentState.getThisSideState();
            }
            if (otherReference == null && animation.otherSideAnimations.isEmpty()) {
                otherReference = currentState.getOtherSideState();
            }
        }
        
        if (thisReference == null || otherReference == null) {
            return false;
        }
        
        if (currentState != null) {
            // The current state has the linked size on the side that follows the other side's size.
            UnilateralPortalState.Builder thisBuilder = new UnilateralPortalState.Builder().from(thisReference);
            UnilateralPortalState.Builder otherBuilder = new UnilateralPortalState.Builder().from(otherReference);
            PortalAnimation.applySizeLink(animation.sizeLinkMode, currentState.scaling, thisBuilder, otherBuilder);
            thisReference = thisBuilder.build();
            otherReference = otherBuilder.build();
        }
        
        NormalAnimation thisAnimation = getSingleNormalAnimation(animation.thisSideAnimations);
        NormalAnimation otherAnimation = getSingleNormalAnimation(animation.otherSideAnimations);
        
        // A side without any driver is an empty track. A side with other kinds of drivers cannot be edited.
        if (thisAnimation == null && !animation.thisSideAnimations.isEmpty()) {
            return false;
        }
        if (otherAnimation == null && !animation.otherSideAnimations.isEmpty()) {
            return false;
        }
        if (thisAnimation == null && otherAnimation == null) {
            return false;
        }
        
        List<NormalAnimation.Phase> thisPhases =
            thisAnimation == null ? new ArrayList<>() : new ArrayList<>(thisAnimation.phases);
        List<NormalAnimation.Phase> otherPhases =
            otherAnimation == null ? new ArrayList<>() : new ArrayList<>(otherAnimation.phases);
        
        if (thisPhases.size() > MAX_ANIMATION_KEYFRAMES + 1 || otherPhases.size() > MAX_ANIMATION_KEYFRAMES + 1) {
            return false;
        }
        
        // Both sides share one loop setting.
        NormalAnimation loopSource = thisAnimation != null ? thisAnimation : otherAnimation;
        boolean loop = loopSource.loopCount >= NormalAnimation.INFINITE_THRESHOLD;
        
        if (loop && isGeneratedLoopBackPhase(thisPhases)) {
            thisPhases.remove(thisPhases.size() - 1);
        }
        if (loop && isGeneratedLoopBackPhase(otherPhases)) {
            otherPhases.remove(otherPhases.size() - 1);
        }
        
        ListTag thisKeyframes = new ListTag();
        ListTag otherKeyframes = new ListTag();
        UnilateralPortalState lastThis = phasesToKeyframes(thisReference, thisPhases, thisKeyframes);
        UnilateralPortalState lastOther = phasesToKeyframes(otherReference, otherPhases, otherKeyframes);
        
        holder.clearAnimationDrivers(true, true);
        // The end of the animation has linked sizes, so the portal is put there with the same linking.
        UnilateralPortalState.Builder lastThisBuilder = new UnilateralPortalState.Builder().from(lastThis);
        UnilateralPortalState.Builder lastOtherBuilder = new UnilateralPortalState.Builder().from(lastOther);
        PortalAnimation.applySizeLink(
            getSizeLinkMode(thisPhases, otherPhases),
            otherReference.width() / thisReference.width(),
            lastThisBuilder, lastOtherBuilder
        );
        holder.setPortalState(UnilateralPortalState.combine(lastThisBuilder.build(), lastOtherBuilder.build()));
        holder.rectifyClusterPortals(true);
        holder.reloadAndSyncClusterToClientNextTick();
        
        PortalState initialState = UnilateralPortalState.combine(thisReference, otherReference);
        
        animationSessionMap.put(
            player, new AnimationSession(holder.level().dimension(), holder.getUUID(), initialState, true)
        );
        
        CompoundTag data = new CompoundTag();
        data.putUUID("portalId", holder.getUUID());
        data.put("start", stateToTag(initialState));
        data.put("thisKeyframes", thisKeyframes);
        data.put("otherKeyframes", otherKeyframes);
        data.putBoolean("loop", loop);
        putClusterIds(data, holder);
        
        McRemoteProcedureCall.tellClientToInvoke(
            player,
            "qouteall.imm_ptl.peripheral.wand.ClientPortalWandPortalAnimate.RemoteCallables.clientBeginEdit",
            data
        );
        return true;
    }
    
    /**
     * Writes the phases as keyframe tags and returns the state after the last phase.
     * Returns the reference state if there are no phases.
     */
    private static UnilateralPortalState phasesToKeyframes(
        UnilateralPortalState reference, List<NormalAnimation.Phase> phases, ListTag out
    ) {
        UnilateralPortalState last = reference;
        
        for (NormalAnimation.Phase phase : phases) {
            last = new UnilateralPortalState.Builder().from(reference).apply(phase.delta()).build();
            
            CompoundTag tag = new CompoundTag();
            tag.put("state", last.toTag());
            tag.putInt("durationTicks", (int) phase.durationTicks());
            tag.putString("easing", phase.timingFunction().name());
            out.add(tag);
        }
        
        return last;
    }
    
    /**
     * Returns the phases of one side, or null if a keyframe is invalid.
     */
    @Nullable
    private static List<NormalAnimation.Phase> parseSidePhases(
        ServerPlayer player, UnilateralPortalState reference, ListTag keyframeList, boolean loop,
        boolean isThisSide, ResourceKey<Level> expectedDimension
    ) {
        if (keyframeList.size() > MAX_ANIMATION_KEYFRAMES) {
            player.sendSystemMessage(Component.translatable("imm_ptl.wand.animate.too_many"));
            return null;
        }
        
        List<NormalAnimation.Phase> phases = new ArrayList<>();
        
        for (int i = 0; i < keyframeList.size(); i++) {
            CompoundTag keyframe = keyframeList.getCompound(i);
            UnilateralPortalState state = UnilateralPortalState.fromTag(keyframe.getCompound("state"));
            
            if (!validateSideState(expectedDimension, state, isThisSide)) {
                player.sendSystemMessage(Component.literal("Invalid keyframe " + (i + 1)));
                LOGGER.error("Invalid animation keyframe {}", keyframe);
                return null;
            }
            
            long durationTicks = Mth.clamp(keyframe.getInt("durationTicks"), 0, MAX_KEYFRAME_TICKS);
            TimingFunction easing = parseTimingFunction(keyframe.getString("easing"));
            
            phases.add(new NormalAnimation.Phase(
                durationTicks, state.subtract(reference).purgeFPError(), easing
            ));
        }
        
        // A zero duration keyframe makes the portal jump.
        // An animation with only zero duration phases would be removed immediately,
        // so its last phase gets one tick.
        if (!phases.isEmpty() && phases.stream().allMatch(p -> p.durationTicks() == 0)) {
            NormalAnimation.Phase lastPhase = phases.get(phases.size() - 1);
            phases.set(
                phases.size() - 1,
                new NormalAnimation.Phase(1, lastPhase.delta(), lastPhase.timingFunction())
            );
        }
        
        if (loop && !phases.isEmpty()) {
            NormalAnimation.Phase last = phases.get(phases.size() - 1);
            
            // Without this the looping animation would jump back to the start.
            if (!last.delta().isIdentity()) {
                phases.add(new NormalAnimation.Phase(
                    last.durationTicks(), DeltaUnilateralPortalState.identity, last.timingFunction()
                ));
            }
        }
        
        return phases;
    }
    
    /**
     * Returns the phases of the this side and the other side, or null if a keyframe is invalid.
     * A side without keyframes gets an empty list.
     */
    @Nullable
    private static List<List<NormalAnimation.Phase>> parseAnimationPhases(
        ServerPlayer player, PortalState initialState, CompoundTag data
    ) {
        boolean loop = data.getBoolean("loop");
        
        List<NormalAnimation.Phase> thisPhases = parseSidePhases(
            player, initialState.getThisSideState(), data.getList("thisKeyframes", Tag.TAG_COMPOUND),
            loop, true, initialState.fromWorld
        );
        if (thisPhases == null) {
            return null;
        }
        
        List<NormalAnimation.Phase> otherPhases = parseSidePhases(
            player, initialState.getOtherSideState(), data.getList("otherKeyframes", Tag.TAG_COMPOUND),
            loop, false, initialState.toWorld
        );
        if (otherPhases == null) {
            return null;
        }
        
        return List.of(thisPhases, otherPhases);
    }
    
    private static boolean changesSize(List<NormalAnimation.Phase> phases) {
        for (NormalAnimation.Phase phase : phases) {
            Vec3 scaling = phase.delta().sizeScaling();
            if (scaling != null && (Math.abs(scaling.x() - 1) > 1e-4 || Math.abs(scaling.y() - 1) > 1e-4)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns which side drives the size of the other side.
     * If both sides change their size, each keeps its own size.
     */
    private static int getSizeLinkMode(
        List<NormalAnimation.Phase> thisPhases, List<NormalAnimation.Phase> otherPhases
    ) {
        boolean thisResizes = changesSize(thisPhases);
        boolean otherResizes = changesSize(otherPhases);
        
        if (thisResizes && !otherResizes) {
            return PortalAnimation.SIZE_LINK_THIS_DRIVES_OTHER;
        }
        if (otherResizes && !thisResizes) {
            return PortalAnimation.SIZE_LINK_OTHER_DRIVES_THIS;
        }
        return PortalAnimation.SIZE_LINK_NONE;
    }
    
    private static void startAnimationDrivers(
        Portal portal, PortalState initialState,
        List<NormalAnimation.Phase> thisPhases, List<NormalAnimation.Phase> otherPhases,
        boolean loopsInfinitely
    ) {
        clearAnimationDriversOf(portal);
        
        // Dragging moved the portal, so put it back to the start before the animation begins.
        portal.setPortalState(initialState);
        portal.rectifyClusterPortals(true);
        portal.animation.thisSideReferenceState = initialState.getThisSideState();
        portal.animation.otherSideReferenceState = initialState.getOtherSideState();
        portal.animation.sizeLinkMode = getSizeLinkMode(thisPhases, otherPhases);
        
        // Both sides start from the same clock so that pausing and resuming keeps them linked.
        long startingGameTime = portal.getAnimationEffectiveTime();
        int loopCount = loopsInfinitely ? NormalAnimation.INFINITE_THRESHOLD : 1;
        
        if (!thisPhases.isEmpty()) {
            portal.addThisSideAnimationDriver(new NormalAnimation.Builder()
                .phases(thisPhases)
                .startingGameTime(startingGameTime)
                .loopCount(loopCount)
                .holdsAtEnd(!loopsInfinitely)
                .build()
            );
        }
        if (!otherPhases.isEmpty()) {
            portal.addOtherSideAnimationDriver(new NormalAnimation.Builder()
                .phases(otherPhases)
                .startingGameTime(startingGameTime)
                .loopCount(loopCount)
                .holdsAtEnd(!loopsInfinitely)
                .build()
            );
        }
    }
    
    private static final int MAX_LINKED_PORTALS = 16;
    
    // The ids of the linked portals that the animation preview of the player is playing on.
    private static final WeakHashMap<ServerPlayer, List<UUID>> previewLinkedMap = new WeakHashMap<>();
    
    @Nullable
    private static Portal findPortalInAnyDimension(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Portal portal) {
                return portal;
            }
        }
        return null;
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
    
    /**
     * Returns the state that the portal has when no animation moves it.
     * The animation drivers must not be cleared before calling this.
     */
    @Nullable
    private static PortalState getRestingState(Portal portal) {
        PortalState current = portal.getPortalState();
        if (current == null || !portal.animation.hasAnimationDriver()) {
            return current;
        }
        
        UnilateralPortalState thisSide = portal.animation.thisSideReferenceState;
        UnilateralPortalState otherSide = portal.animation.otherSideReferenceState;
        return UnilateralPortalState.combine(
            thisSide == null ? current.getThisSideState() : thisSide,
            otherSide == null ? current.getOtherSideState() : otherSide
        );
    }
    
    private static boolean phasesFit(PortalState rest, List<NormalAnimation.Phase> phases, boolean isThisSide) {
        UnilateralPortalState reference = isThisSide ? rest.getThisSideState() : rest.getOtherSideState();
        ResourceKey<Level> dimension = isThisSide ? rest.fromWorld : rest.toWorld;
        
        for (NormalAnimation.Phase phase : phases) {
            UnilateralPortalState state = new UnilateralPortalState.Builder()
                .from(reference).apply(phase.delta()).build();
            if (!validateSideState(dimension, state, isThisSide)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Plays the same phases on the linked portals, starting from the resting state of each of them.
     * The phases are deltas, so the linked portals move by the same amounts as the session portal.
     * A linked portal that already has an animation gets it replaced.
     * The preview remembers the portals so that stopping it can put them back.
     */
    private static void applyAnimationToLinkedPortals(
        ServerPlayer player, Portal sessionPortal, CompoundTag data,
        List<NormalAnimation.Phase> thisPhases, List<NormalAnimation.Phase> otherPhases,
        boolean loopsInfinitely, boolean isPreview
    ) {
        ListTag idList = data.getList("linkedIds", Tag.TAG_INT_ARRAY);
        if (idList.isEmpty()) {
            return;
        }
        
        Set<UUID> coveredIds = getClusterIds(sessionPortal);
        List<UUID> appliedIds = new ArrayList<>();
        int skipped = 0;
        
        for (int i = 0; i < Math.min(idList.size(), MAX_LINKED_PORTALS); i++) {
            UUID id = NbtUtils.loadUUID(idList.get(i));
            if (coveredIds.contains(id)) {
                continue;
            }
            
            Portal linked = findPortalInAnyDimension(player.server, id);
            if (linked == null) {
                skipped++;
                continue;
            }
            
            // The animation is driven by one portal of the cluster, so the animated one is the target.
            Portal target = linked.getAnimationHolder();
            if (target == null) {
                target = linked;
            }
            
            coveredIds.addAll(getClusterIds(linked));
            coveredIds.addAll(getClusterIds(target));
            
            PortalState rest = getRestingState(target);
            if (rest == null || !phasesFit(rest, thisPhases, true) || !phasesFit(rest, otherPhases, false)) {
                skipped++;
                continue;
            }
            
            startAnimationDrivers(
                target, rest, new ArrayList<>(thisPhases), new ArrayList<>(otherPhases), loopsInfinitely
            );
            appliedIds.add(target.getUUID());
        }
        
        if (isPreview) {
            previewLinkedMap.put(player, appliedIds);
        }
        else if (!appliedIds.isEmpty()) {
            player.sendSystemMessage(Component.translatable("imm_ptl.wand.animate.linked_applied", appliedIds.size()));
        }
        
        if (skipped > 0) {
            player.sendSystemMessage(Component.translatable("imm_ptl.wand.animate.linked_skipped", skipped));
        }
    }
    
    /**
     * Stops the preview on the linked portals and puts them back to their resting states.
     */
    private static void restoreLinkedPreview(ServerPlayer player) {
        List<UUID> ids = previewLinkedMap.remove(player);
        if (ids == null) {
            return;
        }
        
        for (UUID id : ids) {
            Portal portal = findPortalInAnyDimension(player.server, id);
            if (portal == null) {
                continue;
            }
            
            PortalState rest = getRestingState(portal);
            clearAnimationDriversOf(portal);
            if (rest != null) {
                portal.setPortalState(rest);
                portal.rectifyClusterPortals(true);
                portal.reloadAndSyncClusterToClientNextTick();
            }
        }
    }
    
    private static void handleFinishAnimationSession(ServerPlayer player, CompoundTag data) {
        AnimationSession session = animationSessionMap.remove(player);
        restoreLinkedPreview(player);
        
        if (session == null) {
            return;
        }
        
        Portal portal = getAnimationSessionPortal(session);
        if (portal == null) {
            player.sendSystemMessage(Component.literal("Cannot find portal " + session.portalId()));
            return;
        }
        
        ListTag thisKeyframes = data.getList("thisKeyframes", Tag.TAG_COMPOUND);
        ListTag otherKeyframes = data.getList("otherKeyframes", Tag.TAG_COMPOUND);
        boolean loop = data.getBoolean("loop");
        
        if (thisKeyframes.isEmpty() && otherKeyframes.isEmpty()) {
            if (session.editsExisting()) {
                clearAnimationDriversOf(portal);
                portal.setPortalState(session.initialState());
                portal.rectifyClusterPortals(true);
                portal.reloadAndSyncClusterToClientNextTick();
                player.sendSystemMessage(Component.translatable("imm_ptl.wand.animate.removed"));
            }
            else {
                player.sendSystemMessage(Component.translatable("imm_ptl.wand.animate.empty"));
            }
            return;
        }
        
        List<List<NormalAnimation.Phase>> phases = parseAnimationPhases(player, session.initialState(), data);
        if (phases == null) {
            return;
        }
        
        startAnimationDrivers(portal, session.initialState(), phases.get(0), phases.get(1), loop);
        applyAnimationToLinkedPortals(player, portal, data, phases.get(0), phases.get(1), loop, false);
        
        player.sendSystemMessage(Component.translatable(
            "imm_ptl.wand.animate.created", thisKeyframes.size() + otherKeyframes.size()
        ));
        giveCommandStick(player, "/portal animation remove_and_reset");
        if (!loop) {
            giveCommandStick(player, "/portal animation replay");
        }
    }
    
    private static void handlePreviewAnimationSession(ServerPlayer player, CompoundTag data) {
        AnimationSession session = animationSessionMap.get(player);
        if (session == null) {
            return;
        }
        
        Portal portal = getAnimationSessionPortal(session);
        if (portal == null
            || (data.getList("thisKeyframes", Tag.TAG_COMPOUND).isEmpty()
            && data.getList("otherKeyframes", Tag.TAG_COMPOUND).isEmpty())
        ) {
            return;
        }
        
        List<List<NormalAnimation.Phase>> phases = parseAnimationPhases(player, session.initialState(), data);
        if (phases == null) {
            return;
        }
        
        // The preview plays once even if the animation loops.
        restoreLinkedPreview(player);
        startAnimationDrivers(portal, session.initialState(), phases.get(0), phases.get(1), false);
        applyAnimationToLinkedPortals(player, portal, data, phases.get(0), phases.get(1), false, true);
    }
    
    private static void handleSetAnimationSessionPortalState(
        ServerPlayer player, CompoundTag data, boolean stopsPreview
    ) {
        AnimationSession session = animationSessionMap.get(player);
        if (session == null) {
            return;
        }
        
        Portal portal = getAnimationSessionPortal(session);
        if (portal == null) {
            return;
        }
        
        if (stopsPreview) {
            restoreLinkedPreview(player);
            clearAnimationDriversOf(portal);
        }
        
        if (!data.contains("thisSide") || !data.contains("otherSide")) {
            return;
        }
        
        PortalState state = tagToState(data);
        if (!validateAnimationKeyframe(session.initialState(), state.getThisSideState(), state.getOtherSideState())) {
            LOGGER.error("Invalid animation session portal state {}", data);
            return;
        }
        
        portal.setPortalState(state);
        portal.rectifyClusterPortals(true);
        portal.reloadAndSyncClusterToClientNextTick();
    }
    
    private static TimingFunction parseTimingFunction(String name) {
        try {
            return TimingFunction.valueOf(name);
        }
        catch (IllegalArgumentException e) {
            return TimingFunction.sine;
        }
    }
    
    private static boolean validateAnimationKeyframe(
        PortalState initialState,
        UnilateralPortalState thisState, UnilateralPortalState otherState
    ) {
        return validateSideState(initialState.fromWorld, thisState, true)
            && validateSideState(initialState.toWorld, otherState, false);
    }
    
    private static boolean validateSideState(
        ResourceKey<Level> expectedDimension, UnilateralPortalState state, boolean isThisSide
    ) {
        if (state.dimension() != expectedDimension) {
            return false;
        }
        
        if (!Double.isFinite(state.width()) || !Double.isFinite(state.height())) {
            return false;
        }
        
        if (isThisSide) {
            if (state.width() < 0.05 || state.width() > 64.1) {
                return false;
            }
            if (state.height() < 0.05 || state.height() > 64.1) {
                return false;
            }
        }
        else if (state.width() <= 0 || state.height() <= 0) {
            return false;
        }
        
        DQuaternion orientation = state.orientation();
        if (!Double.isFinite(orientation.x) || !Double.isFinite(orientation.y)
            || !Double.isFinite(orientation.z) || !Double.isFinite(orientation.w)
        ) {
            return false;
        }
        
        Vec3 pos = state.position();
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z);
    }
    
    private static void handleClearPortalClipboard(ServerPlayer player) {
        copyingSessionMap.remove(player);
    }
    
    
}
