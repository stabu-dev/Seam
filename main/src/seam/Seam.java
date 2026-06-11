package seam;

import seam.core.*;
import seam.graphics.*;
import seam.graphics.draw.*;
import seam.graphics.pick.*;
import seam.graphics.view.*;
import seam.runtime.*;
import seam.runtime.control.*;
import seam.world.*;
import seam.world.config.*;
import seam.world.construction.*;
import seam.world.terrain.*;
import arc.util.*;
import mindustry.mod.*;

public class Seam extends Mod{
    private static final SeamServices services = new SeamServices();

    public static final SeamRuntimeStack stack = services.stack;
    public static final SeamRuntimeRegistry runtimes = services.runtimes;
    public static final SeamRuntimeExecutor executor = services.executor;
    public static final SeamEngine engine = services.engine;
    public static final SeamConfigService config = services.config;
    public static final SeamBuildService builds = services.builds;
    public static final SeamTerrainService terrain = services.terrain;
    public static final SeamQueryService query = services.query;
    public static final SeamViewRegistry views = services.views;
    public static final SeamPickService picks = services.picks;
    public static final SeamRenderService rendering = services.rendering;
    public static final SeamDrawScope drawScope = services.drawScope;
    public static final SeamWorldDraw worldDraw = services.worldDraw;

    public static SeamRuntime mainRuntime;

    public Seam(){
        Log.info("[Seam] Loaded Seam constructor.");
    }

    @Override
    public void init(){
        SeamBootstrapValidator.validate();

        refreshMainRuntime();
        services.installEvents(Seam::refreshMainRuntime);

        Log.info("[Seam] Core initialized successfully.");
    }

    public static void refreshMainRuntime(){
        services.refreshMainRuntime();
        mainRuntime = services.mainRuntime();
    }
}
