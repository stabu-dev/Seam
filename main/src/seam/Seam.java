package seam;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
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
        refreshMainRuntime();

        Events.on(WorldLoadEvent.class, event -> refreshMainRuntime());
        Events.on(ResetEvent.class, event -> refreshMainRuntime());

        Log.info("[Seam] Core initialized successfully.");
    }

    public static void refreshMainRuntime(){
        mainRuntime = SeamRuntime.wrapCurrent(0, "main");
    }
}