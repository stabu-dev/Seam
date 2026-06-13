package seam.core;

import seam.runtime.mutations.*;
import seam.runtime.*;
import seam.runtime.control.*;
import seam.runtime.update.*;
import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.game.Teams.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.storage.*;

public final class SeamEngine{
    private final SeamRuntimeRegistry runtimes;
    private final SeamRuntimeStack stack;
    private final SeamRuntimeExecutor executor;

    private boolean enabled = true;
    private boolean automatic = true;
    private boolean validateAfterStep = true;
    private boolean respectMainPause = true;

    private SeamStepReport lastReport;

    public SeamEngine(SeamRuntimeRegistry runtimes, SeamRuntimeStack stack, SeamRuntimeExecutor executor){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(stack == null){
            throw new NullPointerException("stack");
        }

        if(executor == null){
            throw new NullPointerException("executor");
        }

        this.runtimes = runtimes;
        this.stack = stack;
        this.executor = executor;
    }

    public boolean enabled(){
        return enabled;
    }

    public void enabled(boolean enabled){
        this.enabled = enabled;
    }

    public boolean automatic(){
        return automatic;
    }

    public void automatic(boolean automatic){
        this.automatic = automatic;
    }

    public boolean validateAfterStep(){
        return validateAfterStep;
    }

    public void validateAfterStep(boolean validateAfterStep){
        this.validateAfterStep = validateAfterStep;
    }

    public boolean respectMainPause(){
        return respectMainPause;
    }

    public void respectMainPause(boolean respectMainPause){
        this.respectMainPause = respectMainPause;
    }

    public SeamStepReport lastReport(){
        return lastReport;
    }

    public SeamStepReport update(){
        if(!automatic){
            SeamStepReport report = new SeamStepReport();
            report.skip("automatic updates are disabled");
            lastReport = report;
            return report;
        }

        if(!SeamLifecycle.mainWorldReady()){
            SeamStepReport report = new SeamStepReport();
            report.skip("main world is not ready");
            lastReport = report;
            return report;
        }

        if(respectMainPause && Vars.state != null && Vars.state.isPaused()){
            SeamStepReport report = new SeamStepReport();
            report.skip("main game is paused");
            lastReport = report;
            return report;
        }

        return step();
    }

    public SeamStepReport step(){
        SeamStepReport report = new SeamStepReport();
        lastReport = report;

        if(!enabled){
            report.skip("engine is disabled");
            return report;
        }

        if(!SeamLifecycle.mainWorldReady()){
            IllegalStateException exception = new IllegalStateException("Cannot step SeamEngine: main world is not ready.");
            report.fail(exception);
            throw exception;
        }

        if(stack.active()){
            IllegalStateException exception = new IllegalStateException("Cannot step SeamEngine while a runtime context is already active.");
            report.fail(exception);
            throw exception;
        }

        try{
            Seq<SeamRuntime> copy = runtimes.all();

            for(SeamRuntime runtime : copy){
                if(!runtime.updateEnabled()){
                    continue;
                }

                SeamRuntimeStepReport runtimeReport = updateRuntime(runtime);
                report.add(runtimeReport);
            }

            if(validateAfterStep){
                SeamRuntimeValidator.validateRestoredToMain(runtimes, stack);
            }

            report.finish();
            return report;
        }catch(Throwable throwable){
            report.fail(throwable);
            throw throwable;
        }
    }

    public SeamStepReport step(int amount){
        if(amount < 0){
            throw new IllegalArgumentException("Step amount cannot be negative.");
        }

        SeamStepReport report = null;

        for(int i = 0; i < amount; i++){
            report = step();
        }

        if(report == null){
            report = new SeamStepReport();
            report.skip("zero steps requested");
            lastReport = report;
        }

        return report;
    }

