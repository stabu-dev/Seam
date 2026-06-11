package seam.runtime.control;

import seam.core.*;
import seam.runtime.*;

public final class SeamRuntimeExecutor{
    @FunctionalInterface
    public interface Call<T>{
        T run(SeamRuntime runtime);
    }

    private final SeamRuntimeRegistry runtimes;
    private final SeamRuntimeStack stack;

    private boolean validateAccess = true;

    public SeamRuntimeExecutor(SeamRuntimeRegistry runtimes, SeamRuntimeStack stack){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(stack == null){
            throw new NullPointerException("stack");
        }

        this.runtimes = runtimes;
        this.stack = stack;
    }

    public boolean validateAccess(){
        return validateAccess;
    }

    public void validateAccess(boolean validateAccess){
        this.validateAccess = validateAccess;
    }

    public <T> T call(SeamRuntime runtime, SeamPhase phase, Call<T> task){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        if(task == null){
            throw new NullPointerException("task");
        }

        runtime.requireWorldReady();

        stack.enter(runtime, phase);

        try{
            if(validateAccess){
                SeamRuntimeValidator.validateActiveContext(runtime);
            }

            return task.run(runtime);
        }finally{
            stack.exit();
        }
    }

    public <T> T callRegistered(SeamRuntime runtime, SeamPhase phase, Call<T> task){
        requireRegistered(runtime);
        return call(runtime, phase, task);
    }

    public <T> T callExclusive(SeamRuntime runtime, SeamPhase phase, Call<T> task){
        requireExclusive();
        return call(runtime, phase, task);
    }

    public <T> T callRegisteredExclusive(SeamRuntime runtime, SeamPhase phase, Call<T> task){
        requireExclusive();
        requireRegistered(runtime);
        return call(runtime, phase, task);
    }

    public void requireRegistered(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(runtimes.get(runtime.id) != runtime){
            throw new IllegalStateException("Runtime is not registered: " + runtime);
        }
    }

    public void requireExclusive(){
        if(stack.active()){
            throw new IllegalStateException("Cannot perform exclusive Seam runtime operation while runtime stack is active.");
        }
    }
}
