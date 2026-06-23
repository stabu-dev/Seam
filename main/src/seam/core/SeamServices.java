package seam.core;

import arc.*;
import mindustry.game.EventType.*;
import seam.runtime.*;
import seam.runtime.activation.*;
import seam.runtime.control.*;
import seam.runtime.delayed.*;
import seam.runtime.load.*;
import seam.runtime.mutation.*;
import seam.runtime.schedule.*;
import seam.runtime.update.*;

public final class SeamServices{
    public final SeamHostContext host = new SeamHostContext();
    public final SeamStateInstaller installer = new SeamStateInstaller(host);
    public final SeamRuntimeRegistry runtimes = new SeamRuntimeRegistry();
    public final SeamRuntimeExecutor executor = new SeamRuntimeExecutor(runtimes, installer);
    public final SeamRuntimeStateLoader stateLoader = new SeamRuntimeStateLoader();
    public final SeamWorldActivationTransaction activation = new SeamWorldActivationTransaction(host, installer);
    public final SeamRuntimeDelayedRuns delayed = new SeamRuntimeDelayedRuns(activation);
    public final SeamRuntimePhaseGuard phaseGuard = new SeamRuntimePhaseGuard(activation, installer);
    public final SeamRuntimeUpdateTransaction update = new SeamRuntimeUpdateTransaction(phaseGuard);
    public final SeamRuntimeUpdatePipeline updatePipeline = new SeamRuntimeUpdatePipeline(update);
    public final SeamRuntimeEventQueue runtimeEvents = new SeamRuntimeEventQueue(activation);
    public final SeamRuntimeScheduler scheduler = new SeamRuntimeScheduler(runtimes, activation, updatePipeline, runtimeEvents);
    public final SeamRuntimeMutationTransaction mutation = new SeamRuntimeMutationTransaction(phaseGuard);

    private boolean eventsInstalled;

    public void installEvents(){
        if(eventsInstalled){
            return;
        }

        eventsInstalled = true;
        host.captureCurrent();

        Events.on(ResetEvent.class, event -> clearForReset());
    }

    public void clearForReset(){
        if(phaseGuard.active()){
            phaseGuard.clear();
        }

        if(installer.active()){
            installer.abort();
        }

        scheduler.clear();
        delayed.clear();
        activation.clear();
        runtimes.clear();
        host.captureCurrent();
    }
}