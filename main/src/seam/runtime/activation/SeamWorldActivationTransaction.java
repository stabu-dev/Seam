package seam.runtime.activation;

import arc.*;
import mindustry.game.EventType.*;
import seam.core.*;
import seam.runtime.*;
import seam.runtime.control.*;

public final class SeamWorldActivationTransaction{
    private final SeamHostContext host;
    private final SeamStateInstaller installer;

    private SeamRuntime activeRuntime;
    private SeamHostContext.Snapshot hostSnapshot;
    private boolean switching;
    private Thread switchingThread;
    private boolean scoped;
    private Thread scopedThread;

    public SeamWorldActivationTransaction(SeamHostContext host, SeamStateInstaller installer){
        if(host == null){
            throw new NullPointerException("host");
        }

        if(installer == null){
            throw new NullPointerException("installer");
        }

        this.host = host;
        this.installer = installer;
    }

    public boolean active(){
        return activeRuntime != null;
    }

    public boolean switching(){
        return switching;
    }

    public boolean scoped(){
        return scoped;
    }

    public SeamRuntime activeRuntime(){
        return activeRuntime;
    }

    public void activate(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        runtime.requireWorldReady();
        beginSwitch();

        SeamRuntime previousRuntime = activeRuntime;
        SeamHostContext.Snapshot previousHostSnapshot = hostSnapshot;

        try{
            if(previousRuntime == null){
                hostSnapshot = new SeamHostContext.Snapshot();
            }else{
                previousRuntime.captureCamera(Core.camera);
            }

            installRuntime(runtime);
            activeRuntime = runtime;

            fireWorldLoadEvent();

            installRuntime(runtime);
            SeamRuntimeValidator.validateActiveContext(runtime);
        }catch(Throwable e){
            rollback(previousRuntime, previousHostSnapshot);
            throw e;
        }finally{
            endSwitch();
        }
    }

    public void activateHost(){
        beginSwitch();

        SeamRuntime previousRuntime = activeRuntime;
        SeamHostContext.Snapshot snapshot = hostSnapshot;

        try{
            if(previousRuntime != null){
                previousRuntime.captureCamera(Core.camera);
            }

            if(snapshot != null){
                snapshot.restore();
            }else{
                host.restore();
            }

            activeRuntime = null;
            hostSnapshot = null;

            fireWorldLoadEvent();

            if(snapshot != null){
                snapshot.restore();
            }else{
                host.restore();
            }
        }catch(Throwable e){
            rollback(previousRuntime, snapshot);
            throw e;
        }finally{
            endSwitch();
        }
    }

    public void runIn(SeamRuntime runtime, Runnable task){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(task == null){
            throw new NullPointerException("task");
        }

        SeamRuntime previousRuntime = activeRuntime;

        if(previousRuntime == runtime){
            task.run();
            return;
        }

        activate(runtime);

        try{
            task.run();
        }finally{
            if(previousRuntime == null){
                activateHost();
            }else{
                activate(previousRuntime);
            }
        }
    }

    public void runInHost(Runnable task){
        if(task == null){
            throw new NullPointerException("task");
        }

        SeamRuntime previousRuntime = activeRuntime;

        if(previousRuntime == null){
            task.run();
            return;
        }

        activateHost();

        try{
            task.run();
        }finally{
            activate(previousRuntime);
        }
    }

    public void runScopedIn(SeamRuntime runtime, Runnable task){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(task == null){
            throw new NullPointerException("task");
        }

        runtime.requireWorldReady();

        SeamRuntime previousRuntime = activeRuntime;

        if(previousRuntime == runtime){
            task.run();
            return;
        }

        beginScope();

        SeamHostContext.Snapshot previousHost = null;

        try{
            if(previousRuntime == null){
                previousHost = new SeamHostContext.Snapshot();
            }else{
                previousRuntime.captureCamera(Core.camera);
            }

            installRuntime(runtime);
            activeRuntime = runtime;
            SeamRuntimeValidator.validateActiveContext(runtime);

            task.run();

            SeamRuntimeValidator.validateActiveContext(runtime);
        }finally{
            runtime.captureCamera(Core.camera);

            if(previousRuntime == null){
                activeRuntime = null;

                if(previousHost != null){
                    previousHost.restore();
                }else if(host.captured()){
                    host.restore();
                }
            }else{
                installRuntime(previousRuntime);
                activeRuntime = previousRuntime;
            }

            endScope();
        }
    }

    public void runScopedInHost(Runnable task){
        if(task == null){
            throw new NullPointerException("task");
        }

        SeamRuntime previousRuntime = activeRuntime;

        if(previousRuntime == null){
            task.run();
            return;
        }

        beginScope();

        try{
            previousRuntime.captureCamera(Core.camera);

            if(hostSnapshot != null){
                hostSnapshot.restore();
            }else if(host.captured()){
                host.restore();
            }

            activeRuntime = null;

            task.run();

            hostSnapshot = new SeamHostContext.Snapshot();
        }finally{
            installRuntime(previousRuntime);
            activeRuntime = previousRuntime;
            endScope();
        }
    }

    public void clear(){
        activeRuntime = null;
        hostSnapshot = null;
        switching = false;
        switchingThread = null;
        scoped = false;
        scopedThread = null;
    }

    private void installRuntime(SeamRuntime runtime){
        SeamContextSlots.installRuntime(runtime);
        runtime.applyCamera(Core.camera);
    }

    private static void fireWorldLoadEvent(){
        Events.fire(new WorldLoadEvent());
    }

    private void rollback(SeamRuntime previousRuntime, SeamHostContext.Snapshot previousHostSnapshot){
        try{
            if(previousRuntime == null){
                if(previousHostSnapshot != null){
                    previousHostSnapshot.restore();
                }else if(host.captured()){
                    host.restore();
                }

                activeRuntime = null;
                hostSnapshot = previousHostSnapshot;

                fireWorldLoadEvent();

                if(previousHostSnapshot != null){
                    previousHostSnapshot.restore();
                }else if(host.captured()){
                    host.restore();
                }
            }else{
                installRuntime(previousRuntime);
                activeRuntime = previousRuntime;
                hostSnapshot = previousHostSnapshot;

                fireWorldLoadEvent();

                installRuntime(previousRuntime);
            }
        }catch(Throwable ignored){
        }
    }

    private void beginSwitch(){
        if(switching){
            throw new IllegalStateException("A Seam world activation transaction is already running.");
        }

        if(scoped){
            throw new IllegalStateException("Cannot activate a Seam world while a scoped runtime context is active.");
        }

        if(installer.active()){
            throw new IllegalStateException("Cannot activate a Seam world while a runtime state installer transaction is active.");
        }

        switching = true;
        switchingThread = Thread.currentThread();
    }

    private void endSwitch(){
        if(Thread.currentThread() != switchingThread){
            throw new IllegalStateException("A Seam world activation transaction can only end on the thread that started it.");
        }

        switching = false;
        switchingThread = null;
    }

    private void beginScope(){
        if(scoped){
            throw new IllegalStateException("A scoped Seam runtime context is already active.");
        }

        if(switching){
            throw new IllegalStateException("Cannot enter a scoped Seam runtime context while activation is switching.");
        }

        if(installer.active()){
            throw new IllegalStateException("Cannot enter a scoped Seam runtime context while installer is active.");
        }

        scoped = true;
        scopedThread = Thread.currentThread();
    }

    private void endScope(){
        if(Thread.currentThread() != scopedThread){
            throw new IllegalStateException("A scoped Seam runtime context can only end on the thread that started it.");
        }

        scoped = false;
        scopedThread = null;
    }
}