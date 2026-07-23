package seam.core;

import arc.*;
import arc.graphics.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Time.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.entities.*;
import mindustry.game.*;

public class SubWorldContext{
    private final SubWorld subworld;
    
    // Host state
    private World hostWorld;
    private GameState hostState;
    private Seq<DelayRun> hostRuns;
    private Pathfinder hostPathfinder;
    private Renderer hostRenderer;
    private Logic hostLogic;
    private WaveSpawner hostSpawner;
    private BlockIndexer hostIndexer;
    private FogControl hostFogControl;
    private AsyncCore hostAsyncCore;
    private Player hostPlayerVar;
    private Control hostControlVar;
    private Camera hostCamera;
    private Universe hostUniverse;

    private double hostTimeRaw;
    private double hostGlobalTimeRaw;
    private float hostTime;
    private float hostGlobalTime;
    private float hostDelta;
    
    private EntityGroup<Entityc> hostAll;
    private EntityGroup<Player> hostPlayer;
    private EntityGroup<Bullet> hostBullet;
    private EntityGroup<Unit> hostUnit;
    private EntityGroup<Building> hostBuild;
    private EntityGroup<Syncc> hostSync;
    private EntityGroup<Drawc> hostDraw;
    private EntityGroup<Fire> hostFire;
    private EntityGroup<Puddle> hostPuddle;
    private EntityGroup<WeatherState> hostWeather;
    private EntityGroup<WorldLabel> hostLabel;
    private EntityGroup<PowerGraphUpdaterc> hostPowerGraph;
    
    // Local state
    private final World localWorld;
    private final GameState localState;
    private final Seq<DelayRun> localRuns;
    private final SubWorldEvents localEvents;

    private double localTimeRaw = 0.0;
    private double localGlobalTimeRaw = 0.0;
    private float localTime = 0f;
    private float localGlobalTime = 0f;
    private float localDelta = 1f;
    
    private int activeDepth = 0;

    public SubWorldContext(SubWorld subworld, World world, GameState state){
        this.subworld = subworld;
        this.localWorld = world;
        this.localState = state;
        this.localRuns = new Seq<>();
        this.localEvents = new SubWorldEvents();
    }

    public void begin(){
        if(activeDepth++ > 0) return;
        
        hostWorld = Vars.world;
        hostState = Vars.state;
        hostPathfinder = Vars.pathfinder;
        hostRenderer = Vars.renderer;
        hostLogic = Vars.logic;
        hostSpawner = Vars.spawner;
        hostIndexer = Vars.indexer;
        hostFogControl = Vars.fogControl;
        hostAsyncCore = Vars.asyncCore;
        hostPlayerVar = Vars.player;
        hostControlVar = Vars.control;
        hostUniverse = Vars.universe;
        hostCamera = Core.camera;

        hostTimeRaw = Reflect.get(Time.class, "timeRaw");
        hostGlobalTimeRaw = Reflect.get(Time.class, "globalTimeRaw");
        hostTime = Time.time;
        hostGlobalTime = Time.globalTime;
        hostDelta = Time.delta;
        hostRuns = Reflect.get(Time.class, "runs");
        
        hostAll = Groups.all;
        hostPlayer = Groups.player;
        hostBullet = Groups.bullet;
        hostUnit = Groups.unit;
        hostBuild = Groups.build;
        hostSync = Groups.sync;
        hostDraw = Groups.draw;
        hostFire = Groups.fire;
        hostPuddle = Groups.puddle;
        hostWeather = Groups.weather;
        hostLabel = Groups.label;
        hostPowerGraph = Groups.powerGraph;
        
        Vars.world = localWorld;
        Vars.state = localState;
        if(subworld.pathfinder != null) Vars.pathfinder = subworld.pathfinder;
        if(subworld.renderer != null) Vars.renderer = subworld.renderer;
        if(subworld.logic != null) Vars.logic = subworld.logic;
        if(subworld.spawner != null) Vars.spawner = subworld.spawner;
        if(subworld.indexer != null) Vars.indexer = subworld.indexer;
        if(subworld.fogControl != null) Vars.fogControl = subworld.fogControl;
        if(subworld.asyncCore != null) Vars.asyncCore = subworld.asyncCore;
        if(subworld.universe != null) Vars.universe = subworld.universe;
        Core.camera = subworld.camera;

        Reflect.set(Time.class, "timeRaw", localTimeRaw);
        Reflect.set(Time.class, "globalTimeRaw", localGlobalTimeRaw);
        Time.time = localTime;
        Time.globalTime = localGlobalTime;
        Time.delta = localDelta;
        Reflect.set(Time.class, "runs", localRuns);
        
        Groups.all = subworld.all;
        Groups.player = subworld.player;
        Groups.bullet = subworld.bullet;
        Groups.unit = subworld.unit;
        Groups.build = subworld.build;
        Groups.sync = subworld.sync;
        Groups.draw = subworld.draw;
        Groups.fire = subworld.fire;
        Groups.puddle = subworld.puddle;
        Groups.weather = subworld.weather;
        Groups.label = subworld.label;
        Groups.powerGraph = subworld.powerGraph;
        
        localEvents.begin();
    }

