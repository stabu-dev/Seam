package seam.core;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.ai.Pathfinder.*;
import mindustry.audio.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import seam.gen.*;

import java.util.concurrent.*;

final class Workers implements Disposable{
    static ExecutorService executor = Threads.boundedExecutor("Seam Worker", Math.max(1, OS.cores - 1));
    static long controlBudget = Time.millisToNanos(2);

    private static final long flowBudget = Engine.pathfinderMaxUpdate();
    private static final int invalidInterval = Engine.controlPathInvalidateCheckInterval();
    //FogControl starts its threads whenever these fields are null; this one never starts
    private static final Thread idle = new Thread(() -> {}, "Seam idle fog worker");

    private final Bits clearedFog = new Bits();
    private @Nullable Future<?> flow;
    private long invalidCheck;
    private boolean disposed;

    /** Runs the host's ambient sound pass on the main thread so its native thread never races swapped globals. */
    static void installHostAmbient(){
        SoundControl host = Vars.control.sound;
        if(host == null) return;
        prepareHostAmbient(host);
        Events.on(ResetEvent.class, event -> prepareHostAmbient(host));
        long[] last = {0};
        Events.run(Trigger.update, () -> {
            if(Context.current() != null || Vars.control.sound != host || !Vars.state.isPlaying()) return;
            long now = Time.nanos();
            if(now - last[0] < Time.millisToNanos(50)) return;
            last[0] = now;
            Thread ambient = Engine.soundControlAmbientThread(host);
            if(ambient != null) Engine.audioThreadDoLoop(ambient);
        });
    }

    private static void prepareHostAmbient(SoundControl host){
        Thread nativeThread = Engine.soundControlAmbientThread(host);
        Thread replacement = Engine.newAudioThread();
        if(nativeThread != null){
            Engine.audioThreadSetRunning(nativeThread, false);
            joinThread(nativeThread);
            Engine.audioThreadSources(replacement).addAll(Engine.audioThreadSources(nativeThread));
            Engine.audioThreadInputSources(replacement).addAll(Engine.audioThreadInputSources(nativeThread));
        }
        //a queued native start checks identity before starting its old thread
        Engine.soundControlSetAmbientThread(host, replacement);
    }

    @SuppressWarnings("unchecked")
    static ControlPathfinder controlPath(Prov<ControlPathfinder> factory){
        ObjectMap<Object, Seq<Cons<Object>>> registry = (ObjectMap<Object, Seq<Cons<Object>>>)(Object)Engine.eventsEvents();
        ObjectMap<Object, Seq<Cons<Object>>> saved = registry.copy(), nativeEvents;
        registry.clear();
        try{
            Engine.controlPathCheckEvents();
            nativeEvents = registry.copy();
        }finally{
            registry.clear();
            registry.putAll(saved);
        }
        var installed = new Seq<ControlListener>();
        for(var entry : nativeEvents){
            Seq<Cons<Object>> listeners = registry.get(entry.key, () -> new Seq<>(Cons.class));
            if(entry.key == WorldLoadEvent.class){
                listeners.add(e -> {
                    var replacement = new Seq<ControlListener>();
                    ControlPathfinder next = createControlPath(factory, registry, replacement);
                    if(Vars.controlPath != null){
                        stopThread(Engine.controlPathThread(Vars.controlPath), () -> Engine.controlPathSetThread(Vars.controlPath, null));
                        Engine.controlPathStop(Vars.controlPath);
                    }
                    for(ControlListener listener : installed) listener.detach(registry);
                    installed.set(replacement);
                    Vars.controlPath = next;
                });
            }else{
                for(Cons<Object> listener : entry.value) listeners.add(e -> {
                    if(Vars.controlPath != null) listener.get(e);
                });
            }
        }
        return createControlPath(factory, registry, installed);
    }

