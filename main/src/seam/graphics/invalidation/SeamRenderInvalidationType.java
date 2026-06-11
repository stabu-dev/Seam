package seam.graphics.invalidation;

public enum SeamRenderInvalidationType{
    tile(1),
    block(1 << 1),
    floor(1 << 2),
    overlay(1 << 3),
    light(1 << 4),
    shadow(1 << 5),
    proximity(1 << 6),
    config(1 << 7),
    full(1 << 8);

    public final int mask;

    SeamRenderInvalidationType(int mask){
        this.mask = mask;
    }
}