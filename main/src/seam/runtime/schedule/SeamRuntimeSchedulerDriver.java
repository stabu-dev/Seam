package seam.runtime.schedule;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import seam.runtime.*;

public final class SeamRuntimeSchedulerDriver{
    private final SeamRuntimeRegistry runtimes;
    private final SeamRuntimeScheduler scheduler;

    private boolean eventsInstalled;
    private boolean enabled;
    private boolean running;
    private Thread runningThread;
    private long frames;
    private long lastNanos;

    public SeamRuntimeSchedulerDriver(SeamRuntimeRegistry runtimes, SeamRuntimeScheduler scheduler){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(scheduler == null){
            throw new NullPointerException("scheduler");
        }

        this.runtimes = runtimes;
        this.scheduler = scheduler;
    }

    public boolean enabled(){
        return enabled;
    }

    public boolean running(){
        return running;
    }

    public long frames(){
        return frames;
    }

    public long lastNanos(){
        return lastNanos;
    }

    public void installEvents(){
        if(eventsInstalled){
            return;
        }

        eventsInstalled = true;

        Events.run(Trigger.update, this::update);
    }

    public void enable(){
        enabled = true;
    }

    public void disable(){
        enabled = false;
    }

    public void update(){
        if(!enabled || runtimes.empty()){
            return;
        }

        stepFrame();
    }

    public void stepFrame(){
        begin();

        long start = Time.nanos();

        try{
            scheduler.step();
            frames++;
        }finally{
            lastNanos = Time.timeSinceNanos(start);
            end();
        }
    }

    public void clear(){
        enabled = false;
        running = false;
        runningThread = null;
        frames = 0L;
        lastNanos = 0L;
    }

    private void begin(){
        if(running){
            throw new IllegalStateException("A Seam runtime scheduler driver frame is already running.");
        }

        if(scheduler.active()){
            throw new IllegalStateException("Cannot start Seam runtime scheduler driver while scheduler is active.");
        }

        running = true;
        runningThread = Thread.currentThread();
    }

    private void end(){
        if(Thread.currentThread() != runningThread){
            throw new IllegalStateException("A Seam runtime scheduler driver frame can only end on the thread that started it.");
        }

        running = false;
        runningThread = null;
    }
}