    private static ControlPathfinder createControlPath(Prov<ControlPathfinder> factory, ObjectMap<Object, Seq<Cons<Object>>> registry, Seq<ControlListener> owned){
        var sizes = new ObjectIntMap<Object>();
        //Arc's ObjectMap caches iterator entries without clearing them on clear(); iterate copies so Events.events never pins child listeners
        for(var entry : registry.copy()) sizes.put(entry.key, entry.value.size);
        try{
            ControlPathfinder next = factory.get();
            for(var entry : registry.copy()){
                int start = sizes.get(entry.key, 0);
                for(int i = start; i < entry.value.size; i++) owned.add(new ControlListener(entry.key, entry.value.get(i)));
            }
            return next;
        }catch(Throwable failure){
            for(var entry : registry.copy()){
                int size = sizes.get(entry.key, 0);
                if(size >= entry.value.size) continue;
                var listeners = entry.value.copy();
                listeners.truncate(size);
                registry.put(entry.key, listeners);
            }
            throw failure;
        }
    }

    private record ControlListener(Object type, Cons<Object> callback){
        void detach(ObjectMap<Object, Seq<Cons<Object>>> registry){
            var listeners = registry.get(type);
            if(listeners == null) return;
            //leave the native snapshot of an in-flight dispatch alone
            var copy = listeners.copy();
            copy.remove(callback, true);
            registry.put(type, copy);
        }
    }

    Workers(){
        for(Class<?> type : new Class<?>[]{WorldLoadBeginEvent.class, WorldLoadEvent.class, ResetEvent.class}){
            Events.on(type, e -> completeWork());
            //native lifecycle handlers discard pending work; prepend before them using a copied array to preserve active dispatch
            var registry = Engine.eventsEvents();
            Seq<Cons<?>> listeners = registry.get(type).copy();
            listeners.insert(0, listeners.pop());
            registry.put(type, listeners);
        }
        Events.on(ResetEvent.class, e -> replaceThreads());
        Events.on(WorldLoadEvent.class, e -> replaceThreads());
        Events.run(Trigger.update, this::navigate);
        Events.run(Trigger.afterGameUpdate, this::drawFog);
        replaceThreads();
    }

    private void completeWork(){
        Throwable failure = Context.cleanup(null, this::completeFlow);
        failure = Context.cleanup(failure, Workers::completeAsync);
        if(failure instanceof RuntimeException error) throw error;
        if(failure instanceof Error error) throw error;
        if(failure != null) throw new RuntimeException(failure);
    }

    private void replaceThreads(){
        if(disposed) return;
        invalidCheck = Time.millis() + invalidInterval;
        if(Vars.asyncCore != null){
            ExecutorService exec = Engine.asyncCoreExecutor(Vars.asyncCore);
            Engine.asyncCoreSetExecutor(Vars.asyncCore, DeferredHooks.executor(exec == null ? Vars.mainExecutor : exec));
        }
        if(Vars.pathfinder != null) stopThread(Engine.pathfinderThread(Vars.pathfinder), () -> Engine.pathfinderSetThread(Vars.pathfinder, null));
        if(Vars.controlPath != null) stopThread(Engine.controlPathThread(Vars.controlPath), () -> Engine.controlPathSetThread(Vars.controlPath, null));
        if(Vars.fogControl != null){
            stopThread(Engine.fogStaticFogThread(Vars.fogControl), () -> Engine.fogSetStaticFogThread(Vars.fogControl, idle));
            stopThread(Engine.fogDynamicFogThread(Vars.fogControl), () -> Engine.fogSetDynamicFogThread(Vars.fogControl, idle));
        }
    }

    static void completeAsync(){
        completeAsync(false, true);
    }

    static void drainAsync(){
        completeAsync(true, true);
    }

    static boolean tryCompleteAsync(){
        if(!DeferredHooks.idle(Context.current())) return false;
        if(Vars.asyncCore != null && !ready(Engine.asyncCoreFutures(Vars.asyncCore))) return false;
        if(Vars.unitPhysics != null && !ready(Engine.physicsFutures(Vars.unitPhysics))) return false;
        completeAsync(false, false);
        return true;
    }

    private static boolean ready(Seq<Future<?>> futures){
        for(Future<?> future : futures) if(!future.isDone()) return false;
        return true;
    }

