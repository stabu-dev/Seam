package seam.runtime.control;

import seam.core.*;
import seam.runtime.*;
import arc.*;
import arc.graphics.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;

import java.lang.reflect.*;

public final class SeamRuntimeStack{
    private final Seq<SeamRuntime> stack = new Seq<>();
    private final Seq<SeamPhase> phases = new Seq<>();
    private final Seq<ContextSnapshot> snapshots = new Seq<>();

    public boolean active(){
        return !stack.isEmpty();
    }

    public int depth(){
        return stack.size;
    }

    public SeamRuntime current(){
        return stack.isEmpty() ? null : stack.peek();
    }

    public SeamPhase currentPhase(){
        return phases.isEmpty() ? null : phases.peek();
    }

    public void enter(SeamRuntime runtime){
        enter(runtime, SeamPhase.manual);
    }

    public void enter(SeamRuntime runtime, SeamPhase phase){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(phase == null){
            throw new NullPointerException("phase");
        }

        runtime.requireWorldReady();

        ContextSnapshot snapshot = new ContextSnapshot(runtime, phase);

        snapshots.add(snapshot);
        stack.add(runtime);
        phases.add(phase);

        Vars.world = runtime.world;
        Vars.state = runtime.state;
        Vars.collisions = runtime.collisions;
        Vars.waves = runtime.waves;
        Vars.spawner = runtime.spawner;
        Vars.indexer = runtime.indexer;
        Vars.pathfinder = runtime.pathfinder;
        Vars.controlPath = runtime.controlPath;

        Groups.all = runtime.groups.all;
        Groups.player = runtime.groups.player;
        Groups.bullet = runtime.groups.bullet;
        Groups.unit = runtime.groups.unit;
        Groups.build = runtime.groups.build;
        Groups.sync = runtime.groups.sync;
        Groups.draw = runtime.groups.draw;
        Groups.fire = runtime.groups.fire;
        Groups.puddle = runtime.groups.puddle;
        Groups.weather = runtime.groups.weather;
        Groups.label = runtime.groups.label;
        Groups.powerGraph = runtime.groups.powerGraph;

        if(usesRuntimeCamera(phase)){
            applyRuntimeCamera(runtime);
        }
    }

    public void exit(){
        if(stack.isEmpty()){
            return;
        }

        SeamRuntime runtime = stack.peek();
        ContextSnapshot snapshot = snapshots.peek();

        snapshot.wrapDelayedRuns(this, runtime);

        stack.pop();
        phases.pop();
        snapshots.pop();

        snapshot.restore();
    }

    public void exitAll(){
        while(active()){
            exit();
        }
    }

    private static boolean usesRuntimeCamera(SeamPhase phase){
        return phase == SeamPhase.updateGroups
        || phase == SeamPhase.updateDelayed;
    }

    private static void applyRuntimeCamera(SeamRuntime runtime){
        Camera camera = Core.camera;

        if(camera == null){
            return;
        }

        float width = Math.max(runtime.world.unitWidth(), Vars.tilesize);
        float height = Math.max(runtime.world.unitHeight(), Vars.tilesize);

        camera.position.x = width / 2f;
        camera.position.y = height / 2f;
        camera.width = width;
        camera.height = height;
        camera.update();
    }

    private static final class ContextSnapshot{
        private final World world;
        private final GameState state;
        private final EntityCollisions collisions;
        private final Waves waves;
        private final WaveSpawner spawner;
        private final BlockIndexer indexer;
        private final Pathfinder pathfinder;
        private final ControlPathfinder controlPath;

        private final EntityGroup<Entityc> all;
        private final EntityGroup<Player> player;
        private final EntityGroup<Bullet> bullet;
        private final EntityGroup<Unit> unit;
        private final EntityGroup<Building> build;
        private final EntityGroup<Syncc> sync;
        private final EntityGroup<Drawc> draw;
        private final EntityGroup<Fire> fire;
        private final EntityGroup<Puddle> puddle;
        private final EntityGroup<WeatherState> weather;
        private final EntityGroup<WorldLabel> label;
        private final EntityGroup<PowerGraphUpdaterc> powerGraph;

        private final boolean hasCamera;
        private final float cameraX;
        private final float cameraY;
        private final float cameraWidth;
        private final float cameraHeight;

        private final boolean captureDelayedRuns;
        private final int delayedRunStart;

        ContextSnapshot(SeamRuntime runtime, SeamPhase phase){
            this.world = Vars.world;
            this.state = Vars.state;
            this.collisions = Vars.collisions;
            this.waves = Vars.waves;
            this.spawner = Vars.spawner;
            this.indexer = Vars.indexer;
            this.pathfinder = Vars.pathfinder;
            this.controlPath = Vars.controlPath;

            this.all = Groups.all;
            this.player = Groups.player;
            this.bullet = Groups.bullet;
            this.unit = Groups.unit;
            this.build = Groups.build;
            this.sync = Groups.sync;
            this.draw = Groups.draw;
            this.fire = Groups.fire;
            this.puddle = Groups.puddle;
            this.weather = Groups.weather;
            this.label = Groups.label;
            this.powerGraph = Groups.powerGraph;

            Camera camera = Core.camera;

            this.hasCamera = camera != null;

            if(camera == null){
                this.cameraX = 0f;
                this.cameraY = 0f;
                this.cameraWidth = 0f;
                this.cameraHeight = 0f;
            }else{
                this.cameraX = camera.position.x;
                this.cameraY = camera.position.y;
                this.cameraWidth = camera.width;
                this.cameraHeight = camera.height;
            }

            this.captureDelayedRuns = runtime != null && !runtime.main();
            this.delayedRunStart = captureDelayedRuns ? DelayedRunBridge.size() : -1;
        }

