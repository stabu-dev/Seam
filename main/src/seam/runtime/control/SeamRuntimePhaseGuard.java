package seam.runtime.control;

import seam.core.*;
import seam.runtime.*;
import seam.runtime.activation.*;

public final class SeamRuntimePhaseGuard{
    private final SeamWorldActivationTransaction activation;
    private final SeamStateInstaller installer;

    private SeamRuntime activeRuntime;
    private SeamPhase activePhase;
    private Thread activeThread;

    public SeamRuntimePhaseGuard(SeamWorldActivationTransaction activation, SeamStateInstaller installer){
        if(activation == null){
            throw new NullPointerException("activation");
        }

        if(installer == null){
            throw new NullPointerException("installer");
        }

        this.activation = activation;
        this.installer = installer;
    }

    public boolean active(){
        return activePhase != null;
    }

    public boolean active(SeamPhase phase){
        return activePhase == phase;
    }

    public SeamRuntime current(){
        return activeRuntime;
    }

    public SeamRuntime current(SeamPhase phase){
        return activePhase == phase ? activeRuntime : null;
    }

    public SeamPhase phase(){
        return activePhase;
    }

    public Token begin(SeamRuntime runtime, SeamPhase phase){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        if(active()){
            throw new IllegalStateException("A Seam runtime " + activePhase + " phase is already running: " + activeRuntime);
        }

        if(installer.active()){
            throw new IllegalStateException("Cannot start Seam runtime " + phase + " phase while installer is active.");
        }

        if(activation.switching()){
            throw new IllegalStateException("Cannot start Seam runtime " + phase + " phase while world activation is switching.");
        }

        if(!activation.active()){
            throw new IllegalStateException("Cannot start Seam runtime " + phase + " phase when no runtime world is active.");
        }

        if(activation.activeRuntime() != runtime){
            throw new IllegalStateException("Cannot start Seam runtime " + phase + " phase for inactive runtime. Active runtime: " + activation.activeRuntime() + ", requested: " + runtime);
        }

        runtime.requireWorldReady();

        activeRuntime = runtime;
        activePhase = phase;
        activeThread = Thread.currentThread();

        return new Token(this, runtime, phase, activeThread);
    }

    public void end(Token token){
        if(token == null){
            throw new NullPointerException("token");
        }

        if(token.guard != this){
            throw new IllegalStateException("Seam runtime phase token belongs to another guard.");
        }

        if(token.closed){
            throw new IllegalStateException("Seam runtime phase token is already closed.");
        }

        if(!active()){
            throw new IllegalStateException("No Seam runtime phase is active.");
        }

        if(activeRuntime != token.runtime || activePhase != token.phase || activeThread != token.thread){
            throw new IllegalStateException("Seam runtime phase token does not match the active phase.");
        }

        if(Thread.currentThread() != activeThread){
            throw new IllegalStateException("A Seam runtime phase can only end on the thread that started it.");
        }

        token.closed = true;
        activeRuntime = null;
        activePhase = null;
        activeThread = null;
    }

    public void clear(){
        activeRuntime = null;
        activePhase = null;
        activeThread = null;
    }

    public static final class Token{
        private final SeamRuntimePhaseGuard guard;
        private final SeamRuntime runtime;
        private final SeamPhase phase;
        private final Thread thread;
        private boolean closed;

        private Token(SeamRuntimePhaseGuard guard, SeamRuntime runtime, SeamPhase phase, Thread thread){
            this.guard = guard;
            this.runtime = runtime;
            this.phase = phase;
            this.thread = thread;
        }
    }
}