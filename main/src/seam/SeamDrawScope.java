package seam;

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

    private float previousZ;
    private float rangePreviousZ;

    private boolean active;
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

    public void beginRuntime(SeamRuntime runtime, SeamPhase phase){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        if(active){
            throw new IllegalStateException("SeamDrawScope is already active.");
        }

        if(stack.active()){
            throw new IllegalStateException("Cannot begin SeamDrawScope while another Seam runtime context is active.");
        }

        runtime.requireWorldReady();

        previousProjection.set(Draw.proj());
        previousTransform.set(Draw.trans());
        previousZ = Draw.z();

        stack.enter(runtime, phase);

        try{
            SeamRuntimeValidator.validateActiveContext(runtime);
        }catch(Throwable throwable){
            stack.exit();
            throw throwable;
        }

        active = true;
        zTransformActive = false;
    }

    public void beginQueue(SeamRuntime runtime, SeamView view){
        beginQueue(runtime, view, SeamPhase.renderWorld);
    }

    public void beginQueue(SeamRuntime runtime, SeamView view, SeamPhase phase){
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
            throw new IllegalStateException("Cannot begin Seam queue while Seam render range is active.");
        }

        if(stack.active()){
            throw new IllegalStateException("Cannot begin SeamDrawScope while another Seam runtime context is active.");
        }

        if(view.runtimeId() != runtime.id){
            throw new IllegalArgumentException("View runtime id does not match runtime.");
        }

        if(!view.renderable()){
            throw new IllegalStateException("View is not renderable.");
        }

        runtime.requireWorldReady();

        previousProjection.set(Draw.proj());
        previousTransform.set(Draw.trans());
        previousZ = Draw.z();

        stack.enter(runtime, phase);

        try{
            SeamRuntimeValidator.validateActiveContext(runtime);
            Draw.zTransform(view::hostZ);
            zTransformActive = true;
        }catch(Throwable throwable){
            Draw.zTransform();
            zTransformActive = false;
            stack.exit();
            throw throwable;
        }

        active = true;
    }

    public void endQueue(){
        if(!active){
            throw new IllegalStateException("SeamDrawScope is not active.");
        }

        Throwable failure = null;

        try{
            if(zTransformActive){
                Draw.zTransform();
                zTransformActive = false;
            }

            Draw.z(previousZ);
            Draw.reset();
        }catch(Throwable throwable){
            failure = throwable;
        }

        try{
            stack.exit();
        }catch(Throwable throwable){
            if(failure == null){
                failure = throwable;
            }
        }finally{
            active = false;
        }

        if(failure != null){
            if(failure instanceof RuntimeException runtimeException){
                throw runtimeException;
            }

            throw new RuntimeException(failure);
        }
    }

    public void beginRenderRange(SeamRuntime runtime, SeamView view){
        beginRenderRange(runtime, view, SeamPhase.renderWorld);
    }

    public void beginRenderRange(SeamRuntime runtime, SeamView view, SeamPhase phase){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(view == null){
            throw new NullPointerException("view");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        if(rangeActive){
            throw new IllegalStateException("SeamDrawScope render range is already active.");
        }

        if(active){
            throw new IllegalStateException("Cannot begin Seam render range while another SeamDrawScope operation is active.");
        }

        if(stack.active()){
            throw new IllegalStateException("Cannot begin Seam render range while another Seam runtime context is active.");
        }

        if(view.runtimeId() != runtime.id){
            throw new IllegalArgumentException("View runtime id does not match runtime.");
        }

        if(!view.renderable()){
            throw new IllegalStateException("View is not renderable.");
        }

        runtime.requireWorldReady();

        rangePreviousTransform.set(Draw.trans());
        rangePreviousZ = Draw.z();

        view.projection().writeTransform(rangeSeamTransform);
        rangeCombinedTransform.set(rangePreviousTransform).mul(rangeSeamTransform);

        stack.enter(runtime, phase);

        try{
            SeamRuntimeValidator.validateActiveContext(runtime);
            Draw.trans(rangeCombinedTransform);
        }catch(Throwable throwable){
            stack.exit();
            throw throwable;
        }

        rangeActive = true;
    }

    public void endRenderRange(){
        if(!rangeActive){
            throw new IllegalStateException("SeamDrawScope render range is not active.");
        }

        Throwable failure = null;

        try{
            Draw.trans(rangePreviousTransform);
            Draw.z(rangePreviousZ);
            Draw.reset();
        }catch(Throwable throwable){
            failure = throwable;
        }

        try{
            stack.exit();
        }catch(Throwable throwable){
            if(failure == null){
                failure = throwable;
            }
        }finally{
            rangeActive = false;
        }

        if(failure != null){
            if(failure instanceof RuntimeException runtimeException){
                throw runtimeException;
            }

            throw new RuntimeException(failure);
        }
    }

    public void begin(SeamRuntime runtime, SeamView view){
        begin(runtime, view, SeamPhase.renderWorld);
    }

    public void begin(SeamRuntime runtime, SeamView view, SeamPhase phase){
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
            throw new IllegalStateException("Cannot begin SeamDrawScope while Seam render range is active.");
        }

        if(stack.active()){
            throw new IllegalStateException("Cannot begin SeamDrawScope while another Seam runtime context is active.");
        }

        if(view.runtimeId() != runtime.id){
            throw new IllegalArgumentException("View runtime id does not match runtime.");
        }

        if(!view.renderable()){
            throw new IllegalStateException("View is not renderable.");
        }

        runtime.requireWorldReady();

        previousProjection.set(Draw.proj());
        previousTransform.set(Draw.trans());
        previousZ = Draw.z();

        view.projection().writeTransform(seamTransform);
        combinedTransform.set(previousTransform).mul(seamTransform);

        stack.enter(runtime, phase);

        try{
            SeamRuntimeValidator.validateActiveContext(runtime);

            Draw.trans(combinedTransform);
            Draw.zTransform(view::hostZ);
            zTransformActive = true;
        }catch(Throwable throwable){
            Draw.zTransform();
            zTransformActive = false;
            stack.exit();
            throw throwable;
        }

        active = true;
    }

    public void end(){
        if(!active){
            throw new IllegalStateException("SeamDrawScope is not active.");
        }

        Throwable failure = null;

        try{
            if(zTransformActive){
                Draw.zTransform();
                zTransformActive = false;
            }

            Draw.trans(previousTransform);
            Draw.proj(previousProjection);
            Draw.z(previousZ);
            Draw.reset();
        }catch(Throwable throwable){
            failure = throwable;
        }

        try{
            stack.exit();
        }catch(Throwable throwable){
            if(failure == null){
                failure = throwable;
            }
        }finally{
            active = false;
        }

        if(failure != null){
            if(failure instanceof RuntimeException runtimeException){
                throw runtimeException;
            }

            throw new RuntimeException(failure);
        }
    }

    public void runRuntime(SeamRuntime runtime, SeamPhase phase, Runnable runnable){
        if(runnable == null){
            throw new NullPointerException("runnable");
        }

        beginRuntime(runtime, phase);

        try{
            runnable.run();
        }finally{
            end();
        }
    }

    public void runQueue(SeamRuntime runtime, SeamView view, SeamPhase phase, Runnable runnable){
        if(runnable == null){
            throw new NullPointerException("runnable");
        }

        beginQueue(runtime, view, phase);

        try{
            runnable.run();
        }finally{
            endQueue();
        }
    }

    public void runRenderRange(SeamRuntime runtime, SeamView view, SeamPhase phase, Runnable runnable){
        if(runnable == null){
            throw new NullPointerException("runnable");
        }

        beginRenderRange(runtime, view, phase);

        try{
            runnable.run();
        }finally{
            endRenderRange();
        }
    }

    public void run(SeamRuntime runtime, SeamView view, Runnable runnable){
        run(runtime, view, SeamPhase.renderWorld, runnable);
    }

    public void run(SeamRuntime runtime, SeamView view, SeamPhase phase, Runnable runnable){
        if(runnable == null){
            throw new NullPointerException("runnable");
        }

        begin(runtime, view, phase);

        try{
            runnable.run();
        }finally{
            end();
        }
    }
}