    private SeamRuntimeStepReport updateRuntime(SeamRuntime runtime){
        runtime.requireWorldReady();

        if(runtime.validateOnUpdate()){
            SeamRuntimeValidator.validateRuntime(runtime, false);
        }

        SeamRuntimeStepReport report = new SeamRuntimeStepReport(runtime);
        report.begin(runtime);

        SeamRuntimeUpdatePolicy policy = runtime.updatePolicy();

        try{
            run(runtime, SeamPhase.updatePre, report, active -> {
                active.clock.advance(Time.delta);
                active.state.tick += active.clock.delta();
                active.state.updateId++;
                return null;
            });

            if(policy.mutations){
                run(runtime, SeamPhase.updateMutations, report, active -> {
                    Seq<SeamMutationResult> results = active.mutations.drain(active);
                    report.recordMutations(results);
                    return null;
                });
            }

            if(policy.teams){
                run(runtime, SeamPhase.updateTeams, report, active -> {
                    active.state.teams.updateTeamStats();
                    return null;
                });
            }

            if(policy.logic){
                run(runtime, SeamPhase.updateLogic, report, active -> {
                    updateLogicState(active);
                    return null;
                });
            }

            if(policy.ai){
                run(runtime, SeamPhase.updateAi, report, active -> {
                    updateTeamAi(active);
                    return null;
                });
            }

            if(policy.objectives){
                run(runtime, SeamPhase.updateObjectives, report, active -> {
                    if(!active.state.isEditor()){
                        active.state.rules.objectives.update();
                    }

                    return null;
                });
            }

            if(policy.waves){
                run(runtime, SeamPhase.updateWaves, report, active -> {
                    updateWaves(active);
                    return null;
                });
            }

            if(policy.environment){
                run(runtime, SeamPhase.updateEnvironment, report, active -> {
                    updateEnvironment(active);
                    return null;
                });
            }

            if(usesVanillaCentralEntityUpdate(policy)){
                run(runtime, SeamPhase.updateGroups, report, active -> {
                    Groups.update();
                    return null;
                });
            }else{
                updateLightweightBuildingRuntime(runtime, report, policy);
            }

            run(runtime, SeamPhase.updatePost, report, active -> {
                if(active.validateOnUpdate()){
                    SeamRuntimeValidator.validateActiveContext(active);
                }

                return null;
            });

            return report;
        }finally{
            report.end(runtime);
        }
    }

    private void updateLightweightBuildingRuntime(SeamRuntime runtime, SeamRuntimeStepReport report, SeamRuntimeUpdatePolicy policy){
        if(policy.buildings){
            run(runtime, SeamPhase.updateBuildings, report, active -> {
                Groups.build.update();
                return null;
            });
        }

        if(policy.power){
            run(runtime, SeamPhase.updatePower, report, active -> {
                Groups.powerGraph.update();
                return null;
            });
        }
    }

    private boolean usesVanillaCentralEntityUpdate(SeamRuntimeUpdatePolicy policy){
        return policy.puddles
        || policy.fires
        || policy.weather
        || policy.bullets
        || policy.units
        || policy.sync
        || policy.draw
        || policy.collisions;
    }

    private void updateLogicState(SeamRuntime runtime){
        if(!runtime.state.isGame() || runtime.state.isPaused()){
            return;
        }

        if(Vars.logicVars != null){
            Vars.logicVars.update();
        }
    }

