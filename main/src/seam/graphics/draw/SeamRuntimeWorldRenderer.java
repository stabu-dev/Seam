package seam.graphics.draw;

import seam.core.*;
import seam.graphics.*;
import seam.graphics.cache.*;
import seam.graphics.view.*;
import seam.runtime.*;
import seam.world.*;
import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

public final class SeamRuntimeWorldRenderer{
    private static final float screenPassEpsilon = 0.001f;
    private static final float minimumScreenPassRange = 0.0001f;

    private final SeamRuntime runtime;
    private final SeamRenderService rendering;
    private final SeamDrawScope drawScope;
    private final SeamShadowRenderCache shadows;
    private final SeamRuntimeLightMask lights;
    private final SeamRuntimeDarknessMask darkness;

    private final Rect entityRuntimeBounds = new Rect();
    private final Rect weatherCameraBounds = new Rect();
    private final ObjectSet<Drawc> drawnDrawEntities = new ObjectSet<>();

    private boolean loggedScreenPassFailure;

    public SeamRuntimeWorldRenderer(SeamRuntime runtime, SeamRenderService rendering, SeamDrawScope drawScope){
        if(runtime == null){
            throw new NullPointerException("runtime");
        }

        if(rendering == null){
            throw new NullPointerException("rendering");
        }

        if(drawScope == null){
            throw new NullPointerException("drawScope");
        }

        this.runtime = runtime;
        this.rendering = rendering;
        this.drawScope = drawScope;
        this.shadows = new SeamShadowRenderCache(runtime);
        this.lights = new SeamRuntimeLightMask(runtime);
        this.darkness = new SeamRuntimeDarknessMask(runtime);
    }

    public SeamRuntime runtime(){
        return runtime;
    }

    public SeamShadowRenderCache shadows(){
        return shadows;
    }

    public void dispose(){
        shadows.dispose();
        lights.dispose();
        darkness.dispose();
    }

    public boolean render(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(view == null){
            throw new NullPointerException("view");
        }

        if(hostBounds == null){
            throw new NullPointerException("hostBounds");
        }

        if(settings == null){
            throw new NullPointerException("settings");
        }

        if(stats == null){
            throw new NullPointerException("stats");
        }

        if(view.runtimeId() != runtime.id){
            return false;
        }

        SeamRenderViewBatch batch = rendering.queryView(view.id(), hostBounds);

        if(!batch.success){
            stats.batchesFailed++;
            return false;
        }

        shadows.applyInvalidations(rendering.lastInvalidations(runtime.id));

        Draw.draw(view.hostLayerZ(), () -> renderIsolated(view, hostBounds, batch, settings, stats));

        return true;
    }

    private void renderIsolated(
    SeamView view,
    Rect hostBounds,
    SeamRenderViewBatch batch,
    SeamWorldDrawSettings settings,
    SeamWorldDrawStats stats
    ){
        boolean localLightsActive = renderWorldPass(view, hostBounds, batch, settings, stats);

        if(localLightsActive){
            renderLightPass(view, stats);
        }
    }

    private boolean renderWorldPass(
    SeamView view,
    Rect hostBounds,
    SeamRenderViewBatch batch,
    SeamWorldDrawSettings settings,
    SeamWorldDrawStats stats
    ){
        boolean restoreDrawLight = false;
        boolean previousDrawLight = false;
        boolean localLightsActive = false;

        Throwable failure = null;

        drawScope.beginIsolated(runtime, view, SeamPhase.renderWorld);

        try{
            if(Vars.renderer != null){
                previousDrawLight = Vars.renderer.drawLight;
                restoreDrawLight = true;

                localLightsActive = lights.begin(settings, previousDrawLight);

                Vars.renderer.drawLight = localLightsActive;
            }

            scheduleRuntimeScreenPasses(settings, stats);
            scheduleFloor(view, hostBounds, settings, stats);
            scheduleWallLayer(view, hostBounds, settings, stats);
            scheduleStaticShadows(view, hostBounds, settings, stats);

            if(settings.drawBlocks){
                drawBlocksVanilla(batch, settings, stats);
            }

            if(localLightsActive){
                queueRuntimeTileLights(batch, stats);
            }

            if(settings.drawDrawEntities){
                drawDrawEntities(view, hostBounds, batch, settings, stats);
            }

            scheduleRuntimeDarkness(view, batch, settings, stats);

            stats.isolatedBatchesDrawn++;
        }catch(Throwable throwable){
            failure = throwable;
        }finally{
            try{
                drawScope.endIsolated();
            }catch(Throwable throwable){
                if(failure == null){
                    failure = throwable;
                }
            }

            if(restoreDrawLight && Vars.renderer != null){
                Vars.renderer.drawLight = previousDrawLight;
            }
        }

        if(failure != null){
            if(localLightsActive){
                lights.clearQueuedLights();
            }

            lights.discard();

            rethrow(failure);
        }

        return localLightsActive;
    }

