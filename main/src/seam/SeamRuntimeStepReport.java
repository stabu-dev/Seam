package seam;

import arc.struct.*;

public final class SeamRuntimeStepReport{
    public final int runtimeId;
    public final String runtimeName;
    public final SeamRuntimeKind runtimeKind;

    public final Seq<SeamPhaseReport> phases = new Seq<>();

    public long startedNanos;
    public long endedNanos;

    public long frameBefore;
    public long frameAfter;

    public double clockTickBefore;
    public double clockTickAfter;

    public double stateTickBefore;
    public double stateTickAfter;

    public int mutationPendingBefore;
    public int mutationPendingAfter;
    public int mutationApplied;
    public int mutationFailed;

    public int renderInvalidationPendingBefore;
    public int renderInvalidationPendingAfter;

    public int buildCount;
    public int powerGraphCount;
    public int bulletCount;
    public int unitCount;
    public int puddleCount;
    public int fireCount;
    public int weatherCount;
    public int drawCount;
    public int syncCount;

    public SeamRuntimeStepReport(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtimeId = runtime.id;
        this.runtimeName = runtime.name;
        this.runtimeKind = runtime.kind;
    }

    public void begin(SeamRuntime runtime){
        startedNanos = System.nanoTime();

        frameBefore = runtime.clock.frame();
        clockTickBefore = runtime.clock.tick();
        stateTickBefore = runtime.state.tick;

        mutationPendingBefore = runtime.mutations.size();
        renderInvalidationPendingBefore = runtime.renderInvalidation.size();
    }

    public void end(SeamRuntime runtime){
        endedNanos = System.nanoTime();

        frameAfter = runtime.clock.frame();
        clockTickAfter = runtime.clock.tick();
        stateTickAfter = runtime.state.tick;

        mutationPendingAfter = runtime.mutations.size();
        renderInvalidationPendingAfter = runtime.renderInvalidation.size();

        buildCount = runtime.groups.build.size();
        powerGraphCount = runtime.groups.powerGraph.size();
        bulletCount = runtime.groups.bullet.size();
        unitCount = runtime.groups.unit.size();
        puddleCount = runtime.groups.puddle.size();
        fireCount = runtime.groups.fire.size();
        weatherCount = runtime.groups.weather.size();
        drawCount = runtime.groups.draw.size();
        syncCount = runtime.groups.sync.size();
    }

    public void add(SeamPhaseReport phase){
        phases.add(phase);
    }

    public void recordMutations(Seq<SeamMutationResult> results){
        mutationApplied += results.size;

        for(SeamMutationResult result : results){
            if(!result.success){
                mutationFailed++;
            }
        }
    }

    public long durationNanos(){
        return endedNanos - startedNanos;
    }

    public double durationMillis(){
        return durationNanos() / 1_000_000.0;
    }

    public boolean failed(){
        for(SeamPhaseReport phase : phases){
            if(phase.failed){
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString(){
        return "SeamRuntimeStepReport{" +
        "runtimeId=" + runtimeId +
        ", runtimeName='" + runtimeName + '\'' +
        ", runtimeKind=" + runtimeKind +
        ", durationMs=" + durationMillis() +
        ", frame=" + frameBefore + "->" + frameAfter +
        ", clockTick=" + clockTickBefore + "->" + clockTickAfter +
        ", stateTick=" + stateTickBefore + "->" + stateTickAfter +
        ", mutations=" + mutationApplied +
        ", mutationFailed=" + mutationFailed +
        ", mutationPending=" + mutationPendingBefore + "->" + mutationPendingAfter +
        ", renderInvalidationPending=" + renderInvalidationPendingBefore + "->" + renderInvalidationPendingAfter +
        ", builds=" + buildCount +
        ", powerGraphs=" + powerGraphCount +
        ", bullets=" + bulletCount +
        ", units=" + unitCount +
        ", puddles=" + puddleCount +
        ", fires=" + fireCount +
        ", weather=" + weatherCount +
        ", draw=" + drawCount +
        ", sync=" + syncCount +
        ", phases=" + phases.size +
        ", failed=" + failed() +
        '}';
    }
}