package seam.graphics.draw;

import seam.core.*;
import seam.graphics.view.*;
import seam.runtime.*;
import seam.runtime.control.*;
import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;

public final class SeamDrawScope{
    private final SeamRuntimeStack stack;

    private final Mat previousProjection = new Mat();
    private final Mat previousTransform = new Mat();
    private final Mat seamTransform = new Mat();
    private final Mat combinedTransform = new Mat();

    private final Mat rangePreviousTransform = new Mat();
    private final Mat rangeSeamTransform = new Mat();
    private final Mat rangeCombinedTransform = new Mat();

    private SpriteBatch isolatedBatch;
    private Batch previousBatch;

    private float previousZ;
    private float rangePreviousZ;

    private boolean active;
    private boolean isolatedActive;
    private boolean zTransformActive;
    private boolean rangeActive;

    public SeamDrawScope(SeamRuntimeStack stack){
        if(stack == null){
            throw new NullPointerException("stack");
        }

        this.stack = stack;
    }

    public boolean active(){
        return active || rangeActive;
    }

    public boolean isolatedActive(){
        return isolatedActive;
    }

    public void dispose(){
        if(active || rangeActive){
            throw new IllegalStateException("Cannot dispose SeamDrawScope while it is active.");
        }

        if(isolatedBatch != null){
            isolatedBatch.dispose();
            isolatedBatch = null;
        }
    }

    public void beginIsolated(SeamRuntime runtime, SeamView view){
        beginIsolated(runtime, view, SeamPhase.renderWorld);
    }

    public void beginIsolated(SeamRuntime runtime, SeamView view, SeamPhase phase){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(view == null){
            throw new NullPointerException("view");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        if(active){
            throw new IllegalStateException("SeamDrawScope is already active.");
        }

        if(rangeActive){
            throw new IllegalStateException("Cannot begin isolated Seam draw while Seam render range is active.");
        }

        if(stack.active()){
            throw new IllegalStateException("Cannot begin isolated Seam draw while another Seam runtime context is active.");
        }

        if(view.runtimeId() != runtime.id){
            throw new IllegalArgumentException("View runtime id does not match runtime.");
        }

        if(!view.renderable()){
            throw new IllegalStateException("View is not renderable.");
        }

        runtime.requireWorldReady();

        previousBatch = Core.batch;
        previousProjection.set(Draw.proj());
        previousTransform.set(Draw.trans());
        previousZ = Draw.z();

        view.projection().writeTransform(seamTransform);
        combinedTransform.set(previousTransform).mul(seamTransform);

        stack.enter(runtime, phase);

        try{
            SeamRuntimeValidator.validateActiveContext(runtime);

            Draw.flush();

            Core.batch = isolatedBatch();

            Draw.proj(previousProjection);
            Draw.trans(combinedTransform);
            Draw.sort(true);
            Draw.z(0f);
            Draw.reset();

            active = true;
            isolatedActive = true;
            zTransformActive = false;
        }catch(Throwable throwable){
            try{
                Core.batch = previousBatch;
                previousBatch = null;

                Draw.proj(previousProjection);
                Draw.trans(previousTransform);
                Draw.z(previousZ);
                Draw.reset();
            }finally{
                active = false;
                isolatedActive = false;
                zTransformActive = false;
                stack.exit();
            }

            throw throwable;
        }
    }

    public void endIsolated(){
        if(!active || !isolatedActive){
            throw new IllegalStateException("SeamDrawScope isolated draw is not active.");
        }

        Throwable failure = null;

        try{
            Draw.flush();
            Draw.sort(false);
            Draw.shader();
            Draw.blend();
            Draw.trans(previousTransform);
            Draw.proj(previousProjection);
            Draw.z(previousZ);
            Draw.reset();
        }catch(Throwable throwable){
            failure = throwable;
        }

        try{
            Core.batch = previousBatch;
            previousBatch = null;

            Draw.trans(previousTransform);
            Draw.proj(previousProjection);
            Draw.z(previousZ);
            Draw.reset();
        }catch(Throwable throwable){
            if(failure == null){
                failure = throwable;
            }
        }

        try{
            stack.exit();
        }catch(Throwable throwable){
            if(failure == null){
                failure = throwable;
            }
        }finally{
            active = false;
            isolatedActive = false;
            zTransformActive = false;
        }

        rethrow(failure);
    }

    private SpriteBatch isolatedBatch(){
        if(isolatedBatch == null){
            isolatedBatch = new SpriteBatch(4096);
        }

        return isolatedBatch;
    }

    private void rethrow(Throwable throwable){
        if(throwable == null){
            return;
        }

        if(throwable instanceof RuntimeException runtimeException){
            throw runtimeException;
        }

        throw new RuntimeException(throwable);
    }
}