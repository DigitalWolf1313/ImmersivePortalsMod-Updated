package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.MatrixUniforms;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisTemporalContext;

import java.util.function.Supplier;

@Mixin(value = MatrixUniforms.class, remap = false)
public class MixinIrisMatrixUniforms {
    @WrapOperation(method = "addMatrix", at = @At(value = "INVOKE",
        target = "Lnet/irisshaders/iris/gl/uniform/UniformHolder;uniformMatrix(Lnet/irisshaders/iris/gl/uniform/UniformUpdateFrequency;Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/irisshaders/iris/gl/uniform/UniformHolder;"))
    private static UniformHolder ip_previousMatrix(UniformHolder holder, UniformUpdateFrequency frequency,
                                                   String name, Supplier<Matrix4fc> supplier,
                                                   Operation<UniformHolder> original) {
        if (name.equals("gbufferPreviousModelView") || name.equals("gbufferPreviousProjection")) {
            Supplier<Matrix4fc> fallback = supplier;
            supplier = () -> {
                var view = IrisTemporalContext.current();
                if (view == null) return fallback.get();
                return name.equals("gbufferPreviousModelView") ? view.previousModelView : view.previousProjection;
            };
        }
        return original.call(holder, frequency, name, supplier);
    }
}
