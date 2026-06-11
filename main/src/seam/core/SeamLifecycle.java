package seam.core;

import mindustry.*;

public final class SeamLifecycle{
    private SeamLifecycle(){
    }

    public static boolean mainWorldReady(){
        return worldReady(Vars.world);
    }

    public static boolean worldReady(mindustry.core.World world){
        return world != null
        && world.tiles != null
        && world.tiles.width > 0
        && world.tiles.height > 0;
    }
}