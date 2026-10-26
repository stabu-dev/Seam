package seam;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import seam.content.*;
import seam.core.*;
import seam.world.blocks.*;

public class Seam extends Mod{
    public static final Seq<SubWorld> worlds = new Seq<>();
    public static final Seq<SubWorldMonitor.SubWorldMonitorBuild> monitors = new Seq<>();

    public Seam(){
        Log.info("[Seam] Loaded.");
    }

    @Override
    public void loadContent(){
        SeamBlocks.load();
    }

    @Override
    public void init(){
        Events.run(Trigger.preDraw, () -> {
            for(int i = 0; i < monitors.size; i++){
                monitors.get(i).renderSubWorld();
            }
        });

        Events.run(Trigger.update, () -> {
            for(int i = 0; i < worlds.size; i++){
                worlds.get(i).update();
            }
        });
    }

    public static SubWorld createWorld(){
        SubWorld world = new SubWorld();
        worlds.add(world);
        return world;
    }

    public static void removeWorld(SubWorld world){
        if(world != null) world.dispose();
    }
}