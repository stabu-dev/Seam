package seam.core;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveFileReader.*;
import mindustry.net.*;
import seam.gen.*;

import java.lang.reflect.*;

/**
 * Isolated world scope. Swaps world-owned static fields, final container contents and processor globals around each entry.
 * Failed exit capture invalidates this context; failed host restoration blocks further entry.
 */
public final class Context implements Disposable, AutoCloseable{
    static final Seq<Field> fields = WorldFrame.fields;
    static final Seq<Field> contents = WorldFrame.contents;
    static final Seq<Runnable> services = WorldFrame.services;
    static final Seq<String> listening = WorldFrame.listening;

    private static final Thread thread = Core.app != null && Core.app.getMainThread() != null ? Core.app.getMainThread() : Thread.currentThread();
    private static final WorldFrame host = new WorldFrame(null);
    private static final Seq<Context> pending = new Seq<>();
    private static final Seq<Context> live = new Seq<>();
    private static final Seq<Runnable> preCreateChecks = new Seq<>();
    private static final Seq<CreateHook> createHooks = new Seq<>();
    private static Context current;
    private static Field graphicsDelta;
    private static Class<?> graphicsType;
    private static long nextId = 1L;
    private static boolean changing, restoreFailed, hostWorkers, hostAmbient, draining;
    private static int disposing;

    final long id = nextId++;
    final ObjectMap<Binding<?>, Binding.Value<?>> bindings = new ObjectMap<>();
    /** Deferred-work owner, or null when omitted via {@link Config#deferred}. */
    public final Deferred deferred;
    /** Receives packets broadcast by this world while the host runs a server. */
    public final Broadcast broadcast;
    final boolean localSimulation;
    private final @Nullable Runnable updateLogic;
    private final WorldFrame frame = new WorldFrame(this);
    private final Seq<Cleanup> owned = new Seq<>();
    private Workers workers;
    private boolean invalid, updating, disposed, stopped, closing, failed, released, automaticRejected, admitting;
    private @Nullable Deferred admitted;
    private int disposalPart, depth;
    private Runnable rollback;

    /** Creates a headless child world with default native services. */
    public Context(){
        this(config -> {});
    }

    /** Configures fresh native factories before construction. Nested construction rollback runs at the enclosing host boundary. */
    public Context(Cons<Config> configure){
        checkThread();
        if(configure == null) throw new IllegalArgumentException("Configuration is required.");
        for(Runnable check : preCreateChecks) check.run();
        Config config = new Config();
        configure.get(config);
        if(config.broadcast == null) throw new IllegalArgumentException("A broadcast receiver is required.");
        broadcast = config.broadcast;
        localSimulation = config.localSimulation;
        updateLogic = config.updateLogic;
        if(localSimulation) Network.enableLocalSimulation();
        if(!hostAmbient && current == null && !Vars.headless && Vars.control != null && Vars.control.sound != null){
            Workers.installHostAmbient();
            hostAmbient = true;
        }
        if(!hostWorkers){
            new Workers();
            hostWorkers = true;
        }
        try{
            Deferred component;
            admitting = true;
            try{
                component = config.deferred == null ? null : config.deferred.get(this);
            }finally{
                admitting = false;
            }
            if(component != null && component != admitted) throw new IllegalArgumentException("The deferred component must be created for the context being constructed.");
            deferred = component;
            if(deferred != null) own(deferred);
            for(CreateHook hook : createHooks) if(hook.before != null) hook.before.get(this);
            config.before.get(this);
            boolean headless = config.headless || Vars.renderer == null;
            run(() -> {
                Vars.headless = headless;
                Groups.init();
                Vars.world = config.world.get();
                Vars.state = config.state.get();
                if(Vars.world == null || Vars.state == null) throw new IllegalArgumentException("World and state are required.");
                Vars.asyncCore = config.asyncCore.get();
                Vars.spawner = config.spawner.get();
                Vars.indexer = config.indexer.get();
                Vars.pathfinder = config.pathfinder.get();
                Vars.controlPath = Workers.controlPath(config.controlPath);
                Vars.fogControl = config.fogControl.get();
                workers = new Workers();
                Vars.logic = config.logic.get();
                services.each(Runnable::run);
                for(CreateHook hook : createHooks) if(hook.after != null) hook.after.get(this);
                config.after.get(this);
            });
            live.add(this);
        }catch(Throwable failure){
            failed = true;
            rollback = () -> {
                try{
                    disposeContext();
                }catch(Throwable cleanup){
                    if(cleanup != failure) failure.addSuppressed(cleanup);
                    throw failure;
                }
            };
            pending.add(this);
            if(current == null && !changing && !restoreFailed) cleanup(failure, rollback);
            throw failure;
        }
    }

