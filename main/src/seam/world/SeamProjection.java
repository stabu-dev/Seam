package seam.world;

import seam.runtime.*;
import seam.world.tiles.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;

public final class SeamProjection{
    private float hostOriginX;
    private float hostOriginY;

    private float runtimeOriginX;
    private float runtimeOriginY;

    private float scale = 1f;
    private float rotationDeg;

    public SeamProjection(){
    }

    public SeamProjection(float hostOriginX, float hostOriginY){
        this.hostOriginX = hostOriginX;
        this.hostOriginY = hostOriginY;
    }

    public static SeamProjection identity(){
        return new SeamProjection();
    }

    public static SeamProjection at(float hostOriginX, float hostOriginY){
        return new SeamProjection(hostOriginX, hostOriginY);
    }

    public float hostOriginX(){
        return hostOriginX;
    }

    public float hostOriginY(){
        return hostOriginY;
    }

    public float runtimeOriginX(){
        return runtimeOriginX;
    }

    public float runtimeOriginY(){
        return runtimeOriginY;
    }

    public float scale(){
        return scale;
    }

    public float rotationDeg(){
        return rotationDeg;
    }

    public SeamProjection hostOrigin(float x, float y){
        requireFinite(x, "hostOriginX");
        requireFinite(y, "hostOriginY");

        this.hostOriginX = x;
        this.hostOriginY = y;

        return this;
    }

    public SeamProjection runtimeOrigin(float x, float y){
        requireFinite(x, "runtimeOriginX");
        requireFinite(y, "runtimeOriginY");

        this.runtimeOriginX = x;
        this.runtimeOriginY = y;

        return this;
    }

    public SeamProjection scale(float scale){
        requireFinite(scale, "scale");

        if(scale <= 0f){
            throw new IllegalArgumentException("Projection scale must be positive.");
        }

        this.scale = scale;

        return this;
    }

    public SeamProjection rotationDeg(float rotationDeg){
        requireFinite(rotationDeg, "rotationDeg");

        this.rotationDeg = rotationDeg;

        return this;
    }

    public Mat writeTransform(Mat out){
        if(out == null){
            throw new NullPointerException("out");
        }

        return out
        .idt()
        .translate(hostOriginX, hostOriginY)
        .rotate(rotationDeg)
        .scale(scale, scale)
        .translate(-runtimeOriginX, -runtimeOriginY);
    }

    public Mat transform(){
        return writeTransform(new Mat());
    }

    public Vec2 runtimeWorldToHost(float runtimeWorldX, float runtimeWorldY){
        return runtimeWorldToHost(runtimeWorldX, runtimeWorldY, new Vec2());
    }

    public Vec2 runtimeWorldToHost(float runtimeWorldX, float runtimeWorldY, Vec2 out){
        if(out == null){
            throw new NullPointerException("out");
        }

        float dx = (runtimeWorldX - runtimeOriginX) * scale;
        float dy = (runtimeWorldY - runtimeOriginY) * scale;

        float cos = Mathf.cosDeg(rotationDeg);
        float sin = Mathf.sinDeg(rotationDeg);

        return out.set(
        hostOriginX + dx * cos - dy * sin,
        hostOriginY + dx * sin + dy * cos
        );
    }

    public Vec2 hostToRuntimeWorld(float hostWorldX, float hostWorldY){
        return hostToRuntimeWorld(hostWorldX, hostWorldY, new Vec2());
    }

    public Vec2 hostToRuntimeWorld(float hostWorldX, float hostWorldY, Vec2 out){
        if(out == null){
            throw new NullPointerException("out");
        }

        float dx = hostWorldX - hostOriginX;
        float dy = hostWorldY - hostOriginY;

        float cos = Mathf.cosDeg(rotationDeg);
        float sin = Mathf.sinDeg(rotationDeg);

        return out.set(
        runtimeOriginX + (dx * cos + dy * sin) / scale,
        runtimeOriginY + (-dx * sin + dy * cos) / scale
        );
    }

    public Vec2 runtimeTileCenterToHost(int tileX, int tileY){
        return runtimeTileCenterToHost(tileX, tileY, new Vec2());
    }

    public Vec2 runtimeTileCenterToHost(int tileX, int tileY, Vec2 out){
        float size = Vars.tilesize;

        return runtimeWorldToHost(
        tileX * size + size / 2f,
        tileY * size + size / 2f,
        out
        );
    }

    public boolean containsRuntimeWorld(SeamRuntime runtime, float runtimeWorldX, float runtimeWorldY){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(!runtime.worldReady()){
            return false;
        }

        float width = runtime.world.width() * Vars.tilesize;
        float height = runtime.world.height() * Vars.tilesize;

        return runtimeWorldX >= 0f
        && runtimeWorldY >= 0f
        && runtimeWorldX < width
        && runtimeWorldY < height;
    }

