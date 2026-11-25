package qouteall.imm_ptl.core.compat;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43C;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.q_misc_util.Helper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Keeps Voxy's shader pipeline matched with the Iris pipeline of the dimension being rendered.
 * Iris owns one pipeline per dimension and every level renderer owns its own Voxy render system.
 * A render system can therefore be built against the Iris pipeline of another dimension.
 * Voxy is only reached through reflection, so any stock Voxy build works without a compile time dependency.
 */
public final class IPVoxyPipelineBridge {
    private static final String CORE = "me.cortex.voxy.client.core.";
    private static final int STORAGE_BINDING_COUNT = 10;

    private static volatile BindingHandles bindingHandles;
    private static volatile SwapHandles swapHandles;
    private static boolean bindingBroken;
    private static boolean swapBroken;

    // Records the Iris-side data that a render system last tried to rebuild for.
    // A failed rebuild is therefore not retried every frame.
    private static final Map<Object, WeakReference<Object>> LAST_ATTEMPT = new WeakHashMap<>();

    private static final Map<Class<?>, Optional<Method>> PIPELINE_DATA_GETTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> PATCH_DATA_GETTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> RENDER_SYSTEM_GETTERS = new ConcurrentHashMap<>();

    private IPVoxyPipelineBridge() {
    }

    /**
     * Voxy refuses to build a second shader pipeline for the same Iris data.
     * Several render systems can share one Iris pipeline, so the binding is released before every build.
     */
    public static void clearCurrentDataBinding() {
        if (bindingBroken) {
            return;
        }
        try {
            Object irisPipeline = IrisInterface.invoker.getCurrentPipeline();
            if (irisPipeline == null) {
                return;
            }
            Object data = getPipelineData(irisPipeline);
            if (data != null) {
                bindingHandles().dataBoundPipeline.set(data, null);
            }
        }
        catch (Throwable t) {
            bindingBroken = true;
            Helper.LOGGER.error("Voxy shader pipeline binding hook failed", unwrap(t));
        }
    }

    /**
     * Voxy checks that the pipeline being freed is the one bound to its data.
     * The binding is restored right before a free so that the check passes for any pipeline.
     */
    public static void bindPipelineToData(Object pipeline) {
        if (bindingBroken) {
            return;
        }
        try {
            BindingHandles h = bindingHandles();
            h.dataBoundPipeline.set(h.irisPipelineData.get(pipeline), pipeline);
        }
        catch (Throwable t) {
            bindingBroken = true;
            Helper.LOGGER.error("Voxy shader pipeline free hook failed", unwrap(t));
        }
    }

    public static boolean hasPatchData(Object irisPipeline) {
        try {
            Method getter = cachedGetter(PATCH_DATA_GETTERS, irisPipeline.getClass(), "voxy$getPatchData");
            return getter != null && getter.invoke(irisPipeline) != null;
        }
        catch (Throwable t) {
            Helper.LOGGER.error("Voxy patch data lookup failed", unwrap(t));
            return false;
        }
    }

    /**
     * Returns the depth texture of the Voxy pipeline owned by the level renderer that is currently rendering.
     * Returns 0 when there is no such texture yet.
     */
    public static int getVoxyDepthTexture(boolean translucent) {
        if (bindingBroken) {
            return 0;
        }
        try {
            Object levelRenderer = Minecraft.getInstance().levelRenderer;
            if (levelRenderer == null) {
                return 0;
            }
            Method getSystem = cachedGetter(RENDER_SYSTEM_GETTERS, levelRenderer.getClass(), "voxy$getRenderSystem");
            if (getSystem == null) {
                return 0;
            }
            Object system = getSystem.invoke(levelRenderer);
            if (system == null) {
                return 0;
            }

            BindingHandles h = bindingHandles();
            Object pipeline = h.systemPipeline.get(system);
            if (!h.irisPipelineClass.isInstance(pipeline)) {
                return 0;
            }

            Field framebufferField = translucent ? h.translucentFramebuffer : h.opaqueFramebuffer;
            Object texture = h.depthTexture.invoke(framebufferField.get(pipeline));
            return texture == null ? 0 : h.textureId.getInt(texture);
        }
        catch (Throwable t) {
            bindingBroken = true;
            Helper.LOGGER.error("Voxy depth texture lookup failed", unwrap(t));
            return 0;
        }
    }