    /** Handles packets broadcast while simulating inside a child world. */
    public interface Broadcast{
        /** @param except connection excluded by the native call, or null */
        void send(Object packet, boolean reliable, @Nullable NetConnection except);
    }

    /**
     * Native factories run in declaration order inside the new context. {@code deferred} and {@code before} run in the caller first.
     * Factories must return fresh instances; all except {@code world} and {@code state} may return null.
     */
    public static final class Config{
        /** Runs offline even while connected to a server; host network I/O stays outside this world. */
        public boolean localSimulation;
        /** Deferred-work factory run in the caller before {@link #before}. Null disables tracking. */
        public Func<Context, Deferred> deferred = Deferred::new;
        /** Runs in the caller before entry (for {@link Binding} assignments). */
        public Cons<Context> before = context -> {};
        public Prov<World> world = World::new;
        public Prov<GameState> state = GameState::new;
        public Prov<AsyncCore> asyncCore = AsyncCore::new;
        public Prov<WaveSpawner> spawner = WaveSpawner::new;
        public Prov<BlockIndexer> indexer = BlockIndexer::new;
        public Prov<Pathfinder> pathfinder = Pathfinder::new;
        public Prov<ControlPathfinder> controlPath = ControlPathfinder::new;
        public Prov<FogControl> fogControl = FogControl::new;
        public Prov<Logic> logic = Logic::new;
        /** Replaces native logic inside {@link #update(float)} while keeping clock and worker barriers. */
        public @Nullable Runnable updateLogic;
        /** Runs installed after native services, for custom components and map loading. */
        public Cons<Context> after = context -> {};
        /** Receives broadcasts while the host runs a server. Drops packets by default. */
        public Broadcast broadcast = (packet, reliable, except) -> {};
        /** Skips visual effects, sounds and renderer updates. Always true on servers. */
        public boolean headless = true;
    }

    /** Installs deferred queue, Timer and executor hooks early so mods initializing later route by owner. */
    public static void initHooks(){
        DeferredHooks.install();
    }

    static long id(@Nullable Context world){
        return world == null ? 0L : world.id;
    }

    static @Nullable GameState stateOf(@Nullable Context world){
        return world == null ? host.state() : world.frame.state();
    }

    static void eachLive(Cons<Context> consumer){
        for(int i = 0; i < live.size; i++){
            Context world = live.get(i);
            if(world.isReady()) consumer.get(world);
        }
    }

    void resetSavesCursor(){
        frame.resetSavesCursor();
    }

    /** Registers a check run on the host thread before every new context is created. */
    public static Disposable onBeforeCreate(Runnable check){
        checkThread();
        if(check == null) throw new IllegalArgumentException("Check is required.");
        preCreateChecks.add(check);
        return new Disposable(){
            boolean done;
            @Override public void dispose(){ checkThread(); if(!done){ done = true; preCreateChecks.remove(check, true); } }
            @Override public boolean isDisposed(){ checkThread(); return done; }
        };
    }

