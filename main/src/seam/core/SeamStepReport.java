package seam.core;

import seam.runtime.update.*;
import arc.struct.*;

public final class SeamStepReport{
    public final Seq<SeamRuntimeStepReport> runtimes = new Seq<>();

    public long startedNanos;
    public long endedNanos;

    public boolean skipped;
    public String skipReason;

    public boolean failed;
    public String failure;

    public SeamStepReport(){
        startedNanos = System.nanoTime();
    }

    public void add(SeamRuntimeStepReport runtime){
        runtimes.add(runtime);
    }

    public void skip(String reason){
        skipped = true;
        skipReason = reason;
        finish();
    }

    public void fail(Throwable throwable){
        failed = true;
        failure = SeamFailures.describe(throwable, "unknown");
        finish();
    }

    public void finish(){
        if(endedNanos == 0L){
            endedNanos = System.nanoTime();
        }
    }

    public long durationNanos(){
        if(startedNanos == 0L || endedNanos == 0L){
            return 0L;
        }

        return Math.max(0L, endedNanos - startedNanos);
    }

    public double durationMillis(){
        return durationNanos() / 1_000_000.0;
    }

    public int updatedRuntimeCount(){
        return runtimes.size;
    }

    @Override
    public String toString(){
        StringBuilder builder = new StringBuilder();

        builder.append("SeamStepReport{")
        .append("durationMs=").append(durationMillis())
        .append(", updatedRuntimes=").append(updatedRuntimeCount())
        .append(", skipped=").append(skipped);

        if(skipReason != null){
            builder.append(", skipReason='").append(skipReason).append('\'');
        }

        builder.append(", failed=").append(failed);

        if(failure != null){
            builder.append(", failure='").append(failure).append('\'');
        }

        builder.append("}");

        for(SeamRuntimeStepReport runtime : runtimes){
            builder.append('\n').append("  ").append(runtime);

            for(SeamPhaseReport phase : runtime.phases){
                builder.append('\n').append("    ").append(phase);
            }
        }

        return builder.toString();
    }
}
