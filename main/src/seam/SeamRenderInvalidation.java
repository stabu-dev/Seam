package seam;

public final class SeamRenderInvalidation{
    public final int runtimeId;
    public final int x;
    public final int y;
    public final int tilePos;

    public int mask;
    public int radius;
    public int merges;

    private SeamRenderInvalidation(int runtimeId, int x, int y, int tilePos, int mask, int radius){
        this.runtimeId = runtimeId;
        this.x = x;
        this.y = y;
        this.tilePos = tilePos;
        this.mask = mask;
        this.radius = radius;
        this.merges = 1;
    }

    public static SeamRenderInvalidation tile(int runtimeId, int x, int y, int tilePos, int mask, int radius){
        return new SeamRenderInvalidation(runtimeId, x, y, tilePos, mask, radius);
    }

    public static SeamRenderInvalidation full(int runtimeId){
        return new SeamRenderInvalidation(runtimeId, -1, -1, -1, SeamRenderInvalidationType.full.mask, Integer.MAX_VALUE);
    }

    public boolean full(){
        return has(SeamRenderInvalidationType.full);
    }

    public boolean has(SeamRenderInvalidationType type){
        return type != null && (mask & type.mask) != 0;
    }

    public void merge(int mask, int radius){
        this.mask |= mask;
        this.radius = Math.max(this.radius, radius);
        this.merges++;
    }

    public SeamRenderInvalidation copy(){
        SeamRenderInvalidation copy = new SeamRenderInvalidation(runtimeId, x, y, tilePos, mask, radius);
        copy.merges = merges;
        return copy;
    }

    @Override
    public String toString(){
        return "SeamRenderInvalidation{" +
        "runtimeId=" + runtimeId +
        ", tile=" + x + "," + y +
        ", tilePos=" + tilePos +
        ", mask=" + mask +
        ", radius=" + radius +
        ", merges=" + merges +
        '}';
    }
}