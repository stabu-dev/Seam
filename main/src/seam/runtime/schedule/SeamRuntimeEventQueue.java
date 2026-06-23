package seam.runtime.schedule;

import arc.func.*;
import arc.struct.*;
import arc.util.*;
import seam.runtime.*;
import seam.runtime.activation.*;

public final class SeamRuntimeEventQueue{
    private final SeamWorldActivationTransaction activation;
    private final Seq<Event> pending = new Seq<>();
    private final Seq<Event> drainingEvents = new Seq<>();

    private boolean draining;
    private Thread drainingThread;
    private long sequence;
    private int lastDrainCount;
    private long lastDrainNanos;

    public SeamRuntimeEventQueue(SeamWorldActivationTransaction activation){
        if(activation == null){
            throw new NullPointerException("activation");
        }

        this.activation = activation;
    }

    public boolean draining(){
        return draining;
    }

    public int pending(){
        return pending.size;
    }

    public int lastDrainCount(){
        return lastDrainCount;
    }

    public long lastDrainNanos(){
        return lastDrainNanos;
    }

    public long sequence(){
        return sequence;
    }

    public void enqueue(SeamRuntime target, Cons<SeamRuntime> task){
        enqueue(activation.activeRuntime(), target, task);
    }

    public void enqueue(SeamRuntime source, SeamRuntime target, Cons<SeamRuntime> task){
        if(target == null){
            throw new NullPointerException("target");
        }

        if(task == null){
            throw new NullPointerException("task");
        }

        if(source != null){
            source.requireWorldReady();
        }

        target.requireWorldReady();

        pending.add(new Event(sequence++, source, target, task));
    }

    public int drain(){
        beginDrain();

        int count = pending.size;
        long start = Time.nanos();

        try{
            drainingEvents.clear();

            for(Event event : pending){
                drainingEvents.add(event);
            }

            pending.clear();

            for(Event event : drainingEvents){
                execute(event);
            }

            lastDrainCount = count;
            return count;
        }finally{
            lastDrainNanos = Time.timeSinceNanos(start);
            drainingEvents.clear();
            endDrain();
        }
    }

    public void clear(){
        pending.clear();
        drainingEvents.clear();
        draining = false;
        drainingThread = null;
        sequence = 0L;
        lastDrainCount = 0;
        lastDrainNanos = 0L;
    }

    private void execute(Event event){
        event.target.requireWorldReady();

        activation.runScopedIn(event.target, () -> event.task.get(event.target));
    }

    private void beginDrain(){
        if(draining){
            throw new IllegalStateException("A Seam runtime event queue drain is already running.");
        }

        if(activation.switching()){
            throw new IllegalStateException("Cannot drain Seam runtime events while activation is switching.");
        }

        if(activation.scoped()){
            throw new IllegalStateException("Cannot drain Seam runtime events inside a scoped runtime context.");
        }

        draining = true;
        drainingThread = Thread.currentThread();
    }

    private void endDrain(){
        if(Thread.currentThread() != drainingThread){
            throw new IllegalStateException("A Seam runtime event queue drain can only end on the thread that started it.");
        }

        draining = false;
        drainingThread = null;
    }

    private static final class Event{
        private final long sequence;
        private final SeamRuntime source;
        private final SeamRuntime target;
        private final Cons<SeamRuntime> task;

        private Event(long sequence, SeamRuntime source, SeamRuntime target, Cons<SeamRuntime> task){
            this.sequence = sequence;
            this.source = source;
            this.target = target;
            this.task = task;
        }
    }
}