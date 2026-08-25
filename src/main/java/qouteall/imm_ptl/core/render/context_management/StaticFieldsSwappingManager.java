package qouteall.imm_ptl.core.render.context_management;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Supplier;

//sometimes minecraft stores some dimension-specific things into static fields
//for example BackgroundRenderer
//we have to render multiple dimensions at the same time
//so we have to store multiple sets of these static fields
public class StaticFieldsSwappingManager<Context> {
    private final Consumer<Context> copyFromObject;
    private final Consumer<Context> copyToObject;
    private final boolean strictCheck;
    @Nullable
    private final Supplier<Context> contextConstructor;
    
    public static class ContextRecord<Ctx> {
        public ResourceKey<Level> dimension;
        public Ctx context;
        
        //sometimes the static fields contain the latest context
        //and our context object contains out-dated info
        public boolean isHoldingLatestContext = false;
        
        public ContextRecord(ResourceKey<Level> dimension, Ctx context, boolean isHoldingLatestContext) {
            this.dimension = dimension;
            this.context = context;
            this.isHoldingLatestContext = isHoldingLatestContext;
        }
    }
    
    private ResourceKey<Level> outerDimension;
    private Stack<ContextRecord<Context>> swappedContext = new Stack<>();
    
    //this will be managed by other classes
    public final Map<ResourceKey<Level>, ContextRecord<Context>> contextMap = new HashMap<>();
    
    public StaticFieldsSwappingManager(
        Consumer<Context> copyFromObject,
        Consumer<Context> copyToObject,
        boolean doStrictCheck,
        @Nullable Supplier<Context> contextConstructor
    ) {
        
        this.copyFromObject = copyFromObject;
        this.copyToObject = copyToObject;
        this.strictCheck = doStrictCheck;
        this.contextConstructor = contextConstructor;
    }
    
    public boolean isSwapped() {
        return !swappedContext.empty();
    }
    
    public void setOuterDimension(ResourceKey<Level> dim) {
        Validate.isTrue(!isSwapped());
        
        outerDimension = dim;
    }
    
    public void resetChecks() {
        Validate.isTrue(!isSwapped());
        
        contextMap.values().forEach(record -> {
            record.isHoldingLatestContext = record.dimension != outerDimension;
        });
    }
    
    public ResourceKey<Level> getCurrentDimension() {
        if (swappedContext.empty()) {
            Objects.requireNonNull(outerDimension);
            return outerDimension;
        }
        else {
            return swappedContext.peek().dimension;
        }
    }
    
    public void pushSwapping(ResourceKey<Level> newDimension) {
        ContextRecord<Context> oldContext = getCurrentContextRecord();
        Objects.requireNonNull(oldContext);
        
        // Same-dimension recursion needs a separate context to avoid aliasing
        // the ancestor's context during portal rendering.
        boolean isDimensionAlreadyActive = isDimensionActiveInChain(newDimension);
        
        ContextRecord<Context> newContext;
        if (isDimensionAlreadyActive) {
            newContext = new ContextRecord<>(newDimension, contextConstructor.get(), true);
        }
        else {
            newContext = contextMap.computeIfAbsent(newDimension, k -> {
                return new ContextRecord<>(newDimension, contextConstructor.get(), true);
            });
        }
        Objects.requireNonNull(newContext);
        
        swappedContext.push(newContext);
        
        transferDataFromStaticFieldsToObject(oldContext);
        
        transferDataFromObjectToStaticFields(newContext);
    }
    
    public void popSwapping() {
        ContextRecord<Context> outerContext = swappedContext.pop();
        ContextRecord<Context> innerContext = getCurrentContextRecord();
        
        transferDataFromStaticFieldsToObject(outerContext);
        
        transferDataFromObjectToStaticFields(innerContext);
    }
    
    // Returns the actual active record, including temporary same-dimension ones.
    private ContextRecord<Context> getCurrentContextRecord() {
        if (swappedContext.empty()) {
            Objects.requireNonNull(outerDimension);
            return contextMap.get(outerDimension);
        }
        else {
            return swappedContext.peek();
        }
    }
    
    private boolean isDimensionActiveInChain(ResourceKey<Level> dimension) {
        if (dimension == outerDimension) {
            return true;
        }
        for (ContextRecord<Context> record : swappedContext) {
            if (record.dimension == dimension) {
                return true;
            }
        }
        return false;
    }
    
    public void swapAndInvoke(ResourceKey<Level> newDimension, Runnable func) {
        pushSwapping(newDimension);
        func.run();
        popSwapping();
    }
    
    private void transferDataFromObjectToStaticFields(ContextRecord<Context> newContext) {
        if (!strictCheck) {
            if (newContext == null) {
                return;
            }
        }
        
        if (strictCheck) {
            Validate.isTrue(newContext.isHoldingLatestContext);
        }
        newContext.isHoldingLatestContext = false;
        copyFromObject.accept(newContext.context);
    }
    
    private void transferDataFromStaticFieldsToObject(ContextRecord<Context> oldContext) {
        if (!strictCheck) {
            if (oldContext == null) {
                return;
            }
        }
        
        if (strictCheck) {
            Validate.isTrue(!oldContext.isHoldingLatestContext);
        }
        oldContext.isHoldingLatestContext = true;
        copyToObject.accept(oldContext.context);
    }
    
    //called when player teleports
    public void updateOuterDimensionAndChangeContext(ResourceKey<Level> newDimension) {
        Validate.isTrue(!isSwapped());
        Objects.requireNonNull(outerDimension);
        
        ResourceKey<Level> oldDimension = this.outerDimension;
        
        transferDataFromStaticFieldsToObject(contextMap.get(oldDimension));
        
        transferDataFromObjectToStaticFields(contextMap.get(newDimension));
        
        outerDimension = newDimension;
    }
    
    
}
