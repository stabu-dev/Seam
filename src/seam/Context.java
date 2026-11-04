package seam;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.Pool.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.gen.*;

import java.lang.reflect.*;

/** Experimental data scope. Failed exit capture invalidates its context; failed restoration blocks entry until a sound enclosing scope is restored. */
final class Context{
    private static final Thread thread = Core.app != null && Core.app.getMainThread() != null ? Core.app.getMainThread() : Thread.currentThread();
    private static final ObjectMap<Object, Seq<Cons<?>>> events = Reflect.get(Events.class, "events");
    private static final Field freeQueue;
    private static final Frame host = new Frame();
    private static Context current;
    private static boolean changing, restoreFailed;

    static{
        try{
            freeQueue = Groups.class.getDeclaredField("freeQueue");
            freeQueue.setAccessible(true);
        }catch(ReflectiveOperationException e){
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Frame frame = new Frame();
    private boolean invalid;

    Context(){
        run(() -> {
            Groups.init();
            Vars.world = new World();
            Vars.state = new GameState();
        });
    }

    static Context current(){
        checkThread();
        return current;
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
        if(current == this) return task.get();
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
            return task.get();
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
                        if(previous != null && previous.invalid) throw new IllegalStateException("Cannot restore an invalid context.");
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

    private static void checkThread(){
        if(Thread.currentThread() != thread) throw new IllegalStateException("Use contexts on their bound thread.");
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
        }
    }
}