    public Rect runtimeBounds(SeamRuntime runtime, Rect hostBounds, float growth, Rect out){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(hostBounds == null){
            throw new NullPointerException("hostBounds");
        }

        if(out == null){
            throw new NullPointerException("out");
        }

        requireFinite(hostBounds.x, "hostBounds.x");
        requireFinite(hostBounds.y, "hostBounds.y");
        requireFinite(hostBounds.width, "hostBounds.width");
        requireFinite(hostBounds.height, "hostBounds.height");
        requireFinite(growth, "growth");

        if(growth < 0f){
            throw new IllegalArgumentException("growth must be non-negative.");
        }

        if(!runtime.worldReady() || hostBounds.width <= 0f || hostBounds.height <= 0f){
            return out.set(0f, 0f, 0f, 0f);
        }

        Vec2 a = hostToRuntimeWorld(hostBounds.x, hostBounds.y, Tmp.v1);
        Vec2 b = hostToRuntimeWorld(hostBounds.x + hostBounds.width, hostBounds.y, Tmp.v2);
        Vec2 c = hostToRuntimeWorld(hostBounds.x + hostBounds.width, hostBounds.y + hostBounds.height, Tmp.v3);
        Vec2 d = hostToRuntimeWorld(hostBounds.x, hostBounds.y + hostBounds.height, Tmp.v4);

        setBounds(a, b, c, d, out);
        out.grow(growth);

        float worldW = runtime.world.unitWidth();
        float worldH = runtime.world.unitHeight();

        float x1 = Math.max(0f, out.x);
        float y1 = Math.max(0f, out.y);
        float x2 = Math.min(worldW, out.x + out.width);
        float y2 = Math.min(worldH, out.y + out.height);

        if(x2 <= x1 || y2 <= y1){
            return out.set(0f, 0f, 0f, 0f);
        }

        return out.set(x1, y1, x2 - x1, y2 - y1);
    }

    public int runtimeTileX(float runtimeWorldX){
        return (int)Math.floor(runtimeWorldX / Vars.tilesize);
    }

    public int runtimeTileY(float runtimeWorldY){
        return (int)Math.floor(runtimeWorldY / Vars.tilesize);
    }

    public SeamTileRef hostToRuntimeTile(SeamRuntime runtime, int runtimeId, float hostWorldX, float hostWorldY){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        Vec2 runtimeWorld = hostToRuntimeWorld(hostWorldX, hostWorldY, new Vec2());

        if(!containsRuntimeWorld(runtime, runtimeWorld.x, runtimeWorld.y)){
            return null;
        }

        return SeamTileRef.of(
        runtimeId,
        runtimeTileX(runtimeWorld.x),
        runtimeTileY(runtimeWorld.y)
        );
    }

    public Rect hostBounds(SeamRuntime runtime, Rect out){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(out == null){
            throw new NullPointerException("out");
        }

        if(!runtime.worldReady()){
            return out.set(0f, 0f, 0f, 0f);
        }

        float width = runtime.world.width() * Vars.tilesize;
        float height = runtime.world.height() * Vars.tilesize;

        Vec2 a = runtimeWorldToHost(0f, 0f, Tmp.v1);
        Vec2 b = runtimeWorldToHost(width, 0f, Tmp.v2);
        Vec2 c = runtimeWorldToHost(width, height, Tmp.v3);
        Vec2 d = runtimeWorldToHost(0f, height, Tmp.v4);

        return setBounds(a, b, c, d, out);
    }

    public SeamProjection copy(){
        return new SeamProjection()
        .hostOrigin(hostOriginX, hostOriginY)
        .runtimeOrigin(runtimeOriginX, runtimeOriginY)
        .scale(scale)
        .rotationDeg(rotationDeg);
    }

    private static void requireFinite(float value, String name){
        if(!Float.isFinite(value)){
            throw new IllegalArgumentException(name + " must be finite.");
        }
    }

    private static Rect setBounds(Vec2 a, Vec2 b, Vec2 c, Vec2 d, Rect out){
        float minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        float minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        float maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        float maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));

        return out.set(minX, minY, maxX - minX, maxY - minY);
    }

    @Override
    public String toString(){
        return "SeamProjection{" +
        "hostOrigin=" + hostOriginX + "," + hostOriginY +
        ", runtimeOrigin=" + runtimeOriginX + "," + runtimeOriginY +
        ", scale=" + scale +
        ", rotationDeg=" + rotationDeg +
        '}';
    }
}
