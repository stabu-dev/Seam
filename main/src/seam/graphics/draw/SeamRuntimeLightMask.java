package seam.graphics.draw;

import seam.graphics.view.*;
import seam.runtime.*;
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.geom.*;
import mindustry.*;

public final class SeamRuntimeLightMask{
    private static final Blending premultiplied = new Blending(Gl.one, Gl.oneMinusSrcAlpha, Gl.one, Gl.oneMinusSrcAlpha);

    private final SeamRuntime runtime;
    private final float[] quad = new float[SpriteBatch.SPRITE_SIZE];
    private final Vec2 hostPoint = new Vec2();
    private final Vec2 screenPoint = new Vec2();

    private FrameBuffer target;
    private boolean active;
    private boolean disposed;

    public SeamRuntimeLightMask(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;
    }

    public boolean begin(SeamWorldDrawSettings settings, boolean rendererDrawLight){
        if(disposed){
            throw new IllegalStateException("SeamRuntimeLightMask is disposed.");
        }

        active = enabled(settings, rendererDrawLight);

        return active;
    }

    public boolean active(){
        return active;
    }

    public void discard(){
        active = false;
    }

    public void draw(SeamView view, SeamWorldDrawStats stats){
        if(disposed){
            throw new IllegalStateException("SeamRuntimeLightMask is disposed.");
        }

        if(view == null){
            throw new NullPointerException("view");
        }

        if(stats == null){
            throw new NullPointerException("stats");
        }

        if(!active){
            return;
        }

        if(Vars.renderer == null || Vars.renderer.lights == null){
            discard();
            return;
        }

        try{
            capture();
            composite(view);

            stats.lightPasses++;
        }catch(Throwable throwable){
            clearQueuedLights();
            throw throwable;
        }finally{
            discard();
            Draw.reset();
        }
    }

    public void clearQueuedLights(){
        if(Vars.renderer == null || Vars.renderer.lights == null){
            return;
        }

        boolean previousEnableLight = Vars.enableLight;

        try{
            /*
             * LightRenderer.draw() publicly clears its internal queues and returns early
             * when Vars.enableLight is false. This is the only no-reflection queue drain.
             */
            Vars.enableLight = false;
            Vars.renderer.lights.draw();
        }finally{
            Vars.enableLight = previousEnableLight;
            Draw.reset();
        }
    }

    public void dispose(){
        if(disposed){
            return;
        }

        disposed = true;
        active = false;

        if(target != null){
            target.dispose();
            target = null;
        }
    }

    private boolean enabled(SeamWorldDrawSettings settings, boolean rendererDrawLight){
        if(settings == null || !settings.drawLights){
            return false;
        }

        if(!Vars.enableLight || Vars.renderer == null || Vars.renderer.lights == null || !rendererDrawLight){
            return false;
        }

        if(!runtime.state.rules.lighting){
            return false;
        }

        return runtime.state.rules.ambientLight.a > 0.0001f;
    }

    private void capture(){
        FrameBuffer target = target();

        target.begin(Color.clear);

        try{
            Vars.renderer.lights.draw();
        }finally{
            target.end();
        }
    }

    private void composite(SeamView view){
        if(target == null || Core.camera == null){
            return;
        }

        float x1 = -Vars.tilesize / 2f;
        float y1 = -Vars.tilesize / 2f;
        float x2 = x1 + runtime.world.unitWidth();
        float y2 = y1 + runtime.world.unitHeight();

        Draw.color(Color.white);
        Draw.mixcol();

        float color = Draw.getColorPacked();
        float mix = Draw.getMixColorPacked();
        int width = target.getWidth();
        int height = target.getHeight();

        vertex(view, 0, x1, y1, color, mix, width, height);
        vertex(view, 6, x1, y2, color, mix, width, height);
        vertex(view, 12, x2, y2, color, mix, width, height);
        vertex(view, 18, x2, y1, color, mix, width, height);

        try{
            Draw.blend(premultiplied);
            Draw.vert(target.getTexture(), quad, 0, quad.length);
        }finally{
            Draw.blend();
            Draw.reset();
        }
    }

    private void vertex(SeamView view, int offset, float runtimeX, float runtimeY, float color, float mix, int width, int height){
        view.projection().runtimeWorldToHost(runtimeX, runtimeY, hostPoint);
        screenPoint.set(hostPoint);
        Core.camera.project(screenPoint);

        quad[offset] = runtimeX;
        quad[offset + 1] = runtimeY;
        quad[offset + 2] = color;
        quad[offset + 3] = screenPoint.x / width;
        quad[offset + 4] = screenPoint.y / height;
        quad[offset + 5] = mix;
    }

    private FrameBuffer target(){
        int width = Math.max(Core.graphics.getWidth(), 2);
        int height = Math.max(Core.graphics.getHeight(), 2);

        if(target == null){
            target = new FrameBuffer(width, height);
        }else{
            target.resize(width, height);
        }

        return target;
    }
}