    private static void completeAsync(boolean drain, boolean tracked){
        Throwable failure = null;
        if(Vars.asyncCore != null) failure = Context.cleanup(failure, () -> complete(Engine.asyncCoreFutures(Vars.asyncCore), drain));
        //PhysicsProcess submits layer jobs directly to mainExecutor outside AsyncCore's futures
        if(Vars.unitPhysics != null) failure = Context.cleanup(failure, () -> complete(Engine.physicsFutures(Vars.unitPhysics), drain));
        if(tracked) failure = Context.cleanup(failure, () -> DeferredHooks.complete(Context.current()));
        if(failure instanceof RuntimeException error) throw error;
        if(failure instanceof Error error) throw error;
        if(failure != null) throw new RuntimeException(failure);
    }

    private static void complete(Seq<Future<?>> futures, boolean drain){
        RuntimeException failure = null;
        boolean interrupted = false;
        //wait for every process before globals can change
        for(Future<?> future : futures){
            while(true){
                try{
                    future.get();
                    break;
                }catch(InterruptedException error){
                    interrupted = true;
                }catch(Throwable error){
                    if(failure == null){
                        failure = new RuntimeException(error);
                    }else{
                        failure.addSuppressed(error);
                    }
                    break;
                }
            }
        }
        if(interrupted) Thread.currentThread().interrupt();
        if(failure == null || drain) futures.clear();
        if(failure != null) throw failure;
    }

    private static void stopThread(@Nullable Thread thread, Runnable clear){
        clear.run();
        if(thread != null && thread != idle) joinThread(thread);
    }

    private static void joinThread(Thread thread){
        thread.interrupt();
        boolean interrupted = false;
        while(thread.isAlive()){
            try{
                thread.join();
            }catch(InterruptedException e){
                interrupted = true;
            }
        }
        if(interrupted) Thread.currentThread().interrupt();
    }

    private void navigate(){
        if(disposed || !Vars.state.isPlaying() || Vars.net != null && Vars.net.client()) return;
        Pathfinder pathfinder = Vars.pathfinder;
        if(flow == null || flow.isDone()){
            completeFlow();
            if(pathfinder != null && flowPending(pathfinder)) flow = executor.submit(Deferred.backgroundCurrent(() -> iterateFlow(pathfinder)));
        }
        ControlPathfinder control = Vars.controlPath;
        if(control != null && !Engine.controlPathInvalidated(control)) iterateControl(control);
    }

    private void completeFlow(){
        if(flow == null) return;
        boolean interrupted = false;
        try{
            while(true){
                try{
                    flow.get();
                    return;
                }catch(InterruptedException e){
                    interrupted = true;
                }catch(ExecutionException e){
                    throw new IllegalStateException("Flow field iteration failed.", e.getCause());
                }
            }
        }finally{
            flow = null;
            if(interrupted) Thread.currentThread().interrupt();
        }
    }

    void stopFlow(){
        disposed = true;
        completeFlow();
    }

    @Override
    public void dispose(){
        disposed = true;
        Throwable failure = Context.cleanup(null, this::completeFlow);
        if(Vars.pathfinder != null) failure = Context.cleanup(failure, () -> stopThread(Engine.pathfinderThread(Vars.pathfinder), () -> Engine.pathfinderSetThread(Vars.pathfinder, null)));
        if(Vars.controlPath != null) failure = Context.cleanup(failure, () -> stopThread(Engine.controlPathThread(Vars.controlPath), () -> Engine.controlPathSetThread(Vars.controlPath, null)));
        if(Vars.fogControl != null){
            failure = Context.cleanup(failure, () -> stopThread(Engine.fogStaticFogThread(Vars.fogControl), () -> Engine.fogSetStaticFogThread(Vars.fogControl, null)));
            failure = Context.cleanup(failure, () -> stopThread(Engine.fogDynamicFogThread(Vars.fogControl), () -> Engine.fogSetDynamicFogThread(Vars.fogControl, null)));
        }
        if(failure instanceof RuntimeException error) throw error;
        if(failure instanceof Error error) throw error;
        if(failure != null) throw new RuntimeException(failure);
    }

