package seam.runtime.update;

import arc.func.*;
import seam.core.*;
import seam.runtime.*;
import seam.runtime.control.*;

public final class SeamRuntimeUpdateTransaction{
    private final SeamRuntimePhaseGuard guard;

    public SeamRuntimeUpdateTransaction(SeamRuntimePhaseGuard guard){
        if(guard == null){
            throw new NullPointerException("guard");
        }

        this.guard = guard;
    }

    public boolean active(){
        return guard.active(SeamPhase.update);
    }

    public SeamRuntime current(){
        return guard.current(SeamPhase.update);
    }

    public void run(SeamRuntime runtime, Cons<SeamRuntime> task){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(task == null){
            throw new NullPointerException("task");
        }

        SeamRuntimePhaseGuard.Token token = guard.begin(runtime, SeamPhase.update);

        try{
            SeamRuntimeValidator.validateActiveContext(runtime);
            task.get(runtime);
            SeamRuntimeValidator.validateActiveContext(runtime);
        }finally{
            guard.end(token);
        }
    }

    public void clear(){
        if(active()){
            guard.clear();
        }
    }
}