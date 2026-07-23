package seam.core;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.logic.*;

public class SubWorld implements Entityc{
    public final World world;
    public final GameState state;
    public final SubWorldContext context;
    public final Camera camera;
    public @Nullable Renderer renderer;
    
    public BlockIndexer indexer;
    public Logic logic;
    public WaveSpawner spawner;
    public Pathfinder pathfinder;
    public ControlPathfinder controlPath;
    public GlobalVars logicVars;
    public FogControl fogControl;
    public Universe universe;
    public AsyncCore asyncCore;
    
    public final EntityGroup<Entityc> all;
    public final EntityGroup<Player> player;
    public final EntityGroup<Bullet> bullet;
    public final EntityGroup<Unit> unit;
    public final EntityGroup<Building> build;
    public final EntityGroup<Syncc> sync;
    public final EntityGroup<Drawc> draw;
    public final EntityGroup<Fire> fire;
    public final EntityGroup<Puddle> puddle;
    public final EntityGroup<WeatherState> weather;
    public final EntityGroup<WorldLabel> label;
    public final EntityGroup<PowerGraphUpdaterc> powerGraph;

    private int id;
    private boolean added;

    public SubWorld(){
        this.id = EntityGroup.nextId();
        this.world = new World();
        this.state = new GameState();
        this.camera = new Camera();
        this.context = new SubWorldContext(this, this.world, this.state);
        
        this.all = new EntityGroup<>(Entityc.class, false, false);
        this.player = new EntityGroup<>(Player.class, false, true);
        this.bullet = new EntityGroup<>(Bullet.class, true, false);
        this.unit = new EntityGroup<>(Unit.class, true, true);
        this.build = new EntityGroup<>(Building.class, false, false);
        this.sync = new EntityGroup<>(Syncc.class, false, true);
        this.draw = new EntityGroup<>(Drawc.class, false, false);
        this.fire = new EntityGroup<>(Fire.class, false, false);
        this.puddle = new EntityGroup<>(Puddle.class, false, false);
        this.weather = new EntityGroup<>(WeatherState.class, false, false);
        this.label = new EntityGroup<>(WorldLabel.class, false, true);
        this.powerGraph = new EntityGroup<>(PowerGraphUpdaterc.class, false, false);
        
        context.run(() -> {
            this.universe = new Universe();
            this.asyncCore = new AsyncCore();
            this.spawner = new WaveSpawner();
            this.indexer = new BlockIndexer();
            this.pathfinder = new Pathfinder();
            this.controlPath = new ControlPathfinder();
            this.fogControl = new FogControl();
            this.logicVars = new GlobalVars();
            this.logic = new Logic();
            if(!Vars.headless){
                this.renderer = FakeRendererFactory.create(this);
            }
        });
    }

    public void resize(int width, int height){
        context.run(() -> {
            world.resize(width, height);
            bullet.resize(0, 0, width * 8, height * 8);
            unit.resize(0, 0, width * 8, height * 8);
        });
    }

    @Override
    public void update(){
        if(!state.isPlaying()) return;
        
        context.begin();
        try{
            if(asyncCore != null) asyncCore.begin();
            if(logic != null) logic.update();
            if(asyncCore != null) asyncCore.end();
        }finally{
            context.end();
        }
    }

    @Override
    public <T extends Entityc> T self(){
        return (T)this;
    }

    @Override
    public <T> T as(){
        return (T)this;
    }

    @Override
    public boolean isAdded(){
        return added;
    }

    @Override
    public boolean isLocal(){
        return true;
    }

    @Override
    public boolean isRemote(){
        return false;
    }

    @Override
    public boolean serialize(){
        return false;
    }

    @Override
    public int classId(){
        return -1;
    }

    @Override
    public int id(){
        return id;
    }

    @Override
    public void id(int id){
        this.id = id;
    }

    @Override
    public void add(){
        if(!added){
            added = true;
            Groups.all.add(this);
        }
    }

    @Override
    public void remove(){
        if(added){
            added = false;
            Groups.all.remove(this);
            dispose();
        }
    }

    @Override
    public void afterRead(){}

    @Override
    public void afterReadAll(){}

    @Override
    public void beforeWrite(){}

    @Override
    public void read(Reads read){}

    @Override
    public void write(Writes write){}

    public void dispose(){
        if(pathfinder != null){
            try{
                Reflect.invoke(pathfinder, "stop");
            }catch(Throwable ignored){}
            pathfinder = null;
        }
        if(fogControl != null){
            try{
                Reflect.invoke(fogControl, "stop");
            }catch(Throwable ignored){}
            fogControl = null;
        }
        if(renderer != null){
            try{
                if(renderer.effectBuffer != null) renderer.effectBuffer.dispose();
                if(renderer.fog != null){
                    FrameBuffer sf = Reflect.get(renderer.fog, "staticFog");
                    if(sf != null) sf.dispose();
                    FrameBuffer df = Reflect.get(renderer.fog, "dynamicFog");
                    if(df != null) df.dispose();
                }
                if(renderer.blocks != null){
                    FrameBuffer sh = Reflect.get(renderer.blocks, "shadows");
                    if(sh != null) sh.dispose();
                    FrameBuffer dk = Reflect.get(renderer.blocks, "dark");
                    if(dk != null) dk.dispose();
                    Seq<SpriteCache>[] caches = Reflect.get(renderer.blocks, "caches");
                    if(caches != null){
                        for(Seq<SpriteCache> arr : caches){
                            if(arr != null){
                                for(SpriteCache cache : arr){
                                    cache.dispose();
                                }
                                arr.clear();
                            }
                        }
                    }
                }
            }catch(Throwable ignored){}
            renderer = null;
        }
    }
}
