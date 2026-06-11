package seam.world.tiles;

import arc.math.geom.*;

public final class SeamTileRef{
    public final int runtimeId;
    public final int x;
    public final int y;
    public final int tilePos;

    private SeamTileRef(int runtimeId, int x, int y, int tilePos){
        this.runtimeId = runtimeId;
        this.x = x;
        this.y = y;
        this.tilePos = tilePos;
    }

    public static SeamTileRef of(int runtimeId, int x, int y){
        return new SeamTileRef(runtimeId, x, y, Point2.pack(x, y));
    }

    public static SeamTileRef ofPos(int runtimeId, int tilePos){
        return new SeamTileRef(runtimeId, Point2.x(tilePos), Point2.y(tilePos), tilePos);
    }

    public boolean sameRuntime(SeamTileRef other){
        return other != null && runtimeId == other.runtimeId;
    }

    public boolean sameTile(SeamTileRef other){
        return other != null
        && runtimeId == other.runtimeId
        && tilePos == other.tilePos;
    }

    @Override
    public boolean equals(Object object){
        if(this == object){
            return true;
        }

        if(!(object instanceof SeamTileRef other)){
            return false;
        }

        return runtimeId == other.runtimeId
        && tilePos == other.tilePos;
    }

    @Override
    public int hashCode(){
        int result = runtimeId;
        result = 31 * result + tilePos;
        return result;
    }

    @Override
    public String toString(){
        return "SeamTileRef{" +
        "runtimeId=" + runtimeId +
        ", x=" + x +
        ", y=" + y +
        ", tilePos=" + tilePos +
        '}';
    }
}