    private void renderLightPass(SeamView view, SeamWorldDrawStats stats){
        Throwable failure = null;
        boolean began = false;

        try{
            drawScope.beginIsolated(runtime, view, SeamPhase.renderWorld);
            began = true;

            lights.draw(view, stats);
        }catch(Throwable throwable){
            failure = throwable;

            if(lights.active()){
                lights.clearQueuedLights();
            }
        }finally{
            if(began){
                try{
                    drawScope.endIsolated();
                }catch(Throwable throwable){
                    if(failure == null){
                        failure = throwable;
                    }
                }
            }

            lights.discard();
        }

        rethrow(failure);
    }

    private void rethrow(Throwable throwable){
        if(throwable == null){
            return;
        }

        if(throwable instanceof RuntimeException runtimeException){
            throw runtimeException;
        }

        throw new RuntimeException(throwable);
    }

    private void scheduleRuntimeScreenPasses(SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(Vars.renderer == null){
            return;
        }

        boolean shieldEnabled =
        settings.drawAnimatedShields
        && Vars.renderer.animateShields
        && Shaders.shield != null
        && Vars.renderer.effectBuffer != null;

        boolean buildBeamEnabled =
        settings.drawAnimatedBuildBeams
        && Shaders.buildBeam != null
        && Vars.renderer.effectBuffer != null;

        float requestedRange = sanitizeScreenShaderRange(settings.screenShaderRange);
        float shieldRange = requestedRange;
        float buildBeamRange = requestedRange;

        if(shieldEnabled && buildBeamEnabled){
            float gap = Math.abs(Layer.buildBeam - Layer.shields);

            if(gap <= screenPassEpsilon * 2f){
                buildBeamEnabled = false;
            }else{
                float safeRange = Math.max(minimumScreenPassRange, gap / 2f - screenPassEpsilon);

                shieldRange = Math.min(shieldRange, safeRange);
                buildBeamRange = Math.min(buildBeamRange, safeRange);
            }
        }

        float firstEffectBufferStart = Float.POSITIVE_INFINITY;

        if(shieldEnabled){
            firstEffectBufferStart = Math.min(firstEffectBufferStart, Layer.shields - shieldRange);
        }

        if(buildBeamEnabled){
            firstEffectBufferStart = Math.min(firstEffectBufferStart, Layer.buildBeam - buildBeamRange);
        }

        if(settings.drawBloom && Vars.renderer.bloom != null){
            float bloomStart = Layer.bullet - 0.02f;
            float bloomEnd = Layer.effect + 0.02f;

            if(Float.isFinite(firstEffectBufferStart)){
                bloomEnd = Math.min(bloomEnd, firstEffectBufferStart - screenPassEpsilon);
            }

            scheduleBloomPass(bloomStart, bloomEnd, stats);
        }

        if(shieldEnabled){
            scheduleEffectBufferPass(
            Layer.shields,
            shieldRange,
            Shaders.shield,
            "animated-shields",
            () -> stats.animatedShieldPasses++
            );
        }

        if(buildBeamEnabled){
            scheduleEffectBufferPass(
            Layer.buildBeam,
            buildBeamRange,
            Shaders.buildBeam,
            "animated-build-beams",
            () -> stats.animatedBuildBeamPasses++
            );
        }
    }

    private float sanitizeScreenShaderRange(float range){
        if(!Float.isFinite(range)){
            return 1f;
        }

        return Math.max(minimumScreenPassRange, Math.abs(range));
    }

