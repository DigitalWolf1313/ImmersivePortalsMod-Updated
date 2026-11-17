package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.render.optimization.LazyVertexBufferMap;

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Stream;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class MixinRenderSection implements IERenderSection {
    
    private long portal_mark;
    
    @Shadow
    protected abstract void reset();
    
    @Shadow
    @Final
    @Mutable
    public int index;
    
    @Shadow
    @Final
    private Map<RenderType, VertexBuffer> buffers;
    
    // Skip the eager "one VertexBuffer per layer" collect in the constructor.
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"
        )
    )
    private Object portal_lazyBuffers(Stream stream, Collector collector) {
        return new LazyVertexBufferMap();
    }
    
    // Compile/resort tasks fetch buffers from worker threads, so create them here on the render thread.
    @Inject(method = "createCompileTask", at = @At("HEAD"))
    private void portal_ensureBuffersForCompile(CallbackInfoReturnable<?> cir) {
        portal_ensureBuffers();
    }
    
    @Inject(method = "resortTransparency", at = @At("HEAD"))
    private void portal_ensureBuffersForResort(CallbackInfo ci) {
        portal_ensureBuffers();
    }
    
    private void portal_ensureBuffers() {
        if (buffers instanceof LazyVertexBufferMap lazyMap) {
            lazyMap.ensureAll();
        }
    }
    
    @Override
    public void portal_fullyReset() {
        reset();
    }
    
    @Override
    public long portal_getMark() {
        return portal_mark;
    }
    
    @Override
    public void portal_setMark(long arg) {
        portal_mark = arg;
    }
    
    @Override
    public void portal_setIndex(int arg) {
        index = arg;
    }
    
}