    public void end(){
        if(activeDepth == 0) throw new IllegalStateException("Not active.");
        if(--activeDepth > 0) return;
        
        localEvents.end();

        localTimeRaw = Reflect.get(Time.class, "timeRaw");
        localGlobalTimeRaw = Reflect.get(Time.class, "globalTimeRaw");
        localTime = Time.time;
        localGlobalTime = Time.globalTime;
        localDelta = Time.delta;

        Reflect.set(Time.class, "timeRaw", hostTimeRaw);
        Reflect.set(Time.class, "globalTimeRaw", hostGlobalTimeRaw);
        Time.time = hostTime;
        Time.globalTime = hostGlobalTime;
        Time.delta = hostDelta;
        Reflect.set(Time.class, "runs", hostRuns);
        
        Vars.world = hostWorld;
        Vars.state = hostState;
        Vars.pathfinder = hostPathfinder;
        Vars.renderer = hostRenderer;
        Vars.logic = hostLogic;
        Vars.spawner = hostSpawner;
        Vars.indexer = hostIndexer;
        Vars.fogControl = hostFogControl;
        Vars.asyncCore = hostAsyncCore;
        Vars.player = hostPlayerVar;
        Vars.control = hostControlVar;
        Vars.universe = hostUniverse;
        Core.camera = hostCamera;
        
        Groups.all = hostAll;
        Groups.player = hostPlayer;
        Groups.bullet = hostBullet;
        Groups.unit = hostUnit;
        Groups.build = hostBuild;
        Groups.sync = hostSync;
        Groups.draw = hostDraw;
        Groups.fire = hostFire;
        Groups.puddle = hostPuddle;
        Groups.weather = hostWeather;
        Groups.label = hostLabel;
        Groups.powerGraph = hostPowerGraph;

        hostWorld = null;
        hostState = null;
        hostPathfinder = null;
        hostRenderer = null;
        hostLogic = null;
        hostSpawner = null;
        hostIndexer = null;
        hostFogControl = null;
        hostAsyncCore = null;
        hostPlayerVar = null;
        hostControlVar = null;
        hostUniverse = null;
        hostCamera = null;
        hostRuns = null;

        hostAll = null;
        hostPlayer = null;
        hostBullet = null;
        hostUnit = null;
        hostBuild = null;
        hostSync = null;
        hostDraw = null;
        hostFire = null;
        hostPuddle = null;
        hostWeather = null;
        hostLabel = null;
        hostPowerGraph = null;
    }
    
    public void run(Runnable task){
        begin();
        try{
            task.run();
        }finally{
            end();
        }
    }
    
    public void updateRuns(){
        if(localRuns.size > 0){
            for(int i = 0; i < localRuns.size; i++){
                DelayRun run = localRuns.get(i);
                float delay = Reflect.get(run, "delay");
                delay -= Time.delta;
                Reflect.set(run, "delay", delay);
                if(delay <= 0){
                    localRuns.remove(i);
                    Runnable r = Reflect.get(run, "finish");
                    if(r != null) r.run();
                    Pools.free(run);
                    i--;
                }
            }
        }
    }
}

