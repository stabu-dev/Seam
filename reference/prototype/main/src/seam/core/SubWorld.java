package seam.core;

import java.util.concurrent.*;
import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import seam.*;

/** An isolated Mindustry world that can be updated and rendered independently of the host. */
public class SubWorld{
    public final World world = new World();
    public final GameState state = new GameState();
    public final Camera camera = new Camera();
    public final SubWorldContext context = new SubWorldContext(this);

    public final EntityGroup<Entityc> all = new EntityGroup<>(Entityc.class, false, false);
    public final EntityGroup<Player> players = new EntityGroup<>(Player.class, false, true);
    public final EntityGroup<Bullet> bullet = new EntityGroup<>(Bullet.class, true, false);
    public final EntityGroup<Unit> unit = new EntityGroup<>(Unit.class, true, true);
    public final EntityGroup<Building> build = new EntityGroup<>(Building.class, false, false);
    public final EntityGroup<Syncc> sync = new EntityGroup<>(Syncc.class, false, true);
    public final EntityGroup<Drawc> draw = new EntityGroup<>(Drawc.class, false, false);
    public final EntityGroup<Fire> fire = new EntityGroup<>(Fire.class, false, false);
    public final EntityGroup<Puddle> puddle = new EntityGroup<>(Puddle.class, false, false);
    public final EntityGroup<WeatherState> weather = new EntityGroup<>(WeatherState.class, false, false);
    public final EntityGroup<WorldLabel> label = new EntityGroup<>(WorldLabel.class, false, true);
    public final EntityGroup<PowerGraphUpdaterc> powerGraph = new EntityGroup<>(PowerGraphUpdaterc.class, false, false);

    public Universe universe;
    public AsyncCore asyncCore;
    public AvoidanceProcess avoidance;
    public WaveSpawner spawner;
    public BlockIndexer indexer;
    public Pathfinder pathfinder;
    public ControlPathfinder controlPath;
    public FogControl fogControl;
    public Logic logic;
    public @Nullable Renderer renderer;

    private boolean disposed;

    public SubWorld(){
        // Capture host renderer before context swaps Vars.renderer to null.
        Renderer hostRenderer = Vars.renderer;

        // Systems register Events listeners; must happen inside an isolated event map.
        context.run(() -> {
            universe = new Universe();
            asyncCore = new AsyncCore();
            // AsyncCore constructor assigns Vars.avoidance as a side effect.
            avoidance = Vars.avoidance;
            spawner = new WaveSpawner();
            indexer = new BlockIndexer();
            pathfinder = new Pathfinder();
            controlPath = new ControlPathfinder();
            fogControl = new FogControl();
            logic = new Logic();

            // GlobalVars keeps static LVar fields — do not create/init a second instance.
            // Vars.logicVars stays on the host (see SubWorldContext).

            if(!Vars.headless){
                renderer = FakeRendererFactory.create(hostRenderer);
            }
        });
    }

    /** Resize tile grid and spatial entity trees. Fires {@link WorldLoadEvent} inside this world. */
    public void resize(int width, int height){
        context.run(() -> {
            world.resize(width, height);
            resizeTrees(width, height);
            if(renderer != null) Events.fire(new WorldLoadEvent());
        });
    }

    /** Load tiles via generator, then signal world load to local systems. */
    public void load(int width, int height, Cons<Tiles> generator){
        context.run(() -> {
            world.loadGenerator(width, height, generator);
            resizeTrees(width, height);
            Events.fire(new WorldLoadEvent());
        });
    }

    public void update(){
        if(disposed || !state.isPlaying() || state.isPaused() || Vars.state.isPaused()) return;

        context.begin();
        try{
            if(asyncCore != null) asyncCore.begin();
            if(logic != null) logic.update();
            if(asyncCore != null) asyncCore.end();
        }finally{
            context.end();
        }
    }

    public void dispose(){
        if(disposed) return;
        disposed = true;

        Seam.worlds.remove(this);

        context.run(() -> {
            state.set(GameState.State.menu);
            Events.fire(new ResetEvent());
            shutdownAsync();

            all.clear();
            players.clear();
            bullet.clear();
            unit.clear();
            build.clear();
            sync.clear();
            draw.clear();
            fire.clear();
            puddle.clear();
            weather.clear();
            label.clear();
            powerGraph.clear();

            try{
                world.clearBuildings();
            }catch(Throwable ignored){
            }

            if(renderer != null){
                FakeRendererFactory.dispose(renderer);
                renderer = null;
            }
        });

        context.dispose();

        universe = null;
        asyncCore = null;
        avoidance = null;
        spawner = null;
        indexer = null;
        pathfinder = null;
        controlPath = null;
        fogControl = null;
        logic = null;
    }

    private void resizeTrees(int width, int height){
        float w = width * Vars.tilesize, h = height * Vars.tilesize;
        bullet.resize(0, 0, w, h);
        unit.resize(0, 0, w, h);
    }

    private void shutdownAsync(){
        if(asyncCore == null) return;
        try{
            ExecutorService exec = Reflect.get(asyncCore, "executor");
            if(exec != null) exec.shutdownNow();
        }catch(Throwable ignored){
        }
    }
}