package seam;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import seam.core.*;
import seam.content.*;
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
        Log.info("[Seam] Foundation initialized. Ready to create SubWorlds.");

        Events.run(Trigger.preDraw, () -> {
            for(SubWorldMonitor.SubWorldMonitorBuild b : monitors) b.renderSubWorld();
        });

        Events.run(Trigger.update, () -> {
            for(SubWorld w : worlds) w.update();
        });
    }

    public static SubWorld createWorld(){
        SubWorld world = new SubWorld();
        worlds.add(world);
        return world;
    }

    public static void removeWorld(SubWorld world){
        if(worlds.remove(world)){
            world.dispose();
        }
    }
}