        void wrapDelayedRuns(SeamRuntimeStack owner, SeamRuntime runtime){
            if(!captureDelayedRuns || delayedRunStart < 0 || runtime == null || runtime.main()){
                return;
            }

            DelayedRunBridge.wrapNew(owner, runtime, delayedRunStart);
        }

        void restore(){
            Vars.world = world;
            Vars.state = state;
            Vars.collisions = collisions;
            Vars.waves = waves;
            Vars.spawner = spawner;
            Vars.indexer = indexer;
            Vars.pathfinder = pathfinder;
            Vars.controlPath = controlPath;

            Groups.all = all;
            Groups.player = player;
            Groups.bullet = bullet;
            Groups.unit = unit;
            Groups.build = build;
            Groups.sync = sync;
            Groups.draw = draw;
            Groups.fire = fire;
            Groups.puddle = puddle;
            Groups.weather = weather;
            Groups.label = label;
            Groups.powerGraph = powerGraph;

            if(hasCamera && Core.camera != null){
                Core.camera.position.x = cameraX;
                Core.camera.position.y = cameraY;
                Core.camera.width = cameraWidth;
                Core.camera.height = cameraHeight;
                Core.camera.update();
            }
        }
    }

    private static final class RuntimeDelayedRunnable implements Runnable{
        private final SeamRuntimeStack owner;
        private final SeamRuntime runtime;
        private final Runnable delegate;

        RuntimeDelayedRunnable(SeamRuntimeStack owner, SeamRuntime runtime, Runnable delegate){
            if(owner == null){
                throw new NullPointerException("owner");
            }

            if(runtime == null){
                throw new NullPointerException("runtime");
            }

            if(delegate == null){
                throw new NullPointerException("delegate");
            }

            this.owner = owner;
            this.runtime = runtime;
            this.delegate = delegate;
        }

        @Override
        public void run(){
            if(runtime.disposed() || !runtime.loaded() || !runtime.worldReady()){
                return;
            }

            if(owner.active()){
                if(owner.current() != runtime){
                    throw new IllegalStateException(
                    "Cannot execute delayed Seam runtime task for runtime " + runtime.id +
                    " while runtime " + owner.current().id + " is active."
                    );
                }

                delegate.run();
                return;
            }

            owner.enter(runtime, SeamPhase.updateDelayed);

            try{
                delegate.run();
            }finally{
                owner.exit();
            }
        }
    }

    private static final class DelayedRunBridge{
        private static Field runsField;
        private static Field finishField;

        private static boolean reflectionReady;
        private static boolean reflectionFailed;
        private static boolean loggedFailure;

        static int size(){
            Seq<?> runs = runs();

            return runs == null ? -1 : runs.size;
        }

        static void wrapNew(SeamRuntimeStack owner, SeamRuntime runtime, int start){
            Seq<?> runs = runs();

            if(runs == null || start < 0){
                return;
            }

            int from = Math.min(start, runs.size);

            for(int i = from; i < runs.size; i++){
                Object delayed = runs.items[i];

                if(delayed == null){
                    continue;
                }

                try{
                    Object existing = finishField.get(delayed);

                    if(existing == null || existing instanceof RuntimeDelayedRunnable){
                        continue;
                    }

                    if(!(existing instanceof Runnable runnable)){
                        continue;
                    }

                    finishField.set(delayed, new RuntimeDelayedRunnable(owner, runtime, runnable));
                }catch(Throwable throwable){
                    logFailure(throwable);
                    return;
                }
            }
        }

        private static Seq<?> runs(){
            if(!ensureReflection()){
                return null;
            }

            try{
                return (Seq<?>)runsField.get(null);
            }catch(Throwable throwable){
                logFailure(throwable);
                return null;
            }
        }

        private static boolean ensureReflection(){
            if(reflectionReady){
                return true;
            }

            if(reflectionFailed){
                return false;
            }

            try{
                runsField = Time.class.getDeclaredField("runs");
                runsField.setAccessible(true);

                Class<?> delayRunType = Class.forName("arc.util.Time$DelayRun");
                finishField = delayRunType.getDeclaredField("finish");
                finishField.setAccessible(true);

                reflectionReady = true;
                return true;
            }catch(Throwable throwable){
                reflectionFailed = true;
                logFailure(throwable);
                return false;
            }
        }

        private static void logFailure(Throwable throwable){
            if(loggedFailure){
                return;
            }

            loggedFailure = true;

            Log.err("[Seam] Failed to bind Arc Time.run delayed task to Seam runtime context. Delayed vanilla tasks may leak into the main runtime.");
            Log.err(throwable);
        }
    }
}