package seam;

public final class SeamClock{
    private long frame;
    private double tick;
    private float time;
    private float delta;

    public long frame(){
        return frame;
    }

    public double tick(){
        return tick;
    }

    public float time(){
        return time;
    }

    public float delta(){
        return delta;
    }

    public void advance(float baseDelta){
        if(baseDelta < 0f){
            throw new IllegalArgumentException("Clock delta cannot be negative.");
        }

        this.delta = baseDelta;
        this.time += baseDelta;
        this.tick += baseDelta;
        this.frame++;
    }

    public void reset(){
        frame = 0L;
        tick = 0.0;
        time = 0f;
        delta = 0f;
    }

    @Override
    public String toString(){
        return "SeamClock{" +
        "frame=" + frame +
        ", tick=" + tick +
        ", time=" + time +
        ", delta=" + delta +
        '}';
    }
}