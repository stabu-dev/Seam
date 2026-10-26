package seam.core;

import java.lang.reflect.*;
import arc.*;
import arc.graphics.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Time.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;

/**
 * Installs a {@link SubWorld}'s state into global Mindustry/Arc statics for the duration of a scope.
 * Only world-scoped fields are swapped; shared host services (content, UI, net, control, logicVars, …) stay put.
 */
public class SubWorldContext{
    private static final Field timeRawField = field(Time.class, "timeRaw");
    private static final Field globalTimeRawField = field(Time.class, "globalTimeRaw");
    private static final Field pathWField = field(Pathfinder.class, "wwidth");
    private static final Field pathHField = field(Pathfinder.class, "wheight");
    private static final Field fogWField = field(FogControl.class, "ww");
    private static final Field fogHField = field(FogControl.class, "wh");

    private final SubWorld sub;
    private final SubWorldEvents events = new SubWorldEvents();
    private final Seq<DelayRun> runs = new Seq<>();

    private double timeRaw, globalTimeRaw;
    private float time, globalTime, delta = 1f;
    private int pathW, pathH, fogW, fogH;
    private int depth;

    // host snapshot
    private World hostWorld;
    private GameState hostState;
    private Pathfinder hostPathfinder;
    private ControlPathfinder hostControlPath;
    private Renderer hostRenderer;
    private Logic hostLogic;
    private WaveSpawner hostSpawner;
    private BlockIndexer hostIndexer;
    private FogControl hostFog;
    private AsyncCore hostAsync;
    private AvoidanceProcess hostAvoidance;
    private Universe hostUniverse;
    private Player hostPlayer;
    private Camera hostCamera;
    private Seq<DelayRun> hostRuns;
    private double hostTimeRaw, hostGlobalTimeRaw;
    private float hostTime, hostGlobalTime, hostDelta;
    private int hostPathW, hostPathH, hostFogW, hostFogH;

    private EntityGroup<Entityc> hostAll;
    private EntityGroup<Player> hostPlayers;
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

    public SubWorldContext(SubWorld sub){
        this.sub = sub;
    }

    public void begin(){
        if(depth++ > 0) return;

        hostWorld = Vars.world;
        hostState = Vars.state;
        hostPathfinder = Vars.pathfinder;
        hostControlPath = Vars.controlPath;
        hostRenderer = Vars.renderer;
        hostLogic = Vars.logic;
        hostSpawner = Vars.spawner;
        hostIndexer = Vars.indexer;
        hostFog = Vars.fogControl;
        hostAsync = Vars.asyncCore;
        hostAvoidance = Vars.avoidance;
        hostUniverse = Vars.universe;
        hostPlayer = Vars.player;
        hostCamera = Core.camera;

        hostTimeRaw = getDouble(timeRawField);
        hostGlobalTimeRaw = getDouble(globalTimeRawField);
        hostTime = Time.time;
        hostGlobalTime = Time.globalTime;
        hostDelta = Time.delta;
        hostRuns = Time.getRuns();

        hostPathW = getInt(pathWField);
        hostPathH = getInt(pathHField);
        hostFogW = getInt(fogWField);
        hostFogH = getInt(fogHField);

        hostAll = Groups.all;
        hostPlayers = Groups.player;
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

        Vars.world = sub.world;
        Vars.state = sub.state;
        Vars.pathfinder = sub.pathfinder;
        Vars.controlPath = sub.controlPath;
        Vars.renderer = sub.renderer;
        Vars.logic = sub.logic;
        Vars.spawner = sub.spawner;
        Vars.indexer = sub.indexer;
        Vars.fogControl = sub.fogControl;
        Vars.asyncCore = sub.asyncCore;
        Vars.avoidance = sub.avoidance;
        Vars.universe = sub.universe;
        Vars.player = null;
        Core.camera = sub.camera;

        setDouble(timeRawField, timeRaw);
        setDouble(globalTimeRawField, globalTimeRaw);
        Time.time = time;
        Time.globalTime = globalTime;
        Time.delta = delta;
        Time.setRuns(runs);

        setInt(pathWField, pathW);
        setInt(pathHField, pathH);
        setInt(fogWField, fogW);
        setInt(fogHField, fogH);

        Groups.all = sub.all;
        Groups.player = sub.players;
        Groups.bullet = sub.bullet;
        Groups.unit = sub.unit;
        Groups.build = sub.build;
        Groups.sync = sub.sync;
        Groups.draw = sub.draw;
        Groups.fire = sub.fire;
        Groups.puddle = sub.puddle;
        Groups.weather = sub.weather;
        Groups.label = sub.label;
        Groups.powerGraph = sub.powerGraph;

        events.begin();
    }