    /** Runs optional callbacks before and after each new context initializes. */
    public static Disposable onCreate(@Nullable Cons<Context> before, @Nullable Cons<Context> after){
        checkThread();
        var hook = new CreateHook(before, after);
        createHooks.add(hook);
        return new Disposable(){
            boolean done;
            @Override public void dispose(){ checkThread(); if(!done){ done = true; createHooks.remove(hook, true); } }
            @Override public boolean isDisposed(){ checkThread(); return done; }
        };
    }

    /** Shares matching stateless host {@link CustomChunk}s into every new child world. */
    public static Disposable inheritChunks(Boolf2<String, CustomChunk> filter){
        checkThread();
        if(filter == null) throw new IllegalArgumentException("Chunk filter is required.");
        return WorldFrame.inheritChunks(filter);
    }

    /** Disposes {@code resource} in host scope in reverse registration order with {@link #onDispose(Runnable)}. */
    public <T extends Disposable> T own(T resource){
        checkUsable();
        if(resource == null || resource == this || owned.contains(entry -> entry.resource == resource)) throw new IllegalArgumentException("A distinct resource is required.");
        owned.add(new Cleanup(resource, false));
        return resource;
    }

    /** Runs cleanup with this context installed, after workers stop and before native entities/events clear. */
    public void onDispose(Runnable task){
        checkUsable();
        if(task == null) throw new IllegalArgumentException("Cleanup is required.");
        owned.add(new Cleanup(task::run, true));
    }

    @Override
    public boolean isDisposed(){
        checkThread();
        return disposed;
    }

    /** Closes outside all context scopes. Joins workers, runs owned cleanup in reverse order, clears local entities/timers/events and drops snapshots. */
    @Override
    public void dispose(){
        checkThread();
        try{
            checkHost();
            automaticRejected = false;
            disposeContext();
        }catch(Throwable failure){
            if(current == null && !changing && !released && !pending.contains(this, true)) pending.add(this);
            throw failure;
        }
    }

    /** Schedules close at the next consistent host boundary. Remains usable until teardown starts. */
    public void closeLater(){
        checkThread();
        if(changing) throw new IllegalStateException("Cannot request close during state exchange.");
        if(released) return;
        automaticRejected = false;
        if(current == null && !restoreFailed){
            dispose();
        }else if(!pending.contains(this, true)){
            pending.add(this);
        }
    }

    @Override
    public void close(){
        dispose();
    }

    /** @return installed world, or null for the host */
    public static Context current(){
        checkThread();
        return current;
    }

    /** Runs one native logic frame with {@code delta} ticks and restores the caller's timing on exit. */
    public void update(float delta){
        if(updating) throw new IllegalStateException("Cannot update a world during its own update.");
        run(() -> updateFrame(delta));
    }

    /** Runs {@link #update(float)} if the caller's native and tracked work has finished; returns false without entering otherwise. */
    public boolean tryUpdate(float delta){
        checkUsable();
        if(updating) throw new IllegalStateException("Cannot update a world during its own update.");
        return enter(() -> {
            updateFrame(delta);
            return true;
        }, false) != null;
    }

    /** Enters this world to run {@code task}, then restores the previous scope on exit. */
    public void run(Runnable task){
        if(task == null) throw new IllegalArgumentException("Task is required.");
        get(() -> {
            task.run();
            return null;
        });
    }

    /** Runs {@code task} if the caller's native and tracked work has finished; returns false without entering otherwise. */
    public boolean tryRun(Runnable task){
        checkUsable();
        if(task == null) throw new IllegalArgumentException("Task is required.");
        return enter(() -> {
            task.run();
            return true;
        }, false) != null;
    }

    /** Enters this world to compute {@code task}, then restores the previous scope on exit. */
    public <T> T get(Prov<T> task){
        checkUsable();
        return enter(task);
    }

