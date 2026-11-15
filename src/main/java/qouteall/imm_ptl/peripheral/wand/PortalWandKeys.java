package qouteall.imm_ptl.peripheral.wand;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The configurable key bindings of the animation recording mode of the portal wand.
 * The alternative bindings keep both the main row and the keypad working by default.
 */
@Environment(EnvType.CLIENT)
public class PortalWandKeys {
    private static final String CATEGORY = "key.categories.imm_ptl";
    
    public static final KeyMapping TOGGLE_PREVIEW = create("preview", GLFW.GLFW_KEY_Y);
    public static final KeyMapping DURATION_UP = create("duration_up", GLFW.GLFW_KEY_EQUAL);
    public static final KeyMapping DURATION_UP_ALT = create("duration_up_alt", GLFW.GLFW_KEY_KP_ADD);
    public static final KeyMapping DURATION_DOWN = create("duration_down", GLFW.GLFW_KEY_MINUS);
    public static final KeyMapping DURATION_DOWN_ALT = create("duration_down_alt", GLFW.GLFW_KEY_KP_SUBTRACT);
    public static final KeyMapping CYCLE_EASING = create("cycle_easing", GLFW.GLFW_KEY_G);
    public static final KeyMapping TOGGLE_LOOP = create("toggle_loop", GLFW.GLFW_KEY_N);
    public static final KeyMapping UPDATE_KEYFRAME = create("update_keyframe", GLFW.GLFW_KEY_U);
    public static final KeyMapping PREVIOUS = create("previous", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping NEXT = create("next", GLFW.GLFW_KEY_UP);
    public static final KeyMapping DELETE = create("delete", GLFW.GLFW_KEY_DELETE);
    
    private static KeyMapping create(String name, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.imm_ptl.animate_wand." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY
        ));
    }
    
    /**
     * Loads the class so that the bindings are registered before the options file is read.
     * Reading the fields lazily from the tick would register them too late to be saved.
     */
    public static void init() {
    }
}