    /**
     * Rebuilds the render system's pipeline when it was built for different Iris data than the current Iris pipeline has.
     * The rebuild happens at the start of a frame, before the viewport of the render system is used.
     */
    public static void syncPipeline(Object system) {
        if (swapBroken) {
            return;
        }
        try {
            Object irisPipeline = IrisInterface.invoker.getCurrentPipeline();
            if (irisPipeline == null || IrisInterface.invoker.isRenderingShadowMap()) {
                return;
            }

            BindingHandles binding = bindingHandles();
            Object targetData = getPipelineData(irisPipeline);
            Object pipeline = binding.systemPipeline.get(system);
            Object boundData = binding.irisPipelineClass.isInstance(pipeline)
                ? binding.irisPipelineData.get(pipeline) : null;
            if (targetData == boundData) {
                return;
            }

            WeakReference<Object> attempted = LAST_ATTEMPT.get(system);
            if (attempted != null && attempted.get() == targetData) {
                return;
            }
            LAST_ATTEMPT.put(system, new WeakReference<>(targetData));

            Helper.LOGGER.info("Rebuilding Voxy pipeline, shader data "
                + (targetData == null ? "none" : Integer.toHexString(System.identityHashCode(targetData))));
            swapPipeline(system, swapHandles(), binding);
        }
        catch (Throwable t) {
            Helper.LOGGER.error("Voxy pipeline rebuild failed", unwrap(t));
        }
    }

    private static void swapPipeline(
        Object system, SwapHandles h, BindingHandles binding
    ) throws ReflectiveOperationException {
        Object oldPipeline = h.systemPipeline.get(system);
        Object oldSelector = h.systemSelector.get(system);
        Object oldChunkBound = h.systemChunkBound.get(system);
        Object traversal = h.systemTraversal.get(system);
        Object modelService = h.systemModelService.get(system);

        int[] storageBindings = saveStorageBindings();
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        Object newPipeline = null;
        Object sectionRenderer = null;
        Object newSelector = null;
        Object newChunkBound = null;
        boolean sectionRendererAttached = false;
        boolean traversalTouched = false;
        try {
            BooleanSupplier frexWork = () -> (Boolean) invokeUnchecked(h.systemFrexWork, system);
            newPipeline = h.factoryCreate.invoke(
                null,
                h.systemProperties.get(system),
                h.systemNodeManager.get(system),
                h.systemNodeCleaner.get(system),
                traversal,
                frexWork
            );
            h.pipelineSetupModels.invoke(newPipeline, modelService);

            Object backendFactory = h.systemBackendFactory.invoke(null);
            Object modelStore = method(modelService.getClass(), "getStore", 0).invoke(modelService);
            sectionRenderer = method(backendFactory.getClass(), "create", 3)
                .invoke(backendFactory, newPipeline, modelStore, h.systemGeometry.get(system));
            h.pipelineSetSectionRenderer.invoke(newPipeline, sectionRenderer);
            sectionRendererAttached = true;

            Object renderer = sectionRenderer;
            Method createViewport = method(renderer.getClass(), "createViewport", 0);
            Supplier<Object> viewportCreator = () -> invokeUnchecked(createViewport, renderer);
            newSelector = h.selectorConstructor.newInstance(viewportCreator);

            newChunkBound = h.chunkBoundConstructor.newInstance(newPipeline);
            copyChunkBounds(h, oldChunkBound, newChunkBound);

            traversalTouched = true;
            recompileTraversal(h, traversal, newPipeline);
        }
        catch (Throwable t) {
            if (traversalTouched) {
                try {
                    recompileTraversal(h, traversal, oldPipeline);
                }
                catch (Throwable restoreFailure) {
                    Helper.LOGGER.error("Could not restore the Voxy traversal shader", unwrap(restoreFailure));
                }
            }
            freeQuietly(h.selectorFree, newSelector);
            freeQuietly(h.chunkBoundFree, newChunkBound);
            if (newPipeline != null && sectionRendererAttached) {
                freeQuietly(h.pipelineFree, newPipeline);
            }
            else if (sectionRenderer != null) {
                freeQuietly(method(sectionRenderer.getClass(), "free", 0), sectionRenderer);
            }
            if (t instanceof ReflectiveOperationException roe) {
                throw roe;
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        }
        finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            restoreStorageBindings(storageBindings);
        }

        h.systemPipeline.set(system, newPipeline);
        h.systemSelector.set(system, newSelector);
        h.systemChunkBound.set(system, newChunkBound);

        freeQuietly(h.selectorFree, oldSelector);
        freeQuietly(h.chunkBoundFree, oldChunkBound);
        freeQuietly(h.pipelineFree, oldPipeline);
    }

