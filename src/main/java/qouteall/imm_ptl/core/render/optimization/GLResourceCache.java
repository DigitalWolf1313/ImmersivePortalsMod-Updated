package qouteall.imm_ptl.core.render.optimization;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.util.function.Consumer;

public class GLResourceCache {
    public static Minecraft client = Minecraft.getInstance();
    
    private final Consumer<int[]> generator;
    private final IntList bufferIds = new IntArrayList();
    
    public static GLResourceCache bufferCache = new GLResourceCache(GLResourceCache::generateBuffers);
    public static GLResourceCache vertexArrayCache = new GLResourceCache(GLResourceCache::generateVertexArrays);
    
    public GLResourceCache(Consumer<int[]> generator) {
        this.generator = generator;
    }
    
    public int getNewResourceId() {
        if (bufferIds.isEmpty()) {
            reserve(1000);
        }
        
        int taken = bufferIds.removeInt(bufferIds.size() - 1);
        return taken;
    }
    
    public static void init() {
    
    }
    
    private void reserve(int num) {
        int[] buf = new int[num];
        generator.accept(buf);
        bufferIds.addElements(bufferIds.size(), buf);
    }
    
    // A generated name only becomes an object when it is first bound.
    // Labeling it or using direct state access on it before that raises GL errors on strict drivers.
    // So every generated name is bound once, and the previous binding is restored.
    private static void generateBuffers(int[] ids) {
        GL15.glGenBuffers(ids);
        
        int previousBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        for (int id : ids) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBinding);
    }
    
    private static void generateVertexArrays(int[] ids) {
        GL30.glGenVertexArrays(ids);
        
        int previousBinding = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        for (int id : ids) {
            GL30.glBindVertexArray(id);
        }
        GL30.glBindVertexArray(previousBinding);
    }
    
    private int getExpectedBufferSize() {
        int viewDistance = client.options.getEffectiveRenderDistance();
        int diameter = viewDistance * 2 + 1;
        
        //every column has 16 sections, every section has 5 layers
        return diameter * diameter * 16 * 5 * 4;
    }
}
