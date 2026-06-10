package seam;

import arc.struct.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.gen.*;

public final class SeamRuntimeStack{
    private final Seq<SeamRuntime> stack = new Seq<>();
    private final Seq<ContextSnapshot> snapshots = new Seq<>();

    public SeamRuntime current(){
        return stack.isEmpty() ? null : stack.peek();
    }

    public void enter(SeamRuntime runtime){
        snapshots.add(new ContextSnapshot());
        stack.add(runtime);

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
    }

    public void exit(){
        if(stack.isEmpty()) return;

        stack.pop();
        ContextSnapshot snapshot = snapshots.pop();
        snapshot.restore();
    }

    private static class ContextSnapshot{
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

        public ContextSnapshot(){
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
        }

        public void restore(){
            Vars.world = this.world;
            Vars.state = this.state;
            Vars.collisions = this.collisions;

            Groups.all = this.all;
            Groups.player = this.player;
            Groups.bullet = this.bullet;
            Groups.unit = this.unit;
            Groups.build = this.build;
            Groups.sync = this.sync;
            Groups.draw = this.draw;
            Groups.fire = this.fire;
            Groups.puddle = this.puddle;
            Groups.weather = this.weather;
            Groups.label = this.label;
            Groups.powerGraph = this.powerGraph;
        }
    }
}