    // The traverser keeps a pipeline reference only when the pipeline has a TAA function.
    // The reference is cleared first so that it never points at a freed pipeline.
    private static void recompileTraversal(
        SwapHandles h, Object traversal, Object pipeline
    ) throws ReflectiveOperationException {
        Object oldShader = h.traversalShader.get(traversal);
        if (oldShader != null) {
            method(oldShader.getClass(), "free", 0).invoke(oldShader);
            h.traversalShader.set(traversal, null);
        }
        h.traversalPipeline.set(traversal, null);
        h.traversalCompile.invoke(traversal, pipeline);
    }

    // The bounding sections are carried over because Sodium only reports sections when they change.
    private static void copyChunkBounds(
        SwapHandles h, Object from, Object to
    ) throws ReflectiveOperationException {
        LongOpenHashSet positions = new LongOpenHashSet(((Long2IntOpenHashMap) h.chunkPositions.get(from)).keySet());
        positions.addAll((LongOpenHashSet) h.chunkAdds.get(from));
        positions.removeAll((LongOpenHashSet) h.chunkRemoves.get(from));
        ((LongOpenHashSet) h.chunkAdds.get(to)).addAll(positions);
    }

    private static int[] saveStorageBindings() {
        int[] saved = new int[STORAGE_BINDING_COUNT];
        for (int i = 0; i < saved.length; i++) {
            saved[i] = GL30.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, i);
        }
        return saved;
    }

    private static void restoreStorageBindings(int[] saved) {
        for (int i = 0; i < saved.length; i++) {
            GL30.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, i, saved[i]);
        }
    }

    private static void freeQuietly(Method freeMethod, Object target) {
        if (target == null) {
            return;
        }
        try {
            freeMethod.invoke(target);
        }
        catch (Throwable t) {
            Helper.LOGGER.error("Failed to free a Voxy object", unwrap(t));
        }
    }

    private static Object getPipelineData(Object irisPipeline) throws ReflectiveOperationException {
        Method getter = cachedGetter(PIPELINE_DATA_GETTERS, irisPipeline.getClass(), "voxy$getPipelineData");
        return getter == null ? null : getter.invoke(irisPipeline);
    }

    private static Method cachedGetter(Map<Class<?>, Optional<Method>> cache, Class<?> type, String name) {
        return cache.computeIfAbsent(type, t -> {
            try {
                Method m = t.getMethod(name);
                m.setAccessible(true);
                return Optional.of(m);
            }
            catch (NoSuchMethodException | RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static BindingHandles bindingHandles() throws ReflectiveOperationException {
        BindingHandles h = bindingHandles;
        if (h == null) {
            h = new BindingHandles();
            bindingHandles = h;
        }
        return h;
    }

    private static SwapHandles swapHandles() throws ReflectiveOperationException {
        SwapHandles h = swapHandles;
        if (h == null) {
            try {
                h = new SwapHandles();
            }
            catch (ReflectiveOperationException | RuntimeException e) {
                swapBroken = true;
                throw e;
            }
            swapHandles = h;
        }
        return h;
    }

    private static Object invokeUnchecked(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Throwable unwrap(Throwable t) {
        while (t.getCause() != null && (t instanceof InvocationTargetException || t.getClass() == RuntimeException.class)) {
            t = t.getCause();
        }
        return t;
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, IPVoxyPipelineBridge.class.getClassLoader());
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            }
            catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == parameterCount
                    && !m.isBridge() && !m.isSynthetic()) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + parameterCount);
    }

    private static final class BindingHandles {
        final Class<?> irisPipelineClass;
        final Field irisPipelineData;
        final Field dataBoundPipeline;
        final Field systemPipeline;
        final Field opaqueFramebuffer;
        final Field translucentFramebuffer;
        final Method depthTexture;
        final Field textureId;

        BindingHandles() throws ReflectiveOperationException {
            irisPipelineClass = load(CORE + "IrisVoxyRenderPipeline");
            Class<?> base = load(CORE + "AbstractRenderPipeline");
            Class<?> data = load("me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData");
            Class<?> system = load(CORE + "VoxyRenderSystem");
            Class<?> depthFramebuffer = load(CORE + "rendering.util.DepthFramebuffer");
            Class<?> texture = load(CORE + "gl.GlTexture");

            irisPipelineData = field(irisPipelineClass, "data");
            dataBoundPipeline = field(data, "thePipeline");
            systemPipeline = field(system, "pipeline");
            opaqueFramebuffer = field(base, "fb");
            translucentFramebuffer = field(irisPipelineClass, "fbTranslucent");
            depthTexture = method(depthFramebuffer, "getDepthTex", 0);
            textureId = field(texture, "id");
        }
    }

    private static final class SwapHandles {
        final Field systemPipeline;
        final Field systemSelector;
        final Field systemChunkBound;
        final Field systemProperties;
        final Field systemModelService;
        final Field systemGeometry;
        final Field systemNodeManager;
        final Field systemNodeCleaner;
        final Field systemTraversal;
        final Method systemFrexWork;
        final Method systemBackendFactory;
        final Method factoryCreate;
        final Method pipelineSetupModels;
        final Method pipelineSetSectionRenderer;
        final Method pipelineFree;
        final Method selectorFree;
        final Method chunkBoundFree;
        final Constructor<?> selectorConstructor;
        final Constructor<?> chunkBoundConstructor;
        final Method traversalCompile;
        final Field traversalShader;
        final Field traversalPipeline;
        final Field chunkPositions;
        final Field chunkAdds;
        final Field chunkRemoves;

        SwapHandles() throws ReflectiveOperationException {
            Class<?> system = load(CORE + "VoxyRenderSystem");
            Class<?> base = load(CORE + "AbstractRenderPipeline");
            Class<?> factory = load(CORE + "RenderPipelineFactory");
            Class<?> selector = load(CORE + "rendering.ViewportSelector");
            Class<?> chunkBound = load(CORE + "rendering.ChunkBoundRenderer");
            Class<?> traversal = load(CORE + "rendering.hierachical.HierarchicalOcclusionTraverser");

            systemPipeline = field(system, "pipeline");
            systemSelector = field(system, "viewportSelector");
            systemChunkBound = field(system, "chunkBoundRenderer");
            systemProperties = field(system, "properties");
            systemModelService = field(system, "modelService");
            systemGeometry = field(system, "geometryData");
            systemNodeManager = field(system, "nodeManager");
            systemNodeCleaner = field(system, "nodeCleaner");
            systemTraversal = field(system, "traversal");
            systemFrexWork = method(system, "frexStillHasWork", 0);
            systemBackendFactory = method(system, "getRenderBackendFactory", 0);

            factoryCreate = method(factory, "createPipeline", 5);
            pipelineSetupModels = method(base, "setupExtraModelBakeryData", 1);
            pipelineSetSectionRenderer = method(base, "setSectionRenderer", 1);
            pipelineFree = method(base, "free", 0);
            selectorFree = method(selector, "free", 0);
            chunkBoundFree = method(chunkBound, "free", 0);

            selectorConstructor = selector.getDeclaredConstructor(Supplier.class);
            chunkBoundConstructor = chunkBound.getDeclaredConstructor(base);

            traversalCompile = method(traversal, "lateStageCompile", 1);
            traversalShader = field(traversal, "traversal");
            traversalPipeline = field(traversal, "pipeline");

            chunkPositions = field(chunkBound, "chunk2idx");
            chunkAdds = field(chunkBound, "addQueue");
            chunkRemoves = field(chunkBound, "remQueue");
        }
    }
}
