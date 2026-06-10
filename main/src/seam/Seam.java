package seam;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;

public class Seam extends Mod{
    public static final SeamRuntimeStack stack = new SeamRuntimeStack();
    public static final SeamRuntimeRegistry runtimes = new SeamRuntimeRegistry();
    public static final SeamRuntimeExecutor executor = new SeamRuntimeExecutor(runtimes, stack);
    public static final SeamEngine engine = new SeamEngine(runtimes, stack, executor);
    public static final SeamConfigService config = new SeamConfigService(runtimes, executor);
    public static final SeamBuildService builds = new SeamBuildService(runtimes, executor);
    public static final SeamQueryService query = new SeamQueryService(runtimes, executor);

    public static SeamRuntime mainRuntime;

    public Seam(){
        Log.info("[Seam] Loaded Seam constructor.");
    }

    @Override
    public void init(){
        SeamBootstrapValidator.validate();

        refreshMainRuntime();

        Events.on(WorldLoadEvent.class, event -> refreshMainRuntime());

        Events.on(ResetEvent.class, event -> {
            runtimes.clearSubworlds();
            refreshMainRuntime();
        });

        Events.run(Trigger.update, engine::update);

        Log.info("[Seam] Core initialized successfully.");
    }

    public static void refreshMainRuntime(){
        runtimes.refreshMain();
        mainRuntime = runtimes.main();
    }
}