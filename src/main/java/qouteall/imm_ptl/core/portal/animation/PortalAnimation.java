package qouteall.imm_ptl.core.portal.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.List;

public class PortalAnimation {
    /**
     * The default animation is triggered when changing portal
     * The default animation is client-only. On the server side it changes abruptly.
     * The default animation cannot do relative teleportation.
     */
    @NotNull
    public DefaultPortalAnimation defaultAnimation = DefaultPortalAnimation.createDefault();
    
    /**
     * The animation driver controls the real animation.
     * The real animation runs on both sides and can do relative teleportation.
     */
    @NotNull
    public List<PortalAnimationDriver> thisSideAnimations = new ArrayList<>();
    @NotNull
    public List<PortalAnimationDriver> otherSideAnimations = new ArrayList<>();
    
    public long pauseTime = 0;
    public long timeOffset = 0;
    
    public static final int SIZE_LINK_NONE = 0;
    public static final int SIZE_LINK_THIS_DRIVES_OTHER = 1;
    public static final int SIZE_LINK_OTHER_DRIVES_THIS = 2;
    
    /**
     * A portal has a single scaling between its two sides.
     * When only one side animates its size, the other side would keep its width and stretch its height.
     * This mode makes the side without size animation follow the size of the other side
     * at the scaling that the portal has.
     */
    public int sizeLinkMode = SIZE_LINK_NONE;
    
    @Nullable
    public UnilateralPortalState thisSideReferenceState;
    @Nullable
    public UnilateralPortalState otherSideReferenceState;
    
    @Nullable
    private UnilateralPortalState pausedThisSideState;
    @Nullable
    private UnilateralPortalState pausedOtherSideState;
    
    @Nullable
    public PortalState lastTickAnimatedState;
    @Nullable
    public PortalState thisTickAnimatedState;
    public long updateCounter;
    
    // for client player teleportation
    @Nullable
    public PortalState clientLastFramePortalState;
    public long clientLastFramePortalStateCounter = -1;
    @Nullable
    public PortalState clientCurrentFramePortalState;
    public long clientCurrentFramePortalStateCounter = -1;
    
    public void readFromTag(CompoundTag tag) {
        if (tag.contains("animation")) {
            defaultAnimation = DefaultPortalAnimation.fromNbt(tag.getCompoundOrEmpty("animation"));
        }
        else if (tag.contains("defaultAnimation")) {
            defaultAnimation = DefaultPortalAnimation.fromNbt(tag.getCompoundOrEmpty("defaultAnimation"));
        }
        else {
            defaultAnimation = DefaultPortalAnimation.createDefault();
        }
        
        if (tag.contains("thisSideAnimations")) {
            ListTag listTag = tag.getListOrEmpty("thisSideAnimations");
            thisSideAnimations = Helper.listTagToList(listTag, PortalAnimationDriver::fromTag);
        }
        else {
            thisSideAnimations.clear();
        }
        
        if (tag.contains("otherSideAnimations")) {
            ListTag listTag = tag.getListOrEmpty("otherSideAnimations");
            otherSideAnimations = Helper.listTagToList(listTag, PortalAnimationDriver::fromTag);
        }
        else {
            otherSideAnimations.clear();
        }
        
        if (tag.contains("pauseTime")) {
            pauseTime = tag.getLongOr("pauseTime", 0L);
        }
        else {
            pauseTime = 0;
        }
        
        if (tag.contains("timeOffset")) {
            timeOffset = tag.getLongOr("timeOffset", 0L);
        }
        else {
            timeOffset = 0;
        }
        
        sizeLinkMode = tag.getIntOr("sizeLinkMode", 0);
        
        if (tag.contains("thisSideReferenceState")) {
            thisSideReferenceState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("thisSideReferenceState"));
        }
        else {
            thisSideReferenceState = null;
        }
        
