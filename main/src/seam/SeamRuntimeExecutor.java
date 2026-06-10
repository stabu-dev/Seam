package seam;

public final class SeamRuntimeExecutor{
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

    public <T> T call(SeamRuntime runtime, SeamPhase phase, SeamRuntimeCallable<T> callable){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        if(callable == null){
            throw new NullPointerException("callable");
        }

        runtime.requireWorldReady();

        stack.enter(runtime, phase);

        try{
            if(validateAccess){
                SeamRuntimeValidator.validateActiveContext(runtime);
            }

            return callable.call(runtime);
        }finally{
            stack.exit();
        }
    }

    public <T> T callRegistered(SeamRuntime runtime, SeamPhase phase, SeamRuntimeCallable<T> callable){
        requireRegistered(runtime);
        return call(runtime, phase, callable);
    }

    public <T> T callExclusive(SeamRuntime runtime, SeamPhase phase, SeamRuntimeCallable<T> callable){
        requireExclusive();
        return call(runtime, phase, callable);
    }

    public <T> T callRegisteredExclusive(SeamRuntime runtime, SeamPhase phase, SeamRuntimeCallable<T> callable){
        requireExclusive();
        requireRegistered(runtime);
        return call(runtime, phase, callable);
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