    public void end(){
        if(depth == 0) throw new IllegalStateException("SubWorldContext.end without begin");
        if(--depth > 0) return;

        try{
            events.end();
        }finally{
            timeRaw = getDouble(timeRawField);
            globalTimeRaw = getDouble(globalTimeRawField);
            time = Time.time;
            globalTime = Time.globalTime;
            delta = Time.delta;
            pathW = getInt(pathWField);
            pathH = getInt(pathHField);
            fogW = getInt(fogWField);
            fogH = getInt(fogHField);

            setDouble(timeRawField, hostTimeRaw);
            setDouble(globalTimeRawField, hostGlobalTimeRaw);
            Time.time = hostTime;
            Time.globalTime = hostGlobalTime;
            Time.delta = hostDelta;
            Time.setRuns(hostRuns);

            setInt(pathWField, hostPathW);
            setInt(pathHField, hostPathH);
            setInt(fogWField, hostFogW);
            setInt(fogHField, hostFogH);

            Vars.world = hostWorld;
            Vars.state = hostState;
            Vars.pathfinder = hostPathfinder;
            Vars.controlPath = hostControlPath;
            Vars.renderer = hostRenderer;
            Vars.logic = hostLogic;
            Vars.spawner = hostSpawner;
            Vars.indexer = hostIndexer;
            Vars.fogControl = hostFog;
            Vars.asyncCore = hostAsync;
            Vars.avoidance = hostAvoidance;
            Vars.universe = hostUniverse;
            Vars.player = hostPlayer;
            Core.camera = hostCamera;

            Groups.all = hostAll;
            Groups.player = hostPlayers;
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

            clearHost();
        }
    }

    public void run(Runnable task){
        begin();
        try{
            task.run();
        }finally{
            end();
        }
    }

    public boolean isActive(){
        return depth > 0;
    }

    public void dispose(){
        if(isActive()){
            // Best-effort unwind if dispose races an active scope.
            while(depth > 0) end();
        }
        runs.clear();
        events.dispose();
    }

    private void clearHost(){
        hostWorld = null;
        hostState = null;
        hostPathfinder = null;
        hostControlPath = null;
        hostRenderer = null;
        hostLogic = null;
        hostSpawner = null;
        hostIndexer = null;
        hostFog = null;
        hostAsync = null;
        hostAvoidance = null;
        hostUniverse = null;
        hostPlayer = null;
        hostCamera = null;
        hostRuns = null;
        hostAll = null;
        hostPlayers = null;
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

    private static Field field(Class<?> type, String name){
        try{
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }catch(Throwable t){
            throw new RuntimeException("Missing field " + type.getSimpleName() + "." + name, t);
        }
    }

    private static double getDouble(Field f){
        try{ return f.getDouble(null); }catch(Throwable t){ throw new RuntimeException(t); }
    }

    private static void setDouble(Field f, double v){
        try{ f.setDouble(null, v); }catch(Throwable t){ throw new RuntimeException(t); }
    }

    private static int getInt(Field f){
        try{ return f.getInt(null); }catch(Throwable t){ throw new RuntimeException(t); }
    }

    private static void setInt(Field f, int v){
        try{ f.setInt(null, v); }catch(Throwable t){ throw new RuntimeException(t); }
    }
}