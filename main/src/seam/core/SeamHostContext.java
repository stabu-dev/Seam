package seam.core;

import arc.*;
import arc.graphics.*;
import arc.struct.*;
import mindustry.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import seam.runtime.control.*;

public final class SeamHostContext{
    private Snapshot captured;

    public void captureCurrent(){
        captured = new Snapshot();
    }

    public boolean captured(){
        return captured != null;
    }

    public void restore(){
        if(captured == null){
            throw new IllegalStateException("Host context was not captured.");
        }

        captured.restore();
    }

    public static final class Snapshot{
        private final World world;
        private final GameState state;
        private final EntityCollisions collisions;
        private final Waves waves;
        private final Player player;

        private final EntityGroup all;
        private final EntityGroup<Player> playerGroup;
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

        private final AvoidanceProcess avoidance;
        private final Seq<AsyncProcess> asyncProcesses = new Seq<>();

        private final boolean hasCamera;
        private final float cameraX;
        private final float cameraY;
        private final float cameraWidth;
        private final float cameraHeight;

        public Snapshot(){
            world = Vars.world;
            state = Vars.state;
            collisions = Vars.collisions;
            waves = Vars.waves;
            player = Vars.player;

            all = Groups.all;
            playerGroup = Groups.player;
            bullet = Groups.bullet;
            unit = Groups.unit;
            build = Groups.build;
            sync = Groups.sync;
            draw = Groups.draw;
            fire = Groups.fire;
            puddle = Groups.puddle;
            weather = Groups.weather;
            label = Groups.label;
            powerGraph = Groups.powerGraph;

            avoidance = Vars.avoidance;

            if(Vars.asyncCore != null){
                asyncProcesses.addAll(Vars.asyncCore.processes);
            }

            Camera camera = Core.camera;
            hasCamera = camera != null;

            if(hasCamera){
                cameraX = camera.position.x;
                cameraY = camera.position.y;
                cameraWidth = camera.width;
                cameraHeight = camera.height;
            }else{
                cameraX = 0f;
                cameraY = 0f;
                cameraWidth = 0f;
                cameraHeight = 0f;
            }
        }

        public void restore(){
            Vars.world = world;
            Vars.state = state;
            Vars.collisions = collisions;
            Vars.waves = waves;
            Vars.player = player;

            Groups.all = all;
            Groups.player = playerGroup;
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

            SeamContextSlots.restoreAsync(avoidance, asyncProcesses);

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