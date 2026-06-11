package seam.graphics.view;

import seam.runtime.*;
import seam.world.*;
import arc.math.geom.*;
import mindustry.graphics.*;

public final class SeamView{
    private final int id;
    private final int runtimeId;

    private String name;
    private SeamProjection projection;

    private boolean visible = true;
    private boolean renderable = true;
    private boolean pickable = true;

    private int inputPriority;
    private float renderLayer;

    private float zBase = Layer.end + 10f;
    private float zScale = 1f;

    public SeamView(int id, int runtimeId, String name, SeamProjection projection){
        if(id < 0){
            throw new IllegalArgumentException("View id must be >= 0.");
        }

        if(runtimeId < 0){
            throw new IllegalArgumentException("Runtime id must be >= 0.");
        }

        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("View name cannot be blank.");
        }

        if(projection == null){
            throw new NullPointerException("projection");
        }

        this.id = id;
        this.runtimeId = runtimeId;
        this.name = name;
        this.projection = projection;
    }

    public int id(){
        return id;
    }

    public int runtimeId(){
        return runtimeId;
    }

    public String name(){
        return name;
    }

    public SeamView name(String name){
        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("View name cannot be blank.");
        }

        this.name = name;

        return this;
    }

    public SeamProjection projection(){
        return projection;
    }

    public SeamView projection(SeamProjection projection){
        if(projection == null){
            throw new NullPointerException("projection");
        }

        this.projection = projection;

        return this;
    }

    public boolean visible(){
        return visible;
    }

    public SeamView visible(boolean visible){
        this.visible = visible;

        return this;
    }

    public boolean renderable(){
        return renderable && visible;
    }

    public SeamView renderable(boolean renderable){
        this.renderable = renderable;

        return this;
    }

    public boolean pickable(){
        return pickable && visible;
    }

    public SeamView pickable(boolean pickable){
        this.pickable = pickable;

        return this;
    }

    public int inputPriority(){
        return inputPriority;
    }

    public SeamView inputPriority(int inputPriority){
        this.inputPriority = inputPriority;

        return this;
    }

    public float renderLayer(){
        return renderLayer;
    }

    public SeamView renderLayer(float renderLayer){
        if(!Float.isFinite(renderLayer)){
            throw new IllegalArgumentException("Render layer must be finite.");
        }

        this.renderLayer = renderLayer;

        return this;
    }

    public float zBase(){
        return zBase;
    }

    public SeamView zBase(float zBase){
        if(!Float.isFinite(zBase)){
            throw new IllegalArgumentException("Z base must be finite.");
        }

        this.zBase = zBase;

        return this;
    }

    public float zScale(){
        return zScale;
    }

    public SeamView zScale(float zScale){
        if(!Float.isFinite(zScale)){
            throw new IllegalArgumentException("Z scale must be finite.");
        }

        if(zScale <= 0f){
            throw new IllegalArgumentException("Z scale must be positive.");
        }

        this.zScale = zScale;

        return this;
    }

    public float zRange(){
        return (Layer.max - Layer.min) * zScale;
    }

    public SeamView zRange(float zRange){
        if(!Float.isFinite(zRange)){
            throw new IllegalArgumentException("Z range must be finite.");
        }

        if(zRange <= 0f){
            throw new IllegalArgumentException("Z range must be positive.");
        }

        this.zScale = zRange / (Layer.max - Layer.min);

        return this;
    }

    public float hostLayerZ(){
        return zBase + renderLayer;
    }

    public float zWindowBase(){
        return hostZ(Layer.min);
    }

    public float zWindowEnd(){
        return hostZ(Layer.max);
    }

    public float hostZ(float internalZ){
        if(!Float.isFinite(internalZ)){
            throw new IllegalArgumentException("Internal Z must be finite.");
        }

        return zBase + renderLayer + internalZ * zScale;
    }

    public Rect hostBounds(SeamRuntime runtime, Rect out){
        return projection.hostBounds(runtime, out);
    }

    @Override
    public String toString(){
        return "SeamView{" +
        "id=" + id +
        ", runtimeId=" + runtimeId +
        ", name='" + name + '\'' +
        ", projection=" + projection +
        ", visible=" + visible +
        ", renderable=" + renderable +
        ", pickable=" + pickable +
        ", inputPriority=" + inputPriority +
        ", renderLayer=" + renderLayer +
        ", zBase=" + zBase +
        ", zScale=" + zScale +
        ", hostLayerZ=" + hostLayerZ() +
        ", zWindow=" + zWindowBase() + "->" + zWindowEnd() +
        '}';
    }
}