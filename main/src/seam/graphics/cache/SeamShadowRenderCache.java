package seam.graphics.cache;

import arc.math.*;
import seam.graphics.invalidation.*;
import seam.graphics.view.*;
import seam.runtime.*;
import seam.runtime.control.*;
import seam.world.*;
import arc.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public final class SeamShadowRenderCache{
    private final SeamRuntime runtime;

    private final FrameBuffer shadows = new FrameBuffer();

    private final Seq<Tile> shadowEvents = new Seq<>(false, 64, Tile.class);

    private final Mat previousProjection = new Mat();
    private final Mat previousTransform = new Mat();
    private final Mat identityTransform = new Mat();

    private final Rect visibleRuntimeBounds = new Rect();
    private final Rect drawRuntimeBounds = new Rect();

    private boolean built;
    private boolean disposed;

    private int visibleShadowCount;
    private int lastViewerTeamId = -2;

    private int lastTileChanges = -1;
    private int lastFloorChanges = -1;

    public SeamShadowRenderCache(SeamRuntime runtime){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        this.runtime = runtime;
    }

    public SeamRuntime runtime(){
        return runtime;
    }

    public boolean built(){
        return built;
    }

    public int visibleShadowCount(){
        return visibleShadowCount;
    }

    public void invalidate(){
        built = false;
        shadowEvents.clear();
        visibleShadowCount = 0;
        lastViewerTeamId = -2;
        lastTileChanges = -1;
        lastFloorChanges = -1;
    }

    public void applyInvalidations(Seq<SeamRenderInvalidation> invalidations){
        if(disposed){
            throw new IllegalStateException("SeamShadowRenderCache is disposed.");
        }

        if(invalidations == null || invalidations.isEmpty()){
            return;
        }

        for(SeamRenderInvalidation invalidation : invalidations){
            if(invalidation == null){
                continue;
            }

            if(invalidation.full()
            || invalidation.has(SeamRenderInvalidationType.shadow)
            || invalidation.has(SeamRenderInvalidationType.block)
            || invalidation.has(SeamRenderInvalidationType.tile)
            || invalidation.has(SeamRenderInvalidationType.proximity)){
                invalidate();
                return;
            }
        }
    }

    public void draw(SeamView view, Rect hostBounds, Team viewerTeam){
        if(disposed){
            throw new IllegalStateException("SeamShadowRenderCache is disposed.");
        }

        if(view == null){
            throw new NullPointerException("view");
        }

        if(hostBounds == null){
            throw new NullPointerException("hostBounds");
        }

        if(view.runtimeId() != runtime.id){
            throw new IllegalArgumentException("View runtime id does not match shadow runtime.");
        }

        SeamRuntimeValidator.validateActiveContext(runtime);

        int viewerTeamId = viewerTeam == null ? -1 : viewerTeam.id;

        if(lastTileChanges != runtime.world.tileChanges || lastFloorChanges != runtime.world.floorChanges){
            built = false;
            shadowEvents.clear();
        }

        if(!built || lastViewerTeamId != viewerTeamId){
            rebuild(viewerTeam);
        }else{
            processShadows();
        }

        drawShadowBuffer(view, hostBounds);
    }

    public void rebuild(Team viewerTeam){
        if(disposed){
            throw new IllegalStateException("SeamShadowRenderCache is disposed.");
        }

        runtime.requireWorldReady();

        lastViewerTeamId = viewerTeam == null ? -1 : viewerTeam.id;
        lastTileChanges = runtime.world.tileChanges;
        lastFloorChanges = runtime.world.floorChanges;

        visibleShadowCount = 0;
        shadowEvents.clear();

        shadows.getTexture().setFilter(TextureFilter.linear, TextureFilter.linear);
        shadows.resize(runtime.world.width(), runtime.world.height());

        beginFramebufferDraw(Color.white);

        try{
            Draw.color(BlockRenderer.blendShadowColor);

            for(Tile tile : runtime.world.tiles){
                if(tile == null){
                    continue;
                }

                markInitiallyVisible(tile, viewerTeam);

                if(tile.block().displayShadow(tile) && (tile.build == null || tile.build.wasVisible)){
                    Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1f, 1f);
                    visibleShadowCount++;
                }
            }

            Draw.flush();
            Draw.color();
        }finally{
            endFramebufferDraw();
        }

        built = true;
    }

    public void processShadows(){
        if(disposed){
            throw new IllegalStateException("SeamShadowRenderCache is disposed.");
        }

        if(!built){
            rebuild(viewerTeam());
            return;
        }

        if(shadowEvents.isEmpty()){
            return;
        }

        shadows.getTexture().setFilter(TextureFilter.linear, TextureFilter.linear);
        shadows.resize(runtime.world.width(), runtime.world.height());

        beginFramebufferDraw(null);

        try{
            for(int i = 0; i < shadowEvents.size; i++){
                Tile tile = shadowEvents.get(i);

                if(tile == null){
                    continue;
                }

                boolean hiddenByFog = state.rules.fog && tile.build != null && !tile.build.wasVisible;
                boolean draw = tile.block().displayShadow(tile) && !hiddenByFog;

                Draw.color(draw ? BlockRenderer.blendShadowColor : Color.white);
                Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1f, 1f);
            }

            Draw.flush();
            Draw.color();
        }finally{
            shadowEvents.clear();
            endFramebufferDraw();
        }
    }

    public void updateShadow(Building build){
        if(build == null || build.tile == null){
            return;
        }

        int size = build.block.size;
        int offset = build.block.sizeOffset;
        int tx = build.tile.x;
        int ty = build.tile.y;

        for(int x = 0; x < size; x++){
            for(int y = 0; y < size; y++){
                Tile tile = runtime.world.tile(x + tx + offset, y + ty + offset);

                if(tile != null){
                    shadowEvents.add(tile);
                }
            }
        }
    }

    public void updateShadowTile(Tile tile){
        if(tile != null){
            shadowEvents.add(tile);
        }
    }

    public void dispose(){
        if(disposed){
            return;
        }

        disposed = true;
        built = false;
        visibleShadowCount = 0;
        shadowEvents.clear();
        shadows.dispose();
    }

    private void drawShadowBuffer(SeamView view, Rect hostBounds){
        /*
         * Keep the grow here so shadows close to the visible edge are present,
         * but never use the grown rect directly for the final FBO composite.
         * Otherwise UVs go outside [0..1] near map edges and clamp-to-edge
         * stretches boundary shadow texels outside the runtime map.
         */
        view.projection().runtimeBounds(runtime, hostBounds, Vars.tilesize * 3f, visibleRuntimeBounds);

        if(visibleRuntimeBounds.width <= 0f || visibleRuntimeBounds.height <= 0f){
            return;
        }

        if(!computeDrawBounds(visibleRuntimeBounds, drawRuntimeBounds)){
            return;
        }

        drawWithCleanStencil(
        () -> {
            Draw.color(Color.white);

            /*
             * Visual map extent, matching the half-tile FBO alignment used by vanilla:
             * tile centers are x*tilesize / y*tilesize, so the first visible tile starts
             * at -tilesize/2, not 0.
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

            Draw.fbo(
            shadows.getTexture(),
            runtime.world.width(),
            runtime.world.height(),
            Vars.tilesize,
            Vars.tilesize / 2f
            );

            Draw.shader();
        })
        );
    }

    private boolean computeDrawBounds(Rect visible, Rect out){
        float half = Vars.tilesize / 2f;

        float minX = -half;
        float minY = -half;
        float maxX = runtime.world.unitWidth() - half;
        float maxY = runtime.world.unitHeight() - half;

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
            Draw.beginStencil();
            stencil.run();

            Draw.beginStenciled();
            contents.run();
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

        if(clearColor == null){
            shadows.begin();
        }else{
            shadows.begin(clearColor);
        }

        Draw.proj().setOrtho(0f, 0f, shadows.getWidth(), shadows.getHeight());
    }

    private void endFramebufferDraw(){
        shadows.end();

        Draw.proj(previousProjection);
        Draw.trans(previousTransform);
        Draw.reset();
    }

    private void markInitiallyVisible(Tile tile, Team viewerTeam){
        if(tile == null || tile.build == null){
            return;
        }

        if(viewerTeam == null){
            tile.build.wasVisible = true;
            return;
        }

        if(tile.team() == viewerTeam || !state.rules.fog || (tile.build.visibleFlags & (1L << viewerTeam.id)) != 0L){
            tile.build.wasVisible = true;
        }
    }

    private Team viewerTeam(){
        if(Vars.player == null){
            return null;
        }

        return Vars.player.team();
    }

    @Override
    public String toString(){
        return "SeamShadowRenderCache{" +
        "runtimeId=" + runtime.id +
        ", built=" + built +
        ", visibleShadowCount=" + visibleShadowCount +
        ", pendingShadowEvents=" + shadowEvents.size +
        ", lastViewerTeamId=" + lastViewerTeamId +
        ", lastTileChanges=" + lastTileChanges +
        ", lastFloorChanges=" + lastFloorChanges +
        '}';
    }
}