        if (tag.contains("otherSideReferenceState")) {
            otherSideReferenceState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("otherSideReferenceState"));
        }
        else {
            otherSideReferenceState = null;
        }
        
        if (tag.contains("pausedThisSideState")) {
            pausedThisSideState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("pausedThisSideState"));
        }
        else {
            pausedThisSideState = null;
        }
        
        if (tag.contains("pausedOtherSideState")) {
            pausedOtherSideState = UnilateralPortalState.fromTag(tag.getCompoundOrEmpty("pausedOtherSideState"));
        }
        else {
            pausedOtherSideState = null;
        }
    }
    
    public void writeToTag(CompoundTag tag) {
        tag.put("defaultAnimation", defaultAnimation.toNbt());
        
        if (!thisSideAnimations.isEmpty()) {
            tag.put(
                "thisSideAnimations",
                Helper.listToListTag(thisSideAnimations, PortalAnimationDriver::toTag)
            );
        }
        
        if (!otherSideAnimations.isEmpty()) {
            tag.put(
                "otherSideAnimations",
                Helper.listToListTag(otherSideAnimations, PortalAnimationDriver::toTag)
            );
        }
        
        if (pauseTime != 0) {
            tag.putLong("pauseTime", pauseTime);
        }
        if (timeOffset != 0) {
            tag.putLong("timeOffset", timeOffset);
        }
        if (sizeLinkMode != SIZE_LINK_NONE) {
            tag.putInt("sizeLinkMode", sizeLinkMode);
        }
        
        if (thisSideReferenceState != null) {
            tag.put("thisSideReferenceState", thisSideReferenceState.toTag());
        }
        if (otherSideReferenceState != null) {
            tag.put("otherSideReferenceState", otherSideReferenceState.toTag());
        }
        
        if (pausedThisSideState != null) {
            tag.put("pausedThisSideState", pausedThisSideState.toTag());
        }
        if (pausedOtherSideState != null) {
            tag.put("pausedOtherSideState", pausedOtherSideState.toTag());
        }
    }
    
    public boolean isRoughlyRunningAnimation() {
        return lastTickAnimatedState != null || thisTickAnimatedState != null || hasRunningAnimationDriver();
    }
    
    public boolean hasRunningAnimationDriver() {
        return !isPaused() && hasAnimationDriver();
    }
    
    public boolean hasAnimationDriver() {
        return !thisSideAnimations.isEmpty() || !otherSideAnimations.isEmpty();
    }
    
    public boolean isPaused() {
        return pauseTime != 0;
    }
    
    public void setPaused(Portal portal, boolean paused) {
        if (paused == isPaused()) {
            return;
        }
        
        if (paused) {
            pauseTime = portal.level().getGameTime();
            PortalState portalState = portal.getPortalState();
            assert portalState != null;
            pausedThisSideState = portalState.getThisSideState();
            pausedOtherSideState = portalState.getOtherSideState();
        }
        else {
            timeOffset -= portal.level().getGameTime() - pauseTime;
            pauseTime = 0;
            
            if (pausedThisSideState != null && pausedOtherSideState != null) {
                PortalState currentState = portal.getPortalState();
                assert currentState != null;
                UnilateralPortalState currentThisSideState = currentState.getThisSideState();
                UnilateralPortalState currentOtherSideState = currentState.getOtherSideState();
                DeltaUnilateralPortalState thisSideDelta = currentThisSideState.subtract(pausedThisSideState);
                DeltaUnilateralPortalState otherSideDelta = currentOtherSideState.subtract(pausedOtherSideState);
                if (thisSideReferenceState != null) {
                    thisSideReferenceState = thisSideReferenceState.apply(thisSideDelta);
                }
                if (otherSideReferenceState != null) {
                    otherSideReferenceState = otherSideReferenceState.apply(otherSideDelta);
                }
                pausedThisSideState = null;
                pausedOtherSideState = null;
            }
        }
        
        PortalExtension.forClusterPortals(portal, Portal::reloadAndSyncToClientNextTick);
    }
    
    public void setBackToPausingState(Portal portal) {
        if (this.pausedThisSideState != null && this.pausedOtherSideState != null) {
            // put the portal back to pausing state, avoid moving the reference state when resuming
            portal.setPortalState(UnilateralPortalState.combine(
                this.pausedThisSideState, this.pausedOtherSideState
            ));
        }
    }
    
    public long getEffectiveTime(long gameTime) {
        return (isPaused() ? pauseTime : gameTime) + timeOffset;
    }
    
    public void tick(Portal portal) {
        swapTickRelativeStateIfNeeded(portal);
        
        if (!portal.level().isClientSide()) {
            /*
              The server ticking process {@link ServerLevel#tick(BooleanSupplier)}:
              1. increase game time
              2. tick entities (including portals)
              So use 1 as partial tick
             */
            updateAnimationDriver(
                portal, portal.animation, portal.level().getGameTime(), 1, true, true
            );
            
            if (thisSideAnimations.isEmpty()) {
                thisSideReferenceState = null;
            }
            if (otherSideAnimations.isEmpty()) {
                otherSideReferenceState = null;
            }

//            if (thisSideAnimations.isEmpty() && otherSideAnimations.isEmpty()) {
//                timeOffset = 0;
//            }
            
            if (!hasAnimationDriver()) {
                // when having no animation, don't pause
                setPaused(portal, false);
                sizeLinkMode = SIZE_LINK_NONE;
            }
        }
        else {
            // handled on client
            if (hasAnimationDriver() && !isPaused()) {
                markRequiresClientAnimationUpdate(portal);
            }
        }
    }
    
    /**
     * There should not be any assumption about ticking order between entities in one tick.
     * We need to make sure that it works the same whether the driver portal ticks first or not.
     */
    public void swapTickRelativeStateIfNeeded(Portal portal) {
        int counter = portal.tickCount;
        if (counter != updateCounter) {
            lastTickAnimatedState = thisTickAnimatedState;
            thisTickAnimatedState = null;
            updateCounter = counter;
        }
    }
    
    private void provideThisTickState(Portal portal, PortalState portalState) {
        swapTickRelativeStateIfNeeded(portal);
        if (thisTickAnimatedState != null) {
            if (!portal.level().isClientSide()) {
                Helper.log("Conflicting animation in " + portal);
                portal.clearAnimationDrivers(true, true);
                thisTickAnimatedState = null;
                return;
            }
        }
        thisTickAnimatedState = portalState;
    }
    
    @Environment(EnvType.CLIENT)
    private static void markRequiresClientAnimationUpdate(Portal portal) {
        ClientPortalAnimationManagement.markRequiresCustomAnimationUpdate(portal);
    }
    
    public void updateAnimationDriver(
        Portal portal,
        PortalAnimation animation,
        long gameTime,
        float partialTicks,
        boolean isTicking,
        boolean canRemoveAnimation
    ) {
        if (!hasAnimationDriver()) {
            return;
        }
        
        if (isPaused()) {
            return;
        }
        
        PortalState portalState = portal.getPortalState();
        if (portalState == null) {
            return;
        }
        
        initializeReferenceStates(portalState);
        assert thisSideReferenceState != null;
        assert otherSideReferenceState != null;
        
        long effectiveGameTime = animation.getEffectiveTime(gameTime);
        float effectivePartialTicks = animation.isPaused() ? 0 : partialTicks;
        
        UnilateralPortalState.Builder thisSideState = new UnilateralPortalState.Builder().from(thisSideReferenceState);
        UnilateralPortalState.Builder otherSideState = new UnilateralPortalState.Builder().from(otherSideReferenceState);
        
        int originalThisSideAnimationCount = thisSideAnimations.size();
        int originalOtherSideAnimationCount = otherSideAnimations.size();
        
        AnimationContext context = new AnimationContext(portal.level().isClientSide(), isTicking);
        
        thisSideAnimations.removeIf(animationDriver -> {
            AnimationResult animationResult = animationDriver.getAnimationResult(effectiveGameTime, effectivePartialTicks, context);
            
            if (animationResult.delta() != null) {
                thisSideState.apply(animationResult.delta());
            }
            
            boolean animationRemoved = canRemoveAnimation && animationResult.isFinished();
            
            if (animationRemoved) {
                if (animationResult.delta() != null) {
                    assert thisSideReferenceState != null;
                    thisSideReferenceState = new UnilateralPortalState.Builder()
                        .from(thisSideReferenceState)
                        .apply(animationResult.delta())
                        .build();
                }
            }
            
            return animationRemoved;
        });
        
        otherSideAnimations.removeIf(animationDriver -> {
            AnimationResult animationResult = animationDriver.getAnimationResult(effectiveGameTime, effectivePartialTicks, context);
            
            if (animationResult.delta() != null) {
                otherSideState.apply(animationResult.delta());
            }
            
            boolean animationRemoved = canRemoveAnimation && animationResult.isFinished();
            
            if (animationRemoved) {
                if (animationResult.delta() != null) {
                    assert otherSideReferenceState != null;
                    otherSideReferenceState = new UnilateralPortalState.Builder()
                        .from(otherSideReferenceState)
                        .apply(animationResult.delta())
                        .build();
                }
            }
            
            return animationRemoved;
        });
        
        applySizeLink(sizeLinkMode, portalState.scaling, thisSideState, otherSideState);
        
        if (thisSideState.dimension != portalState.fromWorld || otherSideState.dimension != portalState.toWorld) {
            Helper.err("Portal animation driver cannot change dimension");
            if (!portal.level().isClientSide()) {
                portal.clearAnimationDrivers(true, true);
            }
            return;
        }
        
        UnilateralPortalState newThisSideState = thisSideState.build();
        UnilateralPortalState newOtherSideState = otherSideState.build();
        PortalState newPortalState =
            UnilateralPortalState.combine(newThisSideState, newOtherSideState);
        portal.setPortalState(newPortalState);
        
        if (isTicking) {
            provideThisTickState(portal, newPortalState);
        }
        
        portal.rectifyClusterPortals(false);
        if (isTicking) {
            PortalExtension.forConnectedPortals(
                portal,
                p -> p.animation.provideThisTickState(p, p.getPortalState())
            );
        }
        
        if (!portal.level().isClientSide()) {
            if ((thisSideAnimations.size() != originalThisSideAnimationCount ||
                otherSideAnimations.size() != originalOtherSideAnimationCount)
            ) {
                // delay a little to make client animation to stop smoother
                PortalExtension.forClusterPortals(portal, p -> p.reloadAndSyncToClientWithTickDelay(1));
            }
        }
    }
    
    /**
     * Restarts the normal animations from their first frame.
     * Returns false if there is no animation that can restart.
     */
    public boolean restartNormalAnimations(Portal portal) {
        long startingGameTime = getEffectiveTime(portal.level().getGameTime());
        
        boolean restarted = restartNormalAnimations(thisSideAnimations, startingGameTime);
        restarted |= restartNormalAnimations(otherSideAnimations, startingGameTime);
        return restarted;
    }
    
    private static boolean restartNormalAnimations(
        List<PortalAnimationDriver> drivers, long startingGameTime
    ) {
        boolean restarted = false;
        
        for (int i = 0; i < drivers.size(); i++) {
            if (drivers.get(i) instanceof NormalAnimation animation && !animation.isBuilding) {
                drivers.set(i, new NormalAnimation.Builder()
                    .from(animation)
                    .startingGameTime(startingGameTime)
                    .build()
                );
                restarted = true;
            }
        }
        
        return restarted;
    }
    
    public void initializeReferenceStates(PortalState portalState) {
        if (thisSideReferenceState == null) {
            thisSideReferenceState = UnilateralPortalState.extractThisSide(portalState);
        }
        if (otherSideReferenceState == null) {
            otherSideReferenceState = UnilateralPortalState.extractOtherSide(portalState);
        }
    }
    
    public void resetReferenceState(Portal portal, boolean thisSide, boolean otherSide) {
        PortalState portalState = portal.getPortalState();
        assert portalState != null;
        if (thisSide && thisSideReferenceState != null) {
            thisSideReferenceState = UnilateralPortalState.extractThisSide(portalState);
        }
        if (otherSide && otherSideReferenceState != null) {
            otherSideReferenceState = UnilateralPortalState.extractOtherSide(portalState);
        }
    }
    
    public void clearAnimationDrivers(Portal portal, boolean clearThisSide, boolean clearOtherSide) {
        Validate.isTrue(!portal.level().isClientSide());
        
        setPaused(portal, false);
        
        if (thisSideAnimations.isEmpty() && otherSideAnimations.isEmpty()) {
            return;
        }
        
        AnimationContext context = new AnimationContext(portal.level().isClientSide(), true);
        
        PortalState portalState = portal.getPortalState();
        assert portalState != null;
        UnilateralPortalState.Builder from = new UnilateralPortalState.Builder()
            .from(UnilateralPortalState.extractThisSide(portalState));
        UnilateralPortalState.Builder to = new UnilateralPortalState.Builder()
            .from(UnilateralPortalState.extractOtherSide(portalState));
        
        applyEndingState(portal, clearThisSide, clearOtherSide, context, from, to);
        
        if (clearThisSide && clearOtherSide) {
            applySizeLink(sizeLinkMode, portalState.scaling, from, to);
        }
        sizeLinkMode = SIZE_LINK_NONE;
        
        if (clearThisSide) {
            thisSideReferenceState = null;
            thisSideAnimations.clear();
        }
        if (clearOtherSide) {
            otherSideReferenceState = null;
            otherSideAnimations.clear();
        }
        
        PortalState newState = UnilateralPortalState.combine(from.build(), to.build());
        portal.setPortalState(newState);
        
        PortalExtension.get(portal).rectifyClusterPortals(portal, true);
    }
    
    private void applyEndingState(
        Portal portal,
        boolean includeThisSide, boolean includeOtherSide,
        AnimationContext context,
        UnilateralPortalState.Builder from, UnilateralPortalState.Builder to
    ) {
        long effectiveGameTime = portal.level().getGameTime() + timeOffset;
        if (includeThisSide) {
            for (PortalAnimationDriver animationDriver : thisSideAnimations) {
                DeltaUnilateralPortalState endingResult = animationDriver.getEndingResult(effectiveGameTime, context);
                if (endingResult != null) {
                    from.apply(endingResult);
                }
            }
        }
        
        if (includeOtherSide) {
            for (PortalAnimationDriver animationDriver : otherSideAnimations) {
                DeltaUnilateralPortalState endingResult = animationDriver.getEndingResult(effectiveGameTime, context);
                if (endingResult != null) {
                    to.apply(endingResult);
                }
            }
        }
    }
    
    /**
     * Sets the size of the side that has no size animation from the size of the other side.
     * The scaling is the one that the portal has before the animation changes the sizes.
     */
    public static void applySizeLink(
        int mode, double scaling,
        UnilateralPortalState.Builder thisSide, UnilateralPortalState.Builder otherSide
    ) {
        if (mode == SIZE_LINK_THIS_DRIVES_OTHER) {
            otherSide.width(thisSide.width * scaling)
                .height(thisSide.height * scaling)
                .thickness(thisSide.thickness * scaling);
        }
        else if (mode == SIZE_LINK_OTHER_DRIVES_THIS) {
            thisSide.width(otherSide.width / scaling)
                .height(otherSide.height / scaling)
                .thickness(otherSide.thickness / scaling);
        }
    }
    
    public PortalState getAnimationEndingState(Portal portal) {
        PortalState portalState = portal.getPortalState();
        assert portalState != null;
        
        initializeReferenceStates(portalState);
        assert thisSideReferenceState != null;
        assert otherSideReferenceState != null;
        
        UnilateralPortalState.Builder from = new UnilateralPortalState.Builder()
            .from(thisSideReferenceState);
        UnilateralPortalState.Builder to = new UnilateralPortalState.Builder()
            .from(otherSideReferenceState);
        
        applyEndingState(
            portal, true, true,
            new AnimationContext(portal.level().isClientSide(), true),
            from, to
        );
        
        applySizeLink(sizeLinkMode, portalState.scaling, from, to);
        
        return UnilateralPortalState.combine(from.build(), to.build());
    }
    
    @Environment(EnvType.CLIENT)
    public void updateClientState(Portal portal, long currentTeleportationCounter) {
        if (currentTeleportationCounter == clientCurrentFramePortalStateCounter) {
            return;
        }
        
        if (isRoughlyRunningAnimation()) {
            clientLastFramePortalState = clientCurrentFramePortalState;
            clientLastFramePortalStateCounter = clientCurrentFramePortalStateCounter;
            
            clientCurrentFramePortalState = portal.getPortalState();
            clientCurrentFramePortalStateCounter = currentTeleportationCounter;
        }
    }
    
    public Component getInfo(Portal portal, boolean reverse) {
        MutableComponent component = Component.literal("");
        
        List<PortalAnimationDriver> l1 = reverse ? otherSideAnimations : thisSideAnimations;
        List<PortalAnimationDriver> l2 = reverse ? thisSideAnimations : otherSideAnimations;
        
        component.append(Component.literal("This Side:\n"));
        for (int i = 0; i < l1.size(); i++) {
            PortalAnimationDriver animation = l1.get(i);
            component.append(
                Component.literal("[%d]: ".formatted(i))
                    .withStyle(ChatFormatting.GOLD)
                    .append(animation.getInfo())
                    .append("\n")
            );
        }
        
        component.append(Component.literal("Other Side:\n"));
        for (int i = 0; i < l2.size(); i++) {
            PortalAnimationDriver animation = l2.get(i);
            component.append(
                Component.literal("[%d]: ".formatted(i))
                    .withStyle(ChatFormatting.GOLD)
                    .append(animation.getInfo())
                    .append("\n")
            );
        }
        
        return component;
    }
}
