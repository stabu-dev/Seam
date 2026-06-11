package seam.core;

public final class SeamPhaseReport{
    public final SeamPhase phase;

    public long startedNanos;
    public long endedNanos;

    public boolean failed;
    public String failure;

    public SeamPhaseReport(SeamPhase phase){
        if(phase == null){
            throw new NullPointerException("phase");
        }

        this.phase = phase;
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

    public void begin(){
        startedNanos = System.nanoTime();
    }

    public void end(){
        endedNanos = System.nanoTime();
    }

    public void fail(Throwable throwable){
        failed = true;
        failure = SeamFailures.describe(throwable, "unknown");
    }

    @Override
    public String toString(){
        return "SeamPhaseReport{" +
        "phase=" + phase +
        ", durationMs=" + durationMillis() +
        ", failed=" + failed +
        (failure == null ? "" : ", failure='" + failure + '\'') +
        '}';
    }
}