    private void scheduleBloomPass(float startLayer, float endLayer, SeamWorldDrawStats stats){
        if(endLayer <= startLayer + screenPassEpsilon){
            return;
        }

        ScreenPassState state = new ScreenPassState("bloom");

        Draw.draw(startLayer, () -> beginBloomCapture(state, stats));
        Draw.draw(endLayer, () -> endBloomCapture(state, stats));
    }

    private void beginBloomCapture(ScreenPassState state, SeamWorldDrawStats stats){
        state.active = false;

        if(Vars.renderer == null || Vars.renderer.bloom == null){
            return;
        }

        try{
            Vars.renderer.bloom.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
            Vars.renderer.bloom.setBloomIntensity(Core.settings.getInt("bloomintensity", 6) / 4f + 1f);
            Vars.renderer.bloom.blurPasses = Core.settings.getInt("bloomblur", 1);
            Vars.renderer.bloom.capture();

            state.active = true;
            stats.bloomCaptures++;
        }catch(Throwable throwable){
            state.active = false;
            logScreenPassFailure(state, throwable);
        }
    }

    private void endBloomCapture(ScreenPassState state, SeamWorldDrawStats stats){
        if(!state.active){
            return;
        }

        try{
            Vars.renderer.bloom.render();

            stats.bloomRenders++;
        }catch(Throwable throwable){
            logScreenPassFailure(state, throwable);
        }finally{
            state.active = false;
            Draw.reset();
        }
    }

    private void scheduleEffectBufferPass(float layer, float range, Shader shader, String name, Runnable success){
        if(shader == null || Vars.renderer == null || Vars.renderer.effectBuffer == null){
            return;
        }

        ScreenPassState state = new ScreenPassState(name);

        Draw.drawRange(
        layer,
        range,
        () -> beginEffectBufferPass(state),
        () -> endEffectBufferPass(state, shader, success)
        );
    }

    private void beginEffectBufferPass(ScreenPassState state){
        state.active = false;

        if(Vars.renderer == null || Vars.renderer.effectBuffer == null){
            return;
        }

        try{
            Vars.renderer.effectBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
            Vars.renderer.effectBuffer.begin(Color.clear);

            state.active = true;
        }catch(Throwable throwable){
            state.active = false;
            logScreenPassFailure(state, throwable);
        }
    }

    private void endEffectBufferPass(ScreenPassState state, Shader shader, Runnable success){
        if(!state.active){
            return;
        }

        try{
            Vars.renderer.effectBuffer.end();
            Vars.renderer.effectBuffer.blit(shader);

            if(success != null){
                success.run();
            }
        }catch(Throwable throwable){
            logScreenPassFailure(state, throwable);
        }finally{
            state.active = false;
            Draw.reset();
        }
    }

    private void logScreenPassFailure(ScreenPassState state, Throwable throwable){
        if(loggedScreenPassFailure){
            return;
        }

        loggedScreenPassFailure = true;

        Log.err("[Seam] Runtime screen pass failed: " + (state == null ? "unknown" : state.name));
        Log.err(throwable);
    }

    private void scheduleFloor(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(!settings.drawFloors){
            return;
        }

        Draw.draw(Layer.floor, () -> {
            SeamFloorDrawResult result = rendering.drawFloor(view.id(), hostBounds);
            stats.addFloorResult(result);
        });
    }

    private void scheduleStaticShadows(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(!settings.drawStaticShadows){
            return;
        }

        Draw.draw(Layer.block - 1f, () -> {
            shadows.draw(view, hostBounds, viewerTeam());

            stats.shadowMasksPrepared++;
            stats.staticShadowsDrawn += shadows.visibleShadowCount();
        });
    }

    private void scheduleWallLayer(SeamView view, Rect hostBounds, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        if(!settings.drawFloors){
            return;
        }

        Draw.draw(Layer.block - 0.09f, () -> {
            SeamFloorDrawResult result = rendering.drawWallLayer(view.id(), hostBounds);
            stats.addFloorResult(result);
        });
    }