    void checkUsable(){
        checkThread();
        if(disposed) throw new IllegalStateException("Context is disposed.");
        if(failed) throw new IllegalStateException("Context construction failed.");
        if(closing) throw new IllegalStateException("Context is closing.");
        if(invalid) throw new IllegalStateException("Context snapshot is invalid.");
    }

    boolean isReady(){
        return canRun() && !pending.contains(this, true);
    }

    static void checkHost(){
        checkThread();
        if(current != null || changing || restoreFailed) throw new IllegalStateException("Configure or dispose outside world scopes with consistent host state.");
    }

    static void checkInactive(Context world){
        checkState();
        if(world == null) throw new IllegalArgumentException("A world is required.");
        if(world.depth != 0) throw new IllegalStateException("Cannot reconfigure an active or suspended context.");
    }

    static void checkThread(){
        if(Thread.currentThread() != thread) throw new IllegalStateException("Use contexts on their bound thread.");
    }

    static void checkState(){
        checkThread();
        if(changing || restoreFailed || current != null && current.invalid) throw new IllegalStateException("World state is not consistently installed.");
        Binding.validate(current);
    }

    static Throwable cleanup(@Nullable Throwable failure, Runnable task){
        try{
            task.run();
        }catch(Throwable next){
            if(failure == null) return next;
            if(failure != next) failure.addSuppressed(next);
        }
        return failure;
    }

    private void disposeContext(){
        if(released) return;
        checkHost();
        if(closing) throw new IllegalStateException("Context is closing.");
        closing = true;
        disposing++;
        int part = disposalPart;
        Throwable failure = null;
        try{
            if(invalid) throw new IllegalStateException("Cannot run installed cleanup for an invalid context.");
            while(disposalPart < owned.size + 5){
                part = disposalPart;
                disposeNext();
            }
        }catch(Throwable error){
            failure = error;
            while(disposed && !invalid && !restoreFailed && disposalPart > part && disposalPart < owned.size + 5){
                part = disposalPart;
                cleanup(error, this::disposeNext);
            }
            if(invalid && !restoreFailed){
                if(workers != null) cleanup(error, workers::stopFlow);
                cleanup(error, () -> DeferredHooks.complete(this));
                disposed = true;
                releaseHostTail(error);
                disposalPart = owned.size + 5;
            }
            throw error;
        }finally{
            closing = false;
            disposing--;
            if(disposalPart == owned.size + 5){
                released = true;
                pending.remove(this, true);
                live.remove(this, true);
                Campaigns.onDispose(this);
                owned.clear();
                workers = null;
                for(var entry : bindings) entry.key.detach(this, entry.value);
                bindings.clear();
                frame.release();
                rollback = null;
            }else if(!pending.contains(this, true)){
                pending.add(this);
            }
            if(current == null && !changing && !restoreFailed){
                try{
                    drainPending(this);
                }catch(Throwable next){
                    if(failure == null) throw next;
                    if(failure != next) failure.addSuppressed(next);
                }
            }
        }
    }

    private void releaseHostTail(Throwable failure){
        for(int part = Math.max(1, disposalPart); part <= owned.size; part++){
            Cleanup entry = owned.get(owned.size - part);
            if(!entry.installed) cleanup(failure, entry.resource::dispose);
        }
    }

    private void disposeNext(){
        checkHost();
        disposePart(disposalPart);
    }

    private static void drainPending(){
        drainPending(null);
    }

    private static void drainPending(Context excluded){
        if(draining || disposing != 0 || pending.isEmpty()) return;
        draining = true;
        var batch = pending.copy();
        int index = 0;
        try{
            for(; index < batch.size; index++){
                Context context = batch.get(index);
                if(context != excluded) context.finishClose(batch);
            }
        }catch(Throwable failure){
            for(index++; index < batch.size; index++){
                Context context = batch.get(index);
                if(context != excluded) cleanup(failure, () -> context.finishClose(batch));
            }
            throw failure;
        }finally{
            draining = false;
        }
    }

