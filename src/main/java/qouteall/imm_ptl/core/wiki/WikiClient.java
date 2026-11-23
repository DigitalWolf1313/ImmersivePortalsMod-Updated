package qouteall.imm_ptl.core.wiki;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.List;

/**
 * Registers the wiki key binding and the /immptl_wiki client command.
 */
@Environment(EnvType.CLIENT)
public class WikiClient {
    private static KeyMapping openKey;
    private static List<String> slugs;
    
    public static void init() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.imm_ptl.open_wiki", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.imm_ptl"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new WikiScreen(null, null, null));
                }
            }
        });
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("immptl_wiki")
                .executes(context -> {
                    open(null);
                    return 1;
                })
                .then(ClientCommandManager.argument("page", StringArgumentType.greedyString())
                    .suggests((context, builder) ->
                        SharedSuggestionProvider.suggest(getSlugs(), builder)
                    )
                    .executes(context -> {
                        open(StringArgumentType.getString(context, "page"));
                        return 1;
                    })
                )
        ));
    }
    
    /**
     * Returns the page slugs for command suggestions.
     * The index is read on the first call and the result is kept for the session.
     */
    private static List<String> getSlugs() {
        if (slugs == null) {
            slugs = new WikiLibrary().getSlugs();
        }
        return slugs;
    }
    
    /**
     * Opens the wiki on a later tick because the chat screen closes itself right after running a command.
     * The target is a page slug with an optional anchor, such as {@code portals#some-heading}.
     */
    public static void open(@Nullable String target) {
        IPGlobal.CLIENT_TASK_LIST.addTask(MyTaskList.oneShotTask(() -> {
            Minecraft client = Minecraft.getInstance();
            String slug = target;
            String anchor = null;
            if (target != null) {
                int hash = target.indexOf('#');
                if (hash >= 0) {
                    slug = target.substring(0, hash);
                    anchor = target.substring(hash + 1);
                }
            }
            client.setScreen(new WikiScreen(client.screen, slug, anchor));
        }));
    }
}
