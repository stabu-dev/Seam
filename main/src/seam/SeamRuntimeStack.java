package seam;

import arc.*;
import arc.graphics.*;
import arc.struct.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.gen.*;

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

        ContextSnapshot snapshot = new ContextSnapshot();

        snapshots.add(snapshot);
        stack.add(runtime);
        phases.add(phase);

        Vars.world = runtime.world;
        Vars.state = runtime.state;
        Vars.collisions = runtime.collisions;

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

        stack.pop();
        phases.pop();

        ContextSnapshot snapshot = snapshots.pop();
        snapshot.restore();
    }

    public void exitAll(){
        while(active()){
            exit();
        }
    }

    private static boolean usesRuntimeCamera(SeamPhase phase){
        return phase == SeamPhase.updateGroups;
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

        ContextSnapshot(){
            this.world = Vars.world;
            this.state = Vars.state;
            this.collisions = Vars.collisions;

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
        }

        void restore(){
            Vars.world = world;
            Vars.state = state;
            Vars.collisions = collisions;

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
}