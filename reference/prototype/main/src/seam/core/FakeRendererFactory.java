package seam.core;

import arc.*;
import arc.graphics.*;
import arc.graphics.gl.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.graphics.*;

/**
 * Builds a {@link Renderer} for a subworld.
 * Sub-renderers (blocks/fog/…) are created by field initializers and register on the active event map,
 * so this must be called inside {@link SubWorldContext#run}.
 */
public class FakeRendererFactory{

    /** @param host host renderer to copy read-only visual assets from; may be null */
    public static Renderer create(@Nullable Renderer host){
        // Field initializers construct BlockRenderer etc.; constructor assigns Core.camera (restored by context).
        Renderer r = Reflect.make(Renderer.class.getName());

        // Avoid drawing host input overlays into the subworld FBO.
        Reflect.set(r, "overlays", new OverlayRenderer(){
            @Override public void drawTop(){}
            @Override public void drawBottom(){}
        });

        // Constructor already allocated an effect buffer — replace with one we fully own.
        if(r.effectBuffer != null){
            try{ r.effectBuffer.dispose(); }catch(Throwable ignored){}
        }
        Reflect.set(r, "effectBuffer", new FrameBuffer());
        Reflect.set(r, "clearColor", new Color(0f, 0f, 0f, 1f));
        Reflect.set(r, "camShakeOffset", new Vec2());

        r.minZoom = 1.5f;
        r.maxZoom = 6f;
        r.minZoomInGame = 0.5f;
        r.maxZoomInGame = 6f;

        if(host != null){
            r.envRenderers = host.envRenderers;
            r.customBackgrounds = host.customBackgrounds;
            r.fluidFrames = host.fluidFrames;
            r.bubbles = host.bubbles;
            r.splashes = host.splashes;
            r.planets = host.planets;
            r.animateShields = host.animateShields;
            r.animateWater = host.animateWater;
            r.drawWeather = host.drawWeather;
            r.drawStatus = host.drawStatus;
            r.enableEffects = host.enableEffects;
            r.drawDisplays = host.drawDisplays;
            r.drawLight = host.drawLight;
            r.pixelate = host.pixelate;
            r.showPings = host.showPings;
            r.showOtherBuildPlans = host.showOtherBuildPlans;
        }else{
            r.envRenderers = new Seq<>();
            r.customBackgrounds = new ObjectMap<>();
            r.drawWeather = true;
            r.drawDisplays = true;
            r.drawLight = true;
            r.showPings = true;
            r.showOtherBuildPlans = true;
            r.loadFluidFrames();
        }

        if(r.fluidFrames == null) r.loadFluidFrames();

        // BlockRenderer loads crack regions on ClientLoadEvent.
        Events.fire(new ClientLoadEvent());
        return r;
    }

    public static void dispose(Renderer r){
        if(r == null) return;

        disposeQuiet(r.effectBuffer);

        if(r.fog != null){
            disposeQuiet(Reflect.get(r.fog, "staticFog"));
            disposeQuiet(Reflect.get(r.fog, "dynamicFog"));
        }
        if(r.blocks != null){
            disposeQuiet(Reflect.get(r.blocks, "shadows"));
            disposeQuiet(Reflect.get(r.blocks, "dark"));
            Seq<?>[] caches = Reflect.get(r.blocks, "caches");
            if(caches != null){
                for(Seq<?> list : caches){
                    if(list == null) continue;
                    for(Object cache : list) disposeQuiet(cache);
                    list.clear();
                }
            }
        }
        if(r.pixelator != null){
            try{ Reflect.invoke(r.pixelator, "dispose"); }catch(Throwable ignored){}
        }
    }

    private static void disposeQuiet(Object o){
        if(o instanceof Disposable d){
            try{ d.dispose(); }catch(Throwable ignored){}
        }
    }
}