    private void scheduleRuntimeDarkness(
    SeamView view,
    SeamRenderViewBatch batch,
    SeamWorldDrawSettings settings,
    SeamWorldDrawStats stats
    ){
        if(!settings.drawDarkness){
            return;
        }

        Draw.draw(Layer.darkness, () -> darkness.draw(view, batch, settings, stats));
    }

    private void queueRuntimeTileLights(SeamRenderViewBatch batch, SeamWorldDrawStats stats){
        if(batch == null || batch.lightCount() <= 0 || !lights.active()){
            return;
        }

        for(int i = 0; i < batch.lights.size; i++){
            Tile tile = batch.lights.items[i];

            if(tile == null){
                continue;
            }

            queueTileLight(tile, stats);
        }
    }

    private void queueTileLight(Tile tile, SeamWorldDrawStats stats){
        Block block = tile.block();
        Building build = tile.build;

        if(build != null){
            build.drawLight();
            stats.staticLightsQueued++;
        }else if(block != null && block.emitLight){
            block.drawEnvironmentLight(tile);
            stats.staticLightsQueued++;
        }

        if(block != null && !block.obstructsLight){
            Floor floor = tile.floor();
            Floor overlay = tile.overlay();

            if(floor != null && overlay != null && !floor.obstructsLight && overlay.emitLight){
                overlay.drawEnvironmentLight(tile);
                stats.staticLightsQueued++;
            }

            if(floor != null && (floor.forceDrawLight || ((overlay == null || !overlay.obstructsLight) && floor.emitLight))){
                floor.drawEnvironmentLight(tile);
                stats.staticLightsQueued++;
            }
        }
    }

    private void drawBlocksVanilla(SeamRenderViewBatch batch, SeamWorldDrawSettings settings, SeamWorldDrawStats stats){
        Team pteam = viewerTeam();

        for(int i = 0; i < batch.tiles.size; i++){
            Tile tile = batch.tiles.items[i];

            if(tile == null){
                continue;
            }

            stats.tilesVisited++;

            Block block = tile.block();
            Building build = tile.build;

            if(block == null || block == Blocks.air){
                continue;
            }

            Draw.z(Layer.block);

            boolean visible = build == null || pteam == null || !build.inFogTo(pteam);
            boolean wasVisible = build != null && build.wasVisible;

            if(!visible && !wasVisible){
                continue;
            }

            block.drawBase(tile);

            stats.blocksDrawn++;

            Draw.reset();
            Draw.z(Layer.block);

            if(settings.drawCustomShadows && block.customShadow){
                Draw.z(Layer.block - 1f);
                block.drawShadow(tile);
                Draw.z(Layer.block);

                stats.customShadowsDrawn++;
            }

            if(build != null){
                if(visible){
                    if(pteam != null && settings.updateVisibilityFlags){
                        build.visibleFlags |= 1L << pteam.id;
                    }

                    if(!build.wasVisible){
                        build.wasVisible = true;
                        shadows.updateShadow(build);
                    }
                }

                if(settings.drawCracks && build.damaged()){
                    Draw.z(Layer.blockCracks);
                    build.drawCracks();
                    Draw.z(Layer.block);

                    stats.cracksDrawn++;
                }

                if(settings.drawTeamOverlays && pteam != null && build.team != pteam){
                    if(build.block.drawTeamOverlay){
                        build.drawTeam();
                        Draw.z(Layer.block);

                        stats.teamOverlaysDrawn++;
                    }
                }else if(settings.drawStatus && shouldDrawStatus(block, settings)){
                    build.drawStatus();

                    stats.statusDrawn++;
                }
            }

            Draw.reset();
        }
    }

    private void drawDrawEntities(
    SeamView view,
    Rect hostBounds,
    SeamRenderViewBatch batch,
    SeamWorldDrawSettings settings,
    SeamWorldDrawStats stats
    ){
        view.projection().runtimeBounds(runtime, hostBounds, Vars.tilesize * 8f, entityRuntimeBounds);

        drawnDrawEntities.clear();

        if(settings.drawWeather){
            drawWeatherEntities(batch.runtimeBounds, stats);
        }

        drawDrawGroup(Groups.puddle, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.fire, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.bullet, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.unit, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.label, entityRuntimeBounds, stats);
        drawDrawGroup(Groups.draw, entityRuntimeBounds, stats);

        drawnDrawEntities.clear();
    }

