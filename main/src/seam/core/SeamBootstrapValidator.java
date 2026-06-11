package seam.core;

import arc.util.*;
import mindustry.*;
import mindustry.gen.*;

public final class SeamBootstrapValidator{
    public static void validate(){
        Log.info("[Seam] Running bootstrap checks...");
        try{
            checkNotNull(Vars.world, "Vars.world");
            checkNotNull(Vars.state, "Vars.state");
            checkNotNull(Vars.collisions, "Vars.collisions");
            checkNotNull(Vars.indexer, "Vars.indexer");
            checkNotNull(Vars.pathfinder, "Vars.pathfinder");

            checkNotNull(Groups.all, "Groups.all");
            checkNotNull(Groups.player, "Groups.player");
            checkNotNull(Groups.bullet, "Groups.bullet");
            checkNotNull(Groups.unit, "Groups.unit");
            checkNotNull(Groups.build, "Groups.build");
            checkNotNull(Groups.sync, "Groups.sync");
            checkNotNull(Groups.draw, "Groups.draw");
            checkNotNull(Groups.fire, "Groups.fire");
            checkNotNull(Groups.puddle, "Groups.puddle");
            checkNotNull(Groups.weather, "Groups.weather");
            checkNotNull(Groups.label, "Groups.label");
            checkNotNull(Groups.powerGraph, "Groups.powerGraph");

            Log.info("[Seam] Bootstrap validation completed successfully.");
        }catch(Throwable e){
            throw new RuntimeException("Seam unsupported on this Mindustry build: runtime group layout mismatch.", e);
        }
    }

    private static void checkNotNull(Object obj, String fieldName){
        if(obj == null){
            throw new NullPointerException(fieldName + " is null");
        }
    }
}
