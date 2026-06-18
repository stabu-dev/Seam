package seam.runtime.delayed;

import arc.*;
import arc.util.*;
import arc.util.Timer.*;
import seam.runtime.*;
import seam.runtime.activation.*;

public final class SeamRuntimeDelayedRuns{
    private final SeamWorldActivationTransaction activation;

    private SeamRuntime activeRuntime;
    private boolean activeHost;
    private Thread activeThread;

    public SeamRuntimeDelayedRuns(SeamWorldActivationTransaction activation){
        if(activation == null){
            throw new NullPointerException("activation");
        }

        this.activation = activation;
    }

    public boolean active(){
        return activeRuntime != null || activeHost;
    }

    public boolean activeHost(){
        return activeHost;
    }

    public SeamRuntime activeRuntime(){
        return activeRuntime;
    }

    public void runCurrent(float delay, Runnable task){
        Context context = captureCurrent();
        run(context, delay, task);
    }

    public void run(SeamRuntime runtime, float delay, Runnable task){
        requireRuntime(runtime);
        run(Context.runtime(runtime), delay, task);
    }

    public void runHost(float delay, Runnable task){
        run(Context.host(), delay, task);
    }

    public Task runTaskCurrent(float delay, Runnable task){
        Context context = captureCurrent();
        return runTask(context, delay, task);
    }

    public Task runTask(SeamRuntime runtime, float delay, Runnable task){
        requireRuntime(runtime);
        return runTask(Context.runtime(runtime), delay, task);
    }

    public Task runTaskHost(float delay, Runnable task){
        return runTask(Context.host(), delay, task);
    }

    public void postCurrent(Runnable task){
        Context context = captureCurrent();
        post(context, task);
    }

    public void post(SeamRuntime runtime, Runnable task){
        requireRuntime(runtime);
        post(Context.runtime(runtime), task);
    }

    public void postHost(Runnable task){
        post(Context.host(), task);
    }

    public void clear(){
        activeRuntime = null;
        activeHost = false;
        activeThread = null;
    }

    private void run(Context context, float delay, Runnable task){
        requireDelay(delay);
        requireTask(task);
        Time.run(delay, () -> execute(context, task));
    }

    private Task runTask(Context context, float delay, Runnable task){
        requireDelay(delay);
        requireTask(task);
        return Time.runTask(delay, () -> execute(context, task));
    }

    private void post(Context context, Runnable task){
        requireTask(task);

        Runnable wrapped = () -> execute(context, task);

        if(Core.app == null){
            wrapped.run();
        }else{
            Core.app.post(wrapped);
        }
    }

    private Context captureCurrent(){
        SeamRuntime runtime = activation.activeRuntime();
        return runtime == null ? Context.host() : Context.runtime(runtime);
    }

    private void execute(Context context, Runnable task){
        if(active()){
            throw new IllegalStateException("A Seam delayed runtime task is already running.");
        }

        activeRuntime = context.runtime;
        activeHost = context.host;
        activeThread = Thread.currentThread();

        try{
            if(context.host){
                activation.runScopedInHost(task);
            }else{
                activation.runScopedIn(context.runtime, task);
            }
        }finally{
            clear();
        }
    }

    private static void requireRuntime(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        runtime.requireWorldReady();
    }

    private static void requireDelay(float delay){
        if(Float.isNaN(delay) || Float.isInfinite(delay)){
            throw new IllegalArgumentException("Delay must be finite.");
        }

        if(delay < 0f){
            throw new IllegalArgumentException("Delay must not be negative.");
        }
    }

    private static void requireTask(Runnable task){
        if(task == null){
            throw new NullPointerException("task");
        }
    }

    private static final class Context{
        private final SeamRuntime runtime;
        private final boolean host;

        private Context(SeamRuntime runtime, boolean host){
            this.runtime = runtime;
            this.host = host;
        }

        private static Context runtime(SeamRuntime runtime){
            return new Context(runtime, false);
        }

        private static Context host(){
            return new Context(null, true);
        }
    }
}