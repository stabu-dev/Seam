package seam.graphics.draw;

import seam.graphics.*;
import seam.graphics.view.*;
import seam.runtime.*;
import arc.*;
import arc.math.geom.*;
import arc.struct.*;

public final class SeamWorldDraw{
    private final SeamRuntimeRegistry runtimes;
    private final SeamViewRegistry views;
    private final SeamRenderService rendering;
    private final SeamDrawScope drawScope;

    private final SeamWorldDrawSettings settings = new SeamWorldDrawSettings();
    private final SeamWorldDrawStats lastStats = new SeamWorldDrawStats();

    private final Rect cameraBounds = new Rect();

    private final IntMap<SeamRuntimeWorldRenderer> renderers = new IntMap<>();

    public SeamWorldDraw(
    SeamRuntimeRegistry runtimes,
    SeamViewRegistry views,
    SeamRenderService rendering,
    SeamDrawScope drawScope
    ){
        if(runtimes == null){
            throw new NullPointerException("runtimes");
        }

        if(views == null){
            throw new NullPointerException("views");
        }

        if(rendering == null){
            throw new NullPointerException("rendering");
        }

        if(drawScope == null){
            throw new NullPointerException("drawScope");
        }

        this.runtimes = runtimes;
        this.views = views;
        this.rendering = rendering;
        this.drawScope = drawScope;
    }

    public SeamWorldDrawSettings settings(){
        return settings;
    }

    public SeamWorldDrawStats lastStats(){
        return lastStats.copy();
    }

    public void clear(){
        for(SeamRuntimeWorldRenderer renderer : renderers.values()){
            renderer.dispose();
        }

        renderers.clear();
        lastStats.reset();
    }

    public void removeRuntime(int runtimeId){
        SeamRuntimeWorldRenderer renderer = renderers.remove(runtimeId);

        if(renderer != null){
            renderer.dispose();
        }
    }

    public void draw(){
        if(!settings.enabled){
            return;
        }

        if(Core.camera == null){
            return;
        }

        draw(Core.camera.bounds(cameraBounds));
    }

    public void draw(Rect hostBounds){
        if(hostBounds == null){
            throw new NullPointerException("hostBounds");
        }

        lastStats.reset();
        lastStats.begin();

        try{
            Seq<SeamView> sorted = sortedViews();

            for(int i = 0; i < sorted.size; i++){
                SeamView view = sorted.get(i);

                lastStats.viewsVisited++;

                if(!view.renderable()){
                    lastStats.viewsSkipped++;
                    continue;
                }

                SeamRuntime runtime = runtimes.get(view.runtimeId());

                if(runtime == null || runtime.disposed() || !runtime.loaded() || !runtime.worldReady()){
                    lastStats.viewsSkipped++;
                    continue;
                }

                SeamRuntimeWorldRenderer renderer = renderer(runtime);

                if(renderer.render(view, hostBounds, settings, lastStats)){
                    lastStats.viewsDrawn++;
                }else{
                    lastStats.viewsSkipped++;
                }
            }
        }finally{
            lastStats.end();
        }
    }

    public SeamWorldDrawStats drawView(int viewId){
        lastStats.reset();
        lastStats.begin();

        try{
            if(settings.enabled){
                SeamView view = views.get(viewId);

                lastStats.viewsVisited++;

                if(view == null || !view.renderable()){
                    lastStats.viewsSkipped++;
                }else{
                    SeamRuntime runtime = runtimes.get(view.runtimeId());

                    if(runtime == null || runtime.disposed() || !runtime.loaded() || !runtime.worldReady() || Core.camera == null){
                        lastStats.viewsSkipped++;
                    }else{
                        SeamRuntimeWorldRenderer renderer = renderer(runtime);

                        if(renderer.render(view, Core.camera.bounds(cameraBounds), settings, lastStats)){
                            lastStats.viewsDrawn++;
                        }else{
                            lastStats.viewsSkipped++;
                        }
                    }
                }
            }
        }finally{
            lastStats.end();
        }

        return lastStats.copy();
    }

    private SeamRuntimeWorldRenderer renderer(SeamRuntime runtime){
        SeamRuntimeWorldRenderer renderer = renderers.get(runtime.id);

        if(renderer == null || renderer.runtime() != runtime){
            if(renderer != null){
                renderer.dispose();
            }

            renderer = new SeamRuntimeWorldRenderer(runtime, rendering, drawScope);
            renderers.put(runtime.id, renderer);
        }

        return renderer;
    }

    private Seq<SeamView> sortedViews(){
        Seq<SeamView> copy = views.all();

        copy.sort((a, b) -> {
            int layer = Float.compare(a.hostLayerZ(), b.hostLayerZ());

            if(layer != 0){
                return layer;
            }

            return Integer.compare(a.id(), b.id());
        });

        return copy;
    }

    @Override
    public String toString(){
        return "SeamWorldDraw{" +
        "settings=" + settings +
        ", rendererCount=" + renderers.size +
        ", lastStats=" + lastStats +
        '}';
    }
}