    private static boolean flowPending(Pathfinder pathfinder){
        if(Engine.pathfinderQueue(pathfinder).size() > 0) return true;
        Seq<Flowfield> fields = Engine.pathfinderThreadList(pathfinder);
        for(int i = 0; i < fields.size; i++){
            Flowfield field = fields.get(i);
            if(Engine.flowfieldDirty(field) || Engine.flowfieldFrontier(field).size > 0) return true;
        }
        return false;
    }

    private static void iterateFlow(Pathfinder pathfinder){
        Engine.pathfinderQueue(pathfinder).run();
        Seq<Flowfield> fields = Engine.pathfinderThreadList(pathfinder);
        for(int i = 0; i < fields.size; i++){
            Flowfield field = fields.get(i);
            if(Engine.flowfieldDirty(field) && Engine.flowfieldFrontier(field).size == 0){
                Engine.pathfinderUpdateTargets(pathfinder, field);
                Engine.flowfieldSetDirty(field, false);
            }
            Engine.pathfinderUpdateFrontier(pathfinder, field, flowBudget);
        }
    }

    private void iterateControl(ControlPathfinder control){
        Engine.controlPathQueue(control).run();
        IntSet complete = Engine.controlPathClustersToUpdate(control), inner = Engine.controlPathClustersToInnerUpdate(control);
        complete.each(cluster -> {
            Engine.controlPathUpdateClustersComplete(control, cluster);
            inner.remove(cluster);
        });
        inner.each(cluster -> Engine.controlPathUpdateClustersInner(control, cluster));
        inner.clear();
        complete.clear();

        if(Time.timeSinceMillis(invalidCheck) > invalidInterval){
            invalidCheck = Time.millis();
            recalculateInvalid(control);
        }

        LongMap<Object> fields = Engine.controlPathFields(control);
        long budget = controlBudget / Math.max(fields.size, 1);
        fields.eachValue(cache -> {
            if(cache != null) Engine.controlPathUpdateFields(control, cache, budget);
        });
    }

    private static void recalculateInvalid(ControlPathfinder control){
        TaskQueue queue = Engine.controlPathQueue(control);
        LongMap<Object> fields = Engine.controlPathFields(control);
        Seq<Object> active = Engine.controlPathThreadPathRequests(control);
        ObjectSet<Object> invalid = Engine.controlPathInvalidRequests(control);
        ObjectSet<Object> recalculating = Engine.controlPathRequestsToRecalculate(control);
        var it = invalid.iterator();
        while(it.hasNext()){
            Object request = it.next();
            if(Engine.pathRequestInvalidated(request)){
                it.remove();
                continue;
            }
            int target = Engine.pathRequestDestination(request);
            Object cache = fields.get(FieldIndex.get(target, Engine.pathRequestCostId(request), Engine.pathRequestTeam(request)));
            if(cache != null){
                if(!Engine.fieldCacheFrontier(cache).isEmpty()) continue;
                fields.remove(Engine.fieldCacheMapKey(cache));
                Engine.controlPathFieldList(control).remove(cache);
                for(int i = 0; i < active.size; i++){
                    Object other = active.get(i);
                    if(Engine.pathRequestDestination(other) == target){
                        Engine.pathRequestSetOldCache(other, cache);
                        recalculating.add(other);
                    }
                }
            }
            recalculating.add(request);
            it.remove();
        }
        for(Object request : recalculating) queue.post(() -> Engine.controlPathRecalculatePath(control, request));
        recalculating.clear();
    }

    private void drawFog(){
        if(disposed || Vars.fogControl == null || !Vars.state.rules.fog) return;
        FogControl fog = Vars.fogControl;
        if(Vars.state.rules.staticFog && Engine.fogStaticEvents(fog).size > 0) Engine.fogUpdateStatic(fog);
        if(Engine.fogDynamicEvents(fog).size > 0) Engine.fogUpdateDynamic(fog, clearedFog);
    }
}
