package seam.graphics.draw;

import arc.math.*;
import seam.graphics.view.*;
import seam.runtime.*;
import arc.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.graphics.*;
import mindustry.world.*;

public final class SeamRuntimeDarknessMask{
    private final SeamRuntime runtime;
    private final FrameBuffer dark = new FrameBuffer();

    private final Mat previousProjection = new Mat();
    private final Mat previousTransform = new Mat();
    private final Mat identityTransform = new Mat();

    private final Rect drawRuntimeBounds = new Rect();

    private boolean built;
    private boolean disposed;

    private int lastTileChanges = -1;
    private int lastFloorChanges = -1;
    private int tilesDrawn;

    public SeamRuntimeDarknessMask(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;
    }

    public void invalidate(){
        built = false;
        lastTileChanges = -1;
        lastFloorChanges = -1;
        tilesDrawn = 0;
    }

    public int tilesDrawn(){
        return tilesDrawn;
    }

    public void draw(SeamView view, SeamRenderViewBatch batch, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(disposed){
            throw new IllegalStateException("SeamRuntimeDarknessMask is disposed.");
        }

        if(view == null){
            throw new NullPointerException("view");
        }

        if(batch == null){
            throw new NullPointerException("batch");
        }

        if(settings == null){
            throw new NullPointerException("settings");
        }

        if(stats == null){
            throw new NullPointerException("stats");
        }

        if(!settings.drawDarkness || !Vars.enableDarkness || Shaders.darkness == null){
            return;
        }

        if(lastTileChanges != runtime.world.tileChanges || lastFloorChanges != runtime.world.floorChanges){
            built = false;
        }

        if(!built){
            rebuild(stats);
        }

        if(tilesDrawn <= 0 && !runtime.state.rules.limitMapArea){
            return;
        }

        drawDarknessBuffer(batch.runtimeBounds, stats);
    }

    public void dispose(){
        if(disposed){
            return;
        }

        disposed = true;
        built = false;
        tilesDrawn = 0;
        dark.dispose();
    }

    private void rebuild(SeamWorldDrawStats stats){
        runtime.requireWorldReady();

        runtime.world.addDarkness(runtime.world.tiles);

        lastTileChanges = runtime.world.tileChanges;
        lastFloorChanges = runtime.world.floorChanges;
        tilesDrawn = 0;

        dark.getTexture().setFilter(TextureFilter.linear);
        dark.resize(runtime.world.width(), runtime.world.height());

        beginFramebufferDraw(runtime.state.rules.limitMapArea ? Color.black : Color.white);

        try{
            if(runtime.state.rules.limitMapArea){
                Draw.color(Color.white);
                Fill.crect(
                runtime.state.rules.limitX,
                runtime.state.rules.limitY,
                runtime.state.rules.limitWidth,
                runtime.state.rules.limitHeight
                );
            }

            for(Tile tile : runtime.world.tiles){
                if(tile == null){
                    continue;
                }

                if(runtime.state.rules.limitMapArea && !Rect.contains(
                runtime.state.rules.limitX,
                runtime.state.rules.limitY,
                runtime.state.rules.limitWidth - 1,
                runtime.state.rules.limitHeight - 1,
                tile.x,
                tile.y
                )){
                    continue;
                }

                float darkness = runtime.world.getDarkness(tile.x, tile.y);

                if(darkness <= 0f){
                    continue;
                }

                float lightness = 1f - Math.min((darkness + 0.5f) / 4f, 1f);

                Draw.colorl(lightness);
                Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1f, 1f);

                tilesDrawn++;
            }

            Draw.flush();
            Draw.color();
        }finally{
            endFramebufferDraw();
        }

        built = true;

        stats.darknessTilesDrawn += tilesDrawn;

