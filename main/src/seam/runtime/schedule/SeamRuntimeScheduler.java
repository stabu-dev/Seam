package seam.runtime.schedule;

import arc.struct.*;
import arc.util.*;
import seam.runtime.*;
import seam.runtime.activation.*;
import seam.runtime.update.*;

public final class SeamRuntimeScheduler{
    private final SeamRuntimeRegistry runtimes;
    private final SeamWorldActivationTransaction activation;
    private final SeamRuntimeUpdatePipeline updatePipeline;
    private final SeamRuntimeEventQueue runtimeEvents;
    private final SeamRuntimeFrameClock frameClock;
    private final Seq<RuntimeProfile> profiles = new Seq<>();

    private boolean active;
    private Thread activeThread;
    private long tick;
    private float lastStateDelta;
    private float lastTimeDelta;
    private long lastTotalNanos;
    private int lastEventCount;
    private long lastEventNanos;

    public SeamRuntimeScheduler(
    SeamRuntimeRegistry runtimes,
    SeamWorldActivationTransaction activation,
    SeamRuntimeUpdatePipeline updatePipeline,
    SeamRuntimeEventQueue runtimeEvents,
    SeamRuntimeFrameClock frameClock
    ){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(activation == null){
            throw new NullPointerException("activation");
        }

        if(updatePipeline == null){
            throw new NullPointerException("updatePipeline");
        }

        if(runtimeEvents == null){
            throw new NullPointerException("runtimeEvents");
        }

        if(frameClock == null){
            throw new NullPointerException("frameClock");
        }

        this.runtimes = runtimes;
        this.activation = activation;
        this.updatePipeline = updatePipeline;
        this.runtimeEvents = runtimeEvents;
        this.frameClock = frameClock;
    }

    public boolean active(){
        return active;
    }

    public long tick(){
        return tick;
    }

    public float lastDelta(){
        return lastStateDelta;
    }

    public float lastStateDelta(){
        return lastStateDelta;
    }

    public float lastTimeDelta(){
        return lastTimeDelta;
    }

    public long lastTotalNanos(){
        return lastTotalNanos;
    }

    public int lastEventCount(){
        return lastEventCount;
    }

    public long lastEventNanos(){
        return lastEventNanos;
    }

    public Seq<RuntimeProfile> profiles(){
        return profiles;
    }

    public void step(){
        step(frameClock.sample());
    }

    public void step(float delta){
        step(frameClock.fixed(delta));
    }

    public void step(float stateDelta, float timeDelta){
        step(frameClock.delta(stateDelta, timeDelta));
    }

    public void step(SeamRuntimeFrameClock.FrameDelta delta){
        validateDelta(delta);
        begin();

        long start = Time.nanos();

        try{
            profiles.clear();
            lastStateDelta = delta.stateDelta;
            lastTimeDelta = delta.timeDelta;
            lastEventCount = 0;
            lastEventNanos = 0L;

            for(SeamRuntime runtime : runtimes.all()){
                stepRuntime(runtime, delta);
            }

            long eventStart = Time.nanos();
            lastEventCount = runtimeEvents.drain();
            lastEventNanos = Time.timeSinceNanos(eventStart);

            tick++;
        }finally{
            lastTotalNanos = Time.timeSinceNanos(start);
            end();
        }
    }

    public void clear(){
        active = false;
        activeThread = null;
        tick = 0L;
        lastStateDelta = 0f;
        lastTimeDelta = 0f;
        lastTotalNanos = 0L;
        lastEventCount = 0;
        lastEventNanos = 0L;
        profiles.clear();
        runtimeEvents.clear();
    }

    private void stepRuntime(SeamRuntime runtime, SeamRuntimeFrameClock.FrameDelta delta){
        if(runtime == null){
            throw new IllegalStateException("Runtime registry contains null runtime.");
        }

        runtime.requireWorldReady();

        long start = Time.nanos();

        activation.runScopedIn(runtime, () -> updatePipeline.stepGameplayBase(runtime, delta.stateDelta, delta.timeDelta));

        profiles.add(new RuntimeProfile(
        runtime.id,
        runtime.name,
        Time.timeSinceNanos(start),
        runtime.state.tick,
        runtime.state.updateId,
        runtime.groups.unit.size(),
        runtime.groups.build.size(),
        runtime.groups.bullet.size()
        ));
    }

    private void begin(){
        if(active){
            throw new IllegalStateException("A Seam runtime scheduler pass is already running.");
        }

        if(runtimeEvents.draining()){
            throw new IllegalStateException("Cannot start Seam runtime scheduler while runtime events are draining.");
        }

        if(activation.switching()){
            throw new IllegalStateException("Cannot start Seam runtime scheduler while activation is switching.");
        }

        if(activation.scoped()){
            throw new IllegalStateException("Cannot start Seam runtime scheduler inside a scoped runtime context.");
        }

        active = true;
        activeThread = Thread.currentThread();
    }

    private void end(){
        if(Thread.currentThread() != activeThread){
            throw new IllegalStateException("A Seam runtime scheduler pass can only end on the thread that started it.");
        }

        active = false;
        activeThread = null;
    }

    private static void validateDelta(SeamRuntimeFrameClock.FrameDelta delta){
        if(delta == null){
            throw new NullPointerException("delta");
        }

        validateDelta("stateDelta", delta.stateDelta);
        validateDelta("timeDelta", delta.timeDelta);
    }

    private static void validateDelta(String name, float delta){
        if(Float.isNaN(delta) || Float.isInfinite(delta)){
            throw new IllegalArgumentException("Scheduler " + name + " must be finite.");
        }

        if(delta < 0f){
            throw new IllegalArgumentException("Scheduler " + name + " must not be negative.");
        }
    }

    public static final class RuntimeProfile{
        public final int runtimeId;
        public final String runtimeName;
        public final long nanos;
        public final double stateTick;
        public final long updateId;
        public final int units;
        public final int builds;
        public final int bullets;

        private RuntimeProfile(
        int runtimeId,
        String runtimeName,
        long nanos,
        double stateTick,
        long updateId,
        int units,
        int builds,
        int bullets
        ){
            this.runtimeId = runtimeId;
            this.runtimeName = runtimeName;
            this.nanos = nanos;
            this.stateTick = stateTick;
            this.updateId = updateId;
            this.units = units;
            this.builds = builds;
            this.bullets = bullets;
        }
    }
}