    private void finishClose(Seq<Context> batch){
        if(automaticRejected || !invalid && Binding.invalid(this) != null) return;
        try{
            if(rollback == null){
                disposeContext();
            }else{
                rollback.run();
            }
        }catch(Throwable failure){
            if(!released) automaticRejected = true;
            throw failure;
        }finally{
            for(Context context : pending) if(!batch.contains(context, true)) batch.add(context);
        }
    }

    private void disposePart(int part){
        if(part == 0){
            runCleanup(() -> {
                disposed = true;
                try{
                    try{
                        if(workers != null) workers.dispose();
                    }catch(Throwable failure){
                        cleanup(failure, Workers::drainAsync);
                        throw failure;
                    }
                    Workers.drainAsync();
                }finally{
                    stopped = true;
                }
            });
        }else if(!stopped || restoreFailed){
            throw new IllegalStateException("Cleanup has no safe teardown scope.");
        }else if(part <= owned.size){
            Cleanup entry = owned.get(owned.size - part);
            if(entry.installed){
                runCleanup(entry.resource::dispose);
            }else{
                checkHost();
                attemptCleanup(entry.resource::dispose);
            }
        }else{
            switch(part - owned.size){
                case 1 -> runCleanup(() -> { if(Groups.all != null) Groups.clear(); });
                case 2 -> runCleanup(Groups::updatePooling);
                case 3 -> runCleanup(Time::clear);
                case 4 -> runCleanup(Events::clear);
            }
        }
    }

    private void runCleanup(Runnable task){
        if(invalid) throw new IllegalStateException("Context snapshot is invalid.");
        enter(() -> {
            attemptCleanup(task);
            return null;
        });
    }

    private void attemptCleanup(Runnable task){
        try{
            task.run();
        }finally{
            disposalPart++;
        }
    }

    private void updateFrame(float delta){
        if(Vars.net != null && Vars.net.client()) throw new IllegalStateException("Worlds cannot update while connected to a server.");
        if(updateLogic == null && Vars.state.isCampaign()){
            Campaigns.beforeStep(this);
        }
        var graphics = Core.graphics;
        if(graphicsType != graphics.getClass()){
            graphicsDelta = deltaField(graphics.getClass());
            graphicsType = graphics.getClass();
        }
        Field timing = graphicsDelta;
        float previous = Time.delta, previousGraphics = graphics.getDeltaTime();
        Throwable failure = null;
        updating = true;
        Time.delta = delta;
        try{
            //Logic and fixed-rate groups read seconds from Graphics, which has no timing setter
            Reflect.set(graphics, timing, delta / 60f);
            if(Vars.asyncCore != null) Vars.asyncCore.begin();
            if(updateLogic != null){
                Network.updateWorldLogic(updateLogic);
            }else if(Vars.logic != null){
                Network.updateWorldLogic(Vars.logic::update);
            }
        }catch(Throwable e){
            failure = e;
            throw e;
        }finally{
            try{
                if(Vars.asyncCore != null) Vars.asyncCore.end();
            }catch(Throwable e){
                if(failure == null) throw e;
                failure.addSuppressed(e);
            }finally{
                Time.delta = previous;
                updating = false;
                Reflect.set(graphics, timing, previousGraphics);
            }
        }
    }

    private <T> T enter(Prov<T> task){
        return enter(task, true);
    }

