package seam;

import arc.util.*;
import mindustry.*;
import mindustry.mod.*;

public class Seam extends Mod{
    public static final SeamRuntimeStack stack = new SeamRuntimeStack();
    public static SeamRuntime mainRuntime;

    public Seam(){
        Log.info("[Seam] Loaded Seam constructor.");
    }

    @Override
    public void init(){
        SeamBootstrapValidator.validate();

        mainRuntime = new SeamRuntime(
        0,
        "main",
        Vars.world,
        Vars.state,
        new SeamGroupSet(Vars.world.width(), Vars.world.height()),
        Vars.collisions
        );

        Log.info("[Seam] Core initialized successfully.");
    }
}