    private void drawWeatherEntities(Rect runtimeBounds, SeamWorldDrawStats stats){
        if(Groups.weather == null || Vars.renderer == null){
            return;
        }

        if(Vars.renderer.weatherAlpha <= 0.0001f || !Vars.renderer.drawWeather || !Core.settings.getBool("showweather")){
            return;
        }

        weatherCameraBounds.set(runtimeBounds);

        if(weatherCameraBounds.width <= 0f || weatherCameraBounds.height <= 0f){
            weatherCameraBounds.set(0f, 0f, runtime.world.unitWidth(), runtime.world.unitHeight());
        }

        for(WeatherState weather : Groups.weather){
            if(weather == null || weather.weather() == null){
                continue;
            }

            if(!drawnDrawEntities.add(weather)){
                stats.drawEntityDuplicatesSkipped++;
                continue;
            }

            scheduleWeatherState(weather, weatherCameraBounds, stats);
        }
    }

    private void scheduleWeatherState(WeatherState state, Rect runtimeBounds, SeamWorldDrawStats stats){
        stats.weatherDrawn++;

        Draw.draw(Layer.debris, () -> withRuntimeCamera(runtimeBounds, () -> {
            Draw.alpha(Vars.renderer.weatherAlpha * state.opacity() * state.weather().opacityMultiplier);
            state.weather().drawUnder(state);
            Draw.reset();
        }));

        Draw.draw(Layer.weather, () -> withRuntimeCamera(runtimeBounds, () -> {
            Draw.alpha(Vars.renderer.weatherAlpha * state.opacity() * state.weather().opacityMultiplier);
            state.weather().drawOver(state);
            Draw.reset();
        }));
    }

    private void withRuntimeCamera(Rect runtimeBounds, Runnable draw){
        Camera camera = Core.camera;

        if(camera == null || draw == null){
            return;
        }

        float previousX = camera.position.x;
        float previousY = camera.position.y;
        float previousWidth = camera.width;
        float previousHeight = camera.height;

        try{
            float width = Math.max(runtimeBounds.width, Vars.tilesize);
            float height = Math.max(runtimeBounds.height, Vars.tilesize);

            camera.position.set(runtimeBounds.x + width / 2f, runtimeBounds.y + height / 2f);
            camera.width = width;
            camera.height = height;
            camera.update();

            draw.run();
        }finally{
            camera.position.set(previousX, previousY);
            camera.width = previousWidth;
            camera.height = previousHeight;
            camera.update();
        }
    }

    private void drawDrawGroup(Iterable<?> group, Rect bounds, SeamWorldDrawStats stats){
        if(group == null){
            return;
        }

        for(Object object : group){
            if(!(object instanceof Drawc draw)){
                continue;
            }

            if(!drawnDrawEntities.add(draw)){
                stats.drawEntityDuplicatesSkipped++;
                continue;
            }

            float clip = draw.clipSize();

            if(!Float.isFinite(clip) || clip <= 0f){
                clip = Vars.tilesize * 2f;
            }

            if(bounds.overlaps(draw.x() - clip / 2f, draw.y() - clip / 2f, clip, clip)){
                draw.draw();
                stats.drawEntitiesQueued++;
            }
        }
    }

    private boolean shouldDrawStatus(Block block, SeamWorldDrawSettings settings){
        if(block == null || !block.hasConsumers){
            return false;
        }

        if(!settings.respectVanillaStatusToggle){
            return true;
        }

        return Vars.renderer != null && Vars.renderer.drawStatus;
    }

    private Team viewerTeam(){
        if(Vars.player == null){
            return null;
        }

        return Vars.player.team();
    }

    private static final class ScreenPassState{
        final String name;
        boolean active;

        ScreenPassState(String name){
            this.name = name;
        }
    }

    @Override
    public String toString(){
        return "SeamRuntimeWorldRenderer{" +
        "runtimeId=" + runtime.id +
        ", shadows=" + shadows +
        ", darkness=" + darkness +
        '}';
    }
}
