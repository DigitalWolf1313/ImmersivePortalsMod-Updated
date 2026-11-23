---
title: "Animate Portal Wand"
category: "Portals"
order: 6
description: "Record portal animations by dragging the portal and adding keyframes."
updated: 2026-11-21
manual: true
---

The animate mode of the [portal wand](wiki:portal-customization#portal-wand) records a portal animation from keyframes. You drag the portal to a state and add a keyframe, then repeat. When you finish, the wand builds a [normal animation](wiki:portal-animation#normal-animation) and plays it. [Relative teleportation](wiki:portal-animation#relative-teleportation) works with it.

## Quick Start

1. Press shift + right-click with the portal wand until the mode is Animate Portal.
2. Right-click a portal to start recording. The state of the portal at this moment is the implicit first keyframe.
3. Drag the portal to where it should go, like in [Drag Portal Mode](wiki:portal-customization#drag-portal-mode). Then press shift + right-click to add a keyframe.
4. Repeat step 3 for as many keyframes as you need.
5. Press `Y` to preview the animation. Press `ESC` to finish. The animation is created and starts playing.

While recording, the screen shows the keyframes of each side, the selected keyframe and the controls.

## Keyframes

A keyframe stores the state of one side of the portal. It has two settings:

- **Time**: how long the portal takes to move from the previous keyframe to this one. A new keyframe takes 1 second. The time can be 0 to 60 seconds. A keyframe with time 0 makes the portal jump.
- **Easing**: how the speed changes during that movement. A new keyframe copies the easing of the selected keyframe. The default is `easeInOutCubic`.

A track holds at most 64 keyframes. These are the default controls. They can be changed in the controls menu in the "Immersive Portals" category.

| Action | Key |
| --- | --- |
| Add keyframe | shift + right-click |
| Remove selected keyframe | shift + left-click or `Delete` |
| Select previous / next keyframe | `Down` / `Up` |
| Change keyframe time by 1 second | `-` and `=` (or keypad `-` and `+`) |
| Change keyframe time by 0.05 seconds | scroll wheel |
| Cycle the easing of the keyframe | `G` |
| Toggle looping | `N` |
| Replace the keyframe with the portal's current state | `U` |
| Preview the animation (press again to stop) | `Y` |
| Link or unlink the aimed portal | `J` |

## Easing

![Easing curves](images/easing-curves.png)

The easing of a keyframe applies to the movement that arrives at that keyframe. Press `G` to cycle through these:

| Easing | Speed |
| --- | --- |
| `linear` | Constant. |
| `sine` | Starts fast and ends slowly. |
| `sineFlipped` | Starts slowly and ends fast. |
| `circle` | Starts very abruptly and ends slowly. |
| `easeInOutCubic` | Starts and ends slowly. |

## Looping

Press `N` to toggle looping. The screen shows whether looping is on. A looping animation gets an extra phase that returns the portal to its start. The extra phase uses the time and the easing of the last keyframe. It is not added when the last keyframe is already at the start state.

## Two-Sided Portals

A bi-way bi-faced portal has two sides. The screen calls them Entrance and Exit. Each side has its own keyframes, times and easings. Right-click the portal of the other side to edit that side. The loop setting and the clock are shared by both sides.

A portal has one scaling for both sides. If only one side animates its size, the other side would keep its width and stretch its height. The wand avoids that by making the side without size animation follow the size of the other side.

## Linking Portals

Aim at a portal and press `J` to link it. Press `J` again to unlink it. When you finish, every linked portal gets the same animation and its own animation is replaced. Linked portals whose shape does not fit the animation are skipped, and the game tells you how many. Up to 16 portals can be linked.

in the image below, two portals are linked and both will go up like the main center portal.
![Three portals linked to one animation](images/wand_animate_links.png)

## Editing and Removing

Right-click a portal that already has an animation to edit that animation. Press `U` to replace the selected keyframe with the current state of the portal.

To replay or remove an animation, use `/portal animation replay` and `/portal animation remove_and_reset`. The command stick has the same functions.

## Examples

![Example animation graphs](images/animate-wand-example.png)

### Example 1: Sliding Portal

A portal that slides 4 blocks to the side and back, forever. It needs one keyframe.

1. Create a portal with the Create Portal mode.
2. Switch to the Animate Portal mode and right-click the portal.
3. Hold right-click and drag the portal 4 blocks to the side.
4. Press shift + right-click to add the keyframe.
5. Press `=` twice to make the keyframe take 3 seconds.
6. Check that looping is on. Press `N` if it is off.
7. Press `ESC` to finish.

The portal slides out in 3 seconds. The return phase slides it back in 3 seconds. Both movements use `easeInOutCubic`, so the portal is slow at both ends.

### Example 2: Swinging Door

A portal that swings open around one edge like a door and closes again. It needs two keyframes.

1. Switch to the Animate Portal mode and right-click the portal.
2. Left-click a point on the left edge of the portal to lock it, like in Drag Portal Mode. This is the hinge.
3. Lock the width and the height of the portal, so that it swings instead of stretching.
4. Drag the right edge of the portal until it has turned 45 degrees. Press shift + right-click to add keyframe 1. Keep its time of 1 second.
5. Drag the portal until it has turned 90 degrees. Press shift + right-click to add keyframe 2.
6. Press `=` once to make keyframe 2 take 2 seconds. Press `G` until the screen shows the easing `sine`.
7. Check that looping is on and press `ESC`.

The door opens in 3 seconds. The return phase uses the time and the easing of keyframe 2, so it closes in 2 seconds.

The commands for a similar animation are in [Portal Animation](wiki:portal-animation#examples).
