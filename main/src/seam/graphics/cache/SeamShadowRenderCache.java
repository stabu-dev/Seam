package seam.graphics.cache;

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
import arc.math.*;
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

    private final Rect runtimeBounds = new Rect();

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

        /*
         * This is the reliable invalidation path.
         * Subworld mutations often happen with world.setGenerating(true), so vanilla TileChangeEvent is deliberately suppressed.
         * The runtime world counters are the canonical signal that the shadow buffer cannot be trusted anymore.
         */
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
        view.projection().runtimeBounds(runtime, hostBounds, Vars.tilesize * 3f, runtimeBounds);

        if(runtimeBounds.width <= 0f || runtimeBounds.height <= 0f){
            return;
        }

        float ww = runtime.world.width() * Vars.tilesize;
        float wh = runtime.world.height() * Vars.tilesize;

        float x = runtimeBounds.x + runtimeBounds.width / 2f + Vars.tilesize / 2f;
        float y = runtimeBounds.y + runtimeBounds.height / 2f + Vars.tilesize / 2f;

        float u = (x - runtimeBounds.width / 2f) / ww;
        float v = (y - runtimeBounds.height / 2f) / wh;
        float u2 = (x + runtimeBounds.width / 2f) / ww;
        float v2 = (y + runtimeBounds.height / 2f) / wh;

        Tmp.tr1.set(shadows.getTexture());
        Tmp.tr1.set(u, v2, u2, v);

        Draw.shader(Shaders.darkness);
        Draw.rect(
        Tmp.tr1,
        runtimeBounds.x + runtimeBounds.width / 2f,
        runtimeBounds.y + runtimeBounds.height / 2f,
        runtimeBounds.width,
        runtimeBounds.height
        );
        Draw.shader();
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
