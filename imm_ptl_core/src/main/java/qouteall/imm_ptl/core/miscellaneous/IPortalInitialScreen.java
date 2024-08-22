package qouteall.imm_ptl.core.miscellaneous;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

public class IPortalInitialScreen extends Screen {
    private static final int PAGE_NUM = 4;
    
    private final Runnable onClose;
    
    private Button prevButton;
    private Button iKnowButton;
    
    private int currentPageIndex = 0;
    
    private MultiLineLabel contentLabel = MultiLineLabel.EMPTY;
    
    public IPortalInitialScreen(Runnable onClose) {
        super(Component.empty());
        this.onClose = onClose;
    }
    
    private void onPrevious() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            updateUiStatus(currentPageIndex);
        }
    }
    
    private void onIKnow() {
        if (currentPageIndex == PAGE_NUM - 1) {
            IPConfig config = IPConfig.getConfig();
            config.initialScreenShown = true;
            config.saveConfigFile();
            
            onClose();
        }
        else {
            currentPageIndex++;
            updateUiStatus(currentPageIndex);
        }
    }
    
    private void updateUiStatus(int newPageIndex) {
        this.currentPageIndex = newPageIndex;
        
        if (prevButton != null) {
            prevButton.visible = newPageIndex > 0;
        }
        
        contentLabel = MultiLineLabel.create(
            font,
            Component.translatable("iportal.initial_screen.content." + newPageIndex),
            this.width - 40
        );
    }
    
    @Override
    public void onClose() {
        onClose.run();
    }
    
    @Override
    public void init() {
        this.font = this.minecraft.font;
        
        int centerX = this.width / 2;
        
        prevButton = Button.builder(
            Component.translatable("iportal.initial_screen.prev"),
            button -> onPrevious()
        ).pos(centerX - 100, this.height - 40).size(60, 20).build();
        
        iKnowButton = Button.builder(
            Component.translatable("iportal.initial_screen.i_know"),
            button -> onIKnow()
        ).pos(centerX + 40, this.height - 40).size(60, 20).build();
        
        addRenderableWidget(prevButton);
        addRenderableWidget(iKnowButton);
        
        updateUiStatus(currentPageIndex);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        int centerX = this.width / 2;
        
        // Draw icon
        ResourceLocation iconTexture = new ResourceLocation("immersive_portals", "icon.png");
        guiGraphics.blit(iconTexture, centerX - 55, 10, 0, 0, 30, 30, 30, 30);
        
        // Draw title
        Component title = Component.translatable("iportal.initial_screen.title");
        guiGraphics.drawString(font, title, centerX - 15, 18, 0xFFFFFF, false);
        
        // Draw content
        contentLabel.renderCentered(guiGraphics, centerX, 60, 15, 0xFFFFFF);
        
        // Draw page number
        Component pageText = Component.literal(String.format("%d / %d", currentPageIndex + 1, PAGE_NUM));
        int pageTextWidth = font.width(pageText);
        guiGraphics.drawString(font, pageText, centerX - pageTextWidth / 2, this.height - 60, 0xFFFFFF, false);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}