    private <T> T enter(Prov<T> task, boolean wait){
        checkThread();
        if(invalid) throw new IllegalStateException("Context snapshot is invalid.");
        if(restoreFailed) throw new IllegalStateException("A failed restoration left global state inconsistent.");
        if(changing) throw new IllegalStateException("Cannot enter a context during state exchange.");
        if(task == null) throw new IllegalArgumentException("Task is required.");
        Binding.validate(this);
        boolean outer = current == null;
        boolean suspended = false, entered = false;
        Throwable failure = null;
        try{
            if(outer){
                DeferredHooks.suspendHost();
                suspended = true;
            }
            if(!wait && current != this && !Workers.tryCompleteAsync()) return null;
            Network.install();
            depth++;
            entered = true;
            try{
                return exchange(task, wait);
            }finally{
                depth--;
            }
        }catch(Throwable error){
            failure = error;
            throw error;
        }finally{
            Throwable finish = failure;
            if(suspended) finish = cleanup(finish, () -> DeferredHooks.resumeHost(!restoreFailed));
            if(outer && entered && !changing) finish = cleanup(finish, Context::drainPending);
            if(failure == null && finish != null){
                if(finish instanceof RuntimeException error) throw error;
                if(finish instanceof Error error) throw error;
                throw new RuntimeException(finish);
            }
        }
    }

    private <T> T exchange(Prov<T> task, boolean wait){
        if(current == this) return invoke(task);
        Context previous = current;
        WorldFrame saved = previous == null ? host : previous.frame;
        boolean captured = false, installed = false;
        Throwable failure = null;
        try{
            if(wait) Workers.completeAsync();
            changing = true;
            saved.capture(this, false);
            captured = true;
            frame.install();
            current = this;
            installed = true;
            changing = false;
            return invoke(task);
        }catch(Throwable e){
            failure = e;
            throw e;
        }finally{
            changing = true;
            Throwable work = null;
            try{
                if(installed) work = frame.capture(previous, true);
            }catch(Throwable e){
                invalidate();
                if(failure == null){
                    failure = e;
                    throw e;
                }
                if(failure != e) failure.addSuppressed(e);
            }finally{
                current = previous;
                try{
                    if(captured){
                        if(previous != null && previous.invalid){
                            invalidate();
                            throw new IllegalStateException("Cannot restore an invalid context.");
                        }
                        saved.install();
                        restoreFailed = false;
                    }
                }catch(Throwable e){
                    restoreFailed = true;
                    if(saved.rejectExposed()) invalidate();
                    if(previous != null) previous.invalidate();
                    if(failure == null){
                        failure = e;
                        throw e;
                    }
                    if(failure != e) failure.addSuppressed(e);
                }finally{
                    if(previous == null) host.release();
                    changing = false;
                }
            }
            if(work != null){
                if(failure != null){
                    if(failure != work) failure.addSuppressed(work);
                }else if(work instanceof RuntimeException error){
                    throw error;
                }else if(work instanceof Error error){
                    throw error;
                }else{
                    throw new RuntimeException(work);
                }
            }
        }
    }

    private <T> T invoke(Prov<T> task){
        T result = task.get();
        if(invalid) throw new IllegalStateException("Context became invalid during its task.");
        Binding.validate(this);
        return result;
    }

    private void invalidate(){
        invalid = true;
        for(Binding.Value<?> value : bindings.values()) value.valid = false;
    }

    boolean isInvalid(){
        return invalid;
    }

    void admit(Deferred component){
        checkUsable();
        if(!admitting || admitted != null) throw new IllegalStateException("Deferred components are created only by Context.Config.deferred, once per context.");
        admitted = component;
    }

    private static Field deltaField(Class<?> type){
        for(Class<?> cur = type; cur != null; cur = cur.getSuperclass()){
            try{
                Field field = cur.getDeclaredField("deltaTime");
                field.setAccessible(true);
                return field;
            }catch(NoSuchFieldException ignored){
            }
        }
        throw new ExceptionInInitializerError("Missing Graphics#deltaTime on " + type.getName());
    }

    static boolean onThread(){
        return Thread.currentThread() == thread;
    }

    boolean canRun(){
        checkThread();
        return !disposed && !closing && !failed && !invalid && Binding.invalid(this) == null;
    }

    private record Cleanup(Disposable resource, boolean installed){}
    private record CreateHook(@Nullable Cons<Context> before, @Nullable Cons<Context> after){}
}