    private void updateTeamAi(SeamRuntime runtime){
        if(runtime.state.isEditor() || Vars.net.client()){
            return;
        }

        for(TeamData data : runtime.state.teams.getActive()){
            Rules.TeamRule rules = data.team.rules();

            if(rules.fillItems && data.cores.size > 0 && Vars.content != null){
                Building core = data.cores.first();

                Vars.content.items().each(item -> {
                    if(item.isOnPlanet(runtime.state.getPlanet()) && !item.isHidden()){
                        core.items.set(item, core.getMaximumAccepted(item));
                    }
                });
            }

            if(rules.buildAi && !runtime.state.rules.pvp){
                if(data.buildAi == null){
                    data.buildAi = new BaseBuilderAI(data);
                }

                data.buildAi.update();
            }

            if(rules.rtsAi){
                if(data.rtsAi == null){
                    data.rtsAi = new RtsAI(data);
                }

                data.rtsAi.update();
            }

            if(rules.prebuildAi){
                for(Building core : data.cores){
                    if(!(core.block instanceof CoreBlock coreBlock)){
                        continue;
                    }

                    Seq<Unit> units = data.getUnits(coreBlock.unitType);

                    if(units == null || !units.contains(unit -> unit.flag == core.pos())){
                        Unit unit = coreBlock.unitType.spawn(core, data.team);
                        unit.flag = core.pos();
                        unit.add();
                        Units.notifyUnitSpawn(unit);
                        Fx.spawn.at(unit);
                    }
                }
            }
        }
    }

    private void updateWaves(SeamRuntime runtime){
        if(runtime.state.rules.weather.size > 0 && !Vars.net.client() && !runtime.state.isEditor()){
            updateWeather(runtime);
        }

        if(runtime.state.rules.waves && runtime.state.rules.waveTimer && !runtime.state.gameOver && !isWaitingWave(runtime)){
            runtime.state.wavetime = Math.max(runtime.state.wavetime - Time.delta, 0f);
        }

        if(!Vars.net.client() && runtime.state.wavetime <= 0f && runtime.state.rules.waves){
            runtime.waveSpawner.spawnEnemies();
            runtime.state.wave++;
            runtime.state.wavetime = runtime.state.rules.waveSpacing * (runtime.state.isCampaign() ? runtime.state.getPlanet().campaignRules.difficulty.waveTimeMultiplier : 1f);
            Events.fire(new EventType.WaveEvent());
        }
    }

    private boolean isWaitingWave(SeamRuntime runtime){
        return (runtime.state.rules.waitEnemies || (runtime.state.wave >= runtime.state.rules.winWave && runtime.state.rules.winWave > 0)) && runtime.state.enemies > 0;
    }

    private void updateWeather(SeamRuntime runtime){
        runtime.state.rules.weather.removeAll(entry -> entry.weather == null);

        for(Weather.WeatherEntry entry : runtime.state.rules.weather){
            entry.cooldown -= Time.delta;

            if((entry.cooldown < 0f || entry.always) && !entry.weather.isActive()){
                float duration = entry.always ? Float.POSITIVE_INFINITY : Mathf.random(entry.minDuration, entry.maxDuration);
                entry.cooldown = duration + Mathf.random(entry.minFrequency, entry.maxFrequency);
                Tmp.v1.setToRandomDirection();
                entry.weather.create(entry.intensity, duration).windVector.set(Tmp.v1.x, Tmp.v1.y);
            }
        }
    }

    private void updateEnvironment(SeamRuntime runtime){
        if(!Vars.net.client()){
            runtime.state.enemies = Groups.unit.count(unit -> unit.team() == runtime.state.rules.waveTeam && unit.isEnemy());
        }

        runtime.state.envAttrs.clear();
        runtime.state.envAttrs.add(runtime.state.rules.attributes);
        Groups.weather.each(weather -> runtime.state.envAttrs.add(weather.weather.attrs, weather.opacity));
    }

    private void run(
    SeamRuntime runtime,
    SeamPhase phase,
    SeamRuntimeStepReport runtimeReport,
    SeamRuntimeExecutor.Call<Void> action
    ){
        SeamPhaseReport phaseReport = new SeamPhaseReport(phase);
        runtimeReport.add(phaseReport);

        phaseReport.begin();

        try{
            executor.call(runtime, phase, active -> {
                action.run(active);
                return null;
            });
        }catch(Throwable throwable){
            phaseReport.fail(throwable);
            Log.err("Seam runtime update failed. Runtime: @, phase: @", runtime, phase);
            throw throwable;
        }finally{
            phaseReport.end();
        }
    }
}
