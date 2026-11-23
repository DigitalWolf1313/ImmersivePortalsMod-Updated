package qouteall.imm_ptl.core.platform_specific;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import qouteall.imm_ptl.core.wiki.WikiScreen;

@Environment(EnvType.CLIENT)
public class IPConfigGUI {
    @SuppressWarnings({"deprecation", "unchecked"})
    public static Screen createClothConfigScreen(Screen parent) {
        ConfigScreenProvider<IPConfig> provider =
            (ConfigScreenProvider<IPConfig>) AutoConfig.getConfigScreen(IPConfig.class, parent);
        
        provider.setBuildFunction(builder -> {
            builder.setAfterInitConsumer(IPConfigGUI::addWikiButton);
            return builder.build();
        });
        
        return provider.get();
    }
    
    /**
     * Adds the wiki button at the right end of the darkened tab bar.
     * Cloth runs this after every init, so the button is rebuilt on resize.
     * Cloth draws its tab bar by hand, so the button is drawn from Fabric's after-render event instead.
     * It is inserted first in the children list so it takes clicks before any tab button scrolled beneath it.
     */
    private static void addWikiButton(Screen screen) {
        if (!(screen instanceof AbstractConfigScreen configScreen)) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        Component label = Component.translatable("imm_ptl.config.open_wiki");
        int buttonWidth = client.font.width(label) + 16;
        
        // The tab bar is 24 pixels tall and has a 20 pixel scroll arrow at each end.
        int x = screen.width - 24 - buttonWidth;
        int y = 43;
        if (!configScreen.isShowingTabs()) {
            // Without tabs there is no bar, so the button goes in the top right corner.
            x = screen.width - 6 - buttonWidth;
            y = 6;
        }
        
        Button button = Button.builder(label, b -> client.setScreen(new WikiScreen(screen, null, null)))
            .bounds(x, y, buttonWidth, 20)
            .build();
        
        configScreen.childrenL().add(0, button);
        ScreenEvents.afterRender(screen).register(
            (s, graphics, mouseX, mouseY, delta) -> button.render(graphics, mouseX, mouseY, delta)
        );
    }
}
