package qouteall.imm_ptl.core.compat.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Null-guard mixin for Sable's {@link VanillaChunkedSubLevelRenderData}.
 * <p>
 * When Sable re-creates render data via {@code ClientSubLevel.updateRenderData()},
 * the old render data is {@code close()}d — which calls
 * {@link SectionRenderDispatcher.RenderSection#releaseBuffers()} on every section —
 * and a new instance is created.  If the render thread iterates the old render
 * data's sections between the {@code isEmpty(layer)} check and the
 * {@code getBuffer(layer)} / {@code bind()} / {@code draw()} sequence, the
 * returned {@link VertexBuffer} may be null or may have had its internal state
 * (vertex format, VAO name) cleared by {@code releaseBuffers()}, causing a
 * {@link NullPointerException} inside {@code bind()} or {@code draw()}.
 * <p>
 * This mixin wraps the three calls so that a null buffer (or a buffer whose
 * internals have been released) is silently skipped instead of crashing the
 * render thread.
 */
@Pseudo
@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class MixinSableVanillaChunkedSubLevelRenderData {

    /**
     * Sentinel buffer substituted when {@code getBuffer(layer)} returns null.
     * Lazily created on the render thread (GL context is active).  Never
     * actually bound or drawn — the {@code bind()} / {@code draw()} wraps
     * check for identity and skip.  A single leaked VAO per JVM lifetime is
     * acceptable.
     */
    @Unique
    private static VertexBuffer iPortals$sentinel;

    @Unique
    private static VertexBuffer iPortals$getSentinel() {
        VertexBuffer s = iPortals$sentinel;
        if (s == null) {
            iPortals$sentinel = s = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        return s;
    }

    /**
     * If {@code getBuffer(layer)} returns null, substitute the sentinel so the
     * local variable is never null and the JVM can dispatch {@code bind()} /
     * {@code draw()} on it (those calls are independently wrapped to become
     * no-ops for the sentinel).
     */
    @WrapOperation(
        method = "renderChunkedSubLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexBuffer;"
        )
    )
    private VertexBuffer iPortals$guardGetBuffer(
        SectionRenderDispatcher.RenderSection section,
        RenderType layer,
        Operation<VertexBuffer> original
    ) {
        VertexBuffer buffer = original.call(section, layer);
        return buffer != null ? buffer : iPortals$getSentinel();
    }

    /**
     * Wrap {@code buffer.bind()} — skip if the buffer is the sentinel or if
     * its internal state has been released (NPE guard).
     */
    @WrapOperation(
        method = "renderChunkedSubLevel",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;bind()V"
        )
    )
    private void iPortals$guardBind(VertexBuffer buffer, Operation<Void> original) {
        if (buffer == iPortals$sentinel) {
            return;
        }
        try {
            original.call(buffer);
        } catch (NullPointerException ignored) {
            // Buffer was released between getBuffer() and bind()
        }
    }

    /**
     * Wrap {@code buffer.draw()} — skip if the buffer is the sentinel or if
     * its internal state has been released (NPE guard).
     */
    @WrapOperation(
        method = "renderChunkedSubLevel",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"
        )
    )
    private void iPortals$guardDraw(VertexBuffer buffer, Operation<Void> original) {
        if (buffer == iPortals$sentinel) {
            return;
        }
        try {
            original.call(buffer);
        } catch (NullPointerException ignored) {
            // Buffer was released between getBuffer() and draw()
        }
    }
}