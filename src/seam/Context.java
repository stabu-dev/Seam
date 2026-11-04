package seam;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Time.*;
import arc.util.pooling.Pool.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.gen.*;

import java.lang.reflect.*;

/** Experimental world scope. Failed exit capture invalidates its context; failed restoration blocks entry until a sound enclosing scope is restored. */
final class Context{
    private static final Thread thread = Core.app != null && Core.app.getMainThread() != null ? Core.app.getMainThread() : Thread.currentThread();
    private static final ObjectMap<Object, Seq<Cons<?>>> events = Reflect.get(Events.class, "events");
    private static final Field freeQueue = field(Groups.class, "freeQueue"), removal = field(Time.class, "removal"), executor = field(AsyncCore.class, "executor");
    private static final Frame host = new Frame();
    private static Context current;
    private static boolean changing, restoreFailed;

    private final Frame frame = new Frame();
    private boolean invalid, updating;

    Context(){
        run(() -> {
            Groups.init();
            Vars.world = new World();
            Vars.state = new GameState();
            Vars.asyncCore = new AsyncCore();
            //avoids a thread pool per world
            Reflect.set(Vars.asyncCore, executor, Vars.mainExecutor);
            Vars.spawner = new WaveSpawner();
            Vars.indexer = new BlockIndexer();
            Vars.logic = new Logic();
        });
    }

    static Context current(){
        checkThread();
        return current;
    }

    /** Runs one native logic frame of this world with {@code delta} ticks. */
    void update(float delta){
        if(updating) throw new IllegalStateException("Cannot update a world during its own update.");
        run(() -> {
            if(Vars.net.active()) throw new IllegalStateException("Worlds cannot update while the host network is active.");
            if(Vars.state.rules.fog || Vars.state.isCampaign()) throw new IllegalStateException("Fog and campaign rules are not scoped yet.");
            float previous = Time.delta;
            Throwable failure = null;
            updating = true;
            Time.delta = delta;
            try{
                Vars.asyncCore.begin();
                Vars.logic.update();
            }catch(Throwable e){
                failure = e;
                throw e;
            }finally{
                try{
                    Vars.asyncCore.end();
                }catch(Throwable e){
                    if(failure == null) throw e;
                    failure.addSuppressed(e);
                }finally{
                    Time.delta = previous;
                    updating = false;
                }
            }
        });
    }

    void run(Runnable task){
        if(task == null) throw new IllegalArgumentException("Task is required.");
        get(() -> {
            task.run();
            return null;
        });
    }

    <T> T get(Prov<T> task){
        checkThread();
        if(restoreFailed) throw new IllegalStateException("A failed restoration left global state inconsistent.");
        if(invalid) throw new IllegalStateException("Context snapshot is invalid.");
        if(changing) throw new IllegalStateException("Cannot enter a context during state exchange.");
        if(task == null) throw new IllegalArgumentException("Task is required.");
        if(current == this) return invoke(task);
        Context previous = current;
        Frame saved = previous == null ? host : previous.frame;
        boolean captured = false, installed = false;
        Throwable failure = null;
        try{
            changing = true;
            saved.capture();
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
            try{
                if(installed && !invalid) frame.capture();
            }catch(Throwable e){
                invalid = true;
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
                            invalid = true;
                            throw new IllegalStateException("Cannot restore an invalid context.");
                        }
                        saved.install();
                        restoreFailed = false;
                    }
                }catch(Throwable e){
                    restoreFailed = true;
                    if(previous != null) previous.invalid = true;
                    if(failure == null) throw e;
                    if(failure != e) failure.addSuppressed(e);
                }finally{
                    if(previous == null) host.release();
                    changing = false;
                }
            }
        }
    }

    private <T> T invoke(Prov<T> task){
        T result = task.get();
        if(invalid) throw new IllegalStateException("Context became invalid during its task.");
        return result;
    }

    private static void checkThread(){
        if(Thread.currentThread() != thread) throw new IllegalStateException("Use contexts on their bound thread.");
    }

    private static Field field(Class<?> type, String name){
        try{
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }catch(ReflectiveOperationException e){
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final class Frame{
        World world;
        GameState state;
        EntityGroup<Entityc> all;
        EntityGroup<Building> build;
        EntityGroup<Bullet> bullet;
        EntityGroup<Drawc> draw;
        EntityGroup<EffectState> effect;
        EntityGroup<Player> player;
        EntityGroup<PowerGraphUpdaterc> power;
        EntityGroup<Syncc> sync;
        EntityGroup<Unit> unit;
        EntityGroup<WeatherState> weather;
        boolean clearing;
        Seq<Poolable> pooling = new Seq<>();
        final ObjectMap<Object, Seq<Cons<?>>> listeners = new ObjectMap<>();
        Seq<DelayRun> runs = new Seq<>(), removed = new Seq<>();
        double time;
        Logic logic;
        WaveSpawner spawner;
        BlockIndexer indexer;
        AsyncCore async;
        PhysicsProcess physics;
        AvoidanceProcess avoidance;

        void capture(){
            world = Vars.world;
            state = Vars.state;
            all = Groups.all;
            build = Groups.build;
            bullet = Groups.bullet;
            draw = Groups.draw;
            effect = Groups.effect;
            player = Groups.player;
            power = Groups.powerGraph;
            sync = Groups.sync;
            unit = Groups.unit;
            weather = Groups.weather;
            clearing = Groups.isClearing;
            pooling = Reflect.get(null, freeQueue);
            listeners.set(events);
            runs = Time.getRuns();
            removed = Reflect.get(null, removal);
            time = Time.getInternalTime();
            logic = Vars.logic;
            spawner = Vars.spawner;
            indexer = Vars.indexer;
            async = Vars.asyncCore;
            physics = Vars.unitPhysics;
            avoidance = Vars.avoidance;
        }

        void install(){
            Vars.world = world;
            Vars.state = state;
            Groups.all = all;
            Groups.build = build;
            Groups.bullet = bullet;
            Groups.draw = draw;
            Groups.effect = effect;
            Groups.player = player;
            Groups.powerGraph = power;
            Groups.sync = sync;
            Groups.unit = unit;
            Groups.weather = weather;
            Groups.isClearing = clearing;
            Reflect.set(null, freeQueue, pooling);
            events.set(listeners);
            Time.setRuns(runs);
            Reflect.set(null, removal, removed);
            Time.setInternalTime(time);
            Vars.logic = logic;
            Vars.spawner = spawner;
            Vars.indexer = indexer;
            Vars.asyncCore = async;
            Vars.unitPhysics = physics;
            Vars.avoidance = avoidance;
        }

        void release(){
            world = null;
            state = null;
            all = null;
            build = null;
            bullet = null;
            draw = null;
            effect = null;
            player = null;
            power = null;
            sync = null;
            unit = null;
            weather = null;
            pooling = null;
            listeners.clear();
            runs = null;
            removed = null;
            logic = null;
            spawner = null;
            indexer = null;
            async = null;
            physics = null;
            avoidance = null;
        }
    }
}
