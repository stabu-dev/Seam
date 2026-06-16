package seam.runtime.control;

import arc.*;
import seam.core.*;
import seam.runtime.*;

public final class SeamStateInstaller{
    private final SeamHostContext host;

    private SeamRuntime activeRuntime;
    private SeamPhase activePhase;
    private SeamHostContext.Snapshot transactionSnapshot;
    private Thread activeThread;

    public SeamStateInstaller(SeamHostContext host){
        if(host == null){
            throw new NullPointerException("host");
        }

        this.host = host;
    }

    public boolean active(){
        return activeRuntime != null;
    }

    public int depth(){
        return active() ? 1 : 0;
    }

    public SeamRuntime current(){
        return activeRuntime;
    }

    public SeamPhase currentPhase(){
        return activePhase;
    }

    public void install(SeamRuntime runtime, SeamPhase phase){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        if(active()){
            throw new IllegalStateException("A Seam runtime state is already installed: " + activeRuntime + " / " + activePhase);
        }

        runtime.requireWorldReady();

        transactionSnapshot = new SeamHostContext.Snapshot();
        activeRuntime = runtime;
        activePhase = phase;
        activeThread = Thread.currentThread();

        SeamContextSlots.installRuntime(runtime);
        runtime.applyCamera(Core.camera);
    }

    public void restore(){
        requireActiveThread();

        SeamRuntime runtime = activeRuntime;
        SeamHostContext.Snapshot restore = transactionSnapshot;

        transactionSnapshot = null;
        activeRuntime = null;
        activePhase = null;
        activeThread = null;

        if(runtime != null){
            runtime.captureCamera(Core.camera);
        }

        if(restore != null){
            restore.restore();
        }else{
            host.restore();
        }
    }

    public void abort(){
        if(active()){
            restore();
        }
    }

    public void restoreHost(){
        if(active()){
            throw new IllegalStateException("Cannot restore host while a Seam runtime state is installed.");
        }

        host.restore();
    }

    private void requireActiveThread(){
        if(!active()){
            throw new IllegalStateException("No Seam runtime state is installed.");
        }

        if(Thread.currentThread() != activeThread){
            throw new IllegalStateException("A Seam runtime state can only be restored by the thread that installed it.");
        }
    }
}