        if(tilesDrawn > 0 || runtime.state.rules.limitMapArea){
            stats.darknessPasses++;
        }
    }

    private void drawDarknessBuffer(Rect visibleRuntimeBounds, SeamWorldDrawStats stats){
        if(visibleRuntimeBounds == null || visibleRuntimeBounds.width <= 0f || visibleRuntimeBounds.height <= 0f){
            return;
        }

        if(!computeDrawBounds(visibleRuntimeBounds, drawRuntimeBounds)){
            return;
        }

        drawWithCleanStencil(
        () -> {
            Draw.color(Color.white);

            /*
             * Full tile-grid visual extent.
             * Tile centers are at x*tilesize/y*tilesize; the first tile begins visually
             * at -tilesize/2, same alignment as vanilla Draw.fbo(..., tilesize/2f).
             */
            Fill.crect(
            -Vars.tilesize / 2f,
            -Vars.tilesize / 2f,
            runtime.world.unitWidth(),
            runtime.world.unitHeight()
            );

            Draw.color();
        },
        () -> withRuntimeCamera(drawRuntimeBounds, () -> {
            Draw.shader(Shaders.darkness);

            /*
             * Arc v158.1 only has the offset overload for Texture, not FrameBuffer.
             */
            Draw.fbo(
            dark.getTexture(),
            runtime.world.width(),
            runtime.world.height(),
            Vars.tilesize,
            Vars.tilesize / 2f
            );

            Draw.shader();
        })
        );

        stats.darknessDraws++;
    }

    private boolean computeDrawBounds(Rect visible, Rect out){
        float half = Vars.tilesize / 2f;

        float minX = -half;
        float minY = -half;
        float maxX = runtime.world.unitWidth() - half;
        float maxY = runtime.world.unitHeight() - half;

        /*
         * Grow visible bounds so edge tiles are sampled using the same half-tile
         * texture offset as vanilla. Then clamp to the real visual map extent.
         */
        float x1 = Math.max(minX, visible.x - half);
        float y1 = Math.max(minY, visible.y - half);
        float x2 = Math.min(maxX, visible.x + visible.width + half);
        float y2 = Math.min(maxY, visible.y + visible.height + half);

        if(x2 <= x1 || y2 <= y1){
            return false;
        }

        out.set(x1, y1, x2 - x1, y2 - y1);
        return true;
    }

    private void withRuntimeCamera(Rect bounds, Runnable draw){
        Camera camera = Core.camera;

        if(camera == null || draw == null){
            return;
        }

        float previousX = camera.position.x;
        float previousY = camera.position.y;
        float previousWidth = camera.width;
        float previousHeight = camera.height;

        try{
            camera.position.set(bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f);
            camera.width = bounds.width;
            camera.height = bounds.height;
            camera.update();

            draw.run();
        }finally{
            camera.position.set(previousX, previousY);
            camera.width = previousWidth;
            camera.height = previousHeight;
            camera.update();
        }
    }

    private void drawWithCleanStencil(Runnable stencil, Runnable contents){
        clearStencil();

        try{
            Draw.stencil(stencil, contents);
        }finally{
            Draw.endStencil();
            clearStencil();
        }
    }

    private void clearStencil(){
        Draw.flush();

        Gl.stencilMask(0xFF);
        Gl.clearStencil(0);
        Gl.clear(Gl.stencilBufferBit);
        Gl.disable(Gl.stencilTest);
    }

    private void beginFramebufferDraw(Color clearColor){
        Draw.flush();

        previousProjection.set(Draw.proj());
        previousTransform.set(Draw.trans());
        identityTransform.idt();

        Draw.trans(identityTransform);

        dark.begin(clearColor);
        Draw.proj().setOrtho(0f, 0f, dark.getWidth(), dark.getHeight());
    }

    private void endFramebufferDraw(){
        dark.end();

        Draw.proj(previousProjection);
        Draw.trans(previousTransform);
        Draw.reset();
    }

    @Override
    public String toString(){
        return "SeamRuntimeDarknessMask{" +
        "runtimeId=" + runtime.id +
        ", built=" + built +
        ", tilesDrawn=" + tilesDrawn +
        ", lastTileChanges=" + lastTileChanges +
        ", lastFloorChanges=" + lastFloorChanges +
        '}';
    }
}