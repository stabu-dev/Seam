package seam;

import arc.util.*;
import mindustry.mod.*;
import seam.core.*;
import seam.runtime.*;
import seam.runtime.activation.*;
import seam.runtime.control.*;
import seam.runtime.delayed.*;
import seam.runtime.load.*;
import seam.runtime.mutation.*;
import seam.runtime.update.*;

public class Seam extends Mod{
    private static final SeamServices services = new SeamServices();

    public static final SeamHostContext host = services.host;
    public static final SeamStateInstaller installer = services.installer;
    public static final SeamRuntimeRegistry runtimes = services.runtimes;
    public static final SeamRuntimeExecutor executor = services.executor;
    public static final SeamRuntimeStateLoader stateLoader = services.stateLoader;
    public static final SeamWorldActivationTransaction activation = services.activation;
    public static final SeamRuntimeDelayedRuns delayed = services.delayed;
    public static final SeamRuntimeUpdateTransaction update = services.update;
    public static final SeamRuntimeUpdatePipeline updatePipeline = services.updatePipeline;
    public static final SeamRuntimeMutationTransaction mutation = services.mutation;

    public Seam(){
        Log.info("[Seam] Loaded.");
    }

    @Override
    public void init(){
        SeamBootstrapValidator.validate();
        services.installEvents();
        Log.info("[Seam] Foundation initialized.");
    }
}