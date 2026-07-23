package seam.core;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.graphics.*;

public class FakeRendererFactory{

    public static Renderer create(SubWorld subworld){
        Renderer r = Reflect.make(Renderer.class.getName());

        subworld.context.run(() -> {
            Reflect.set(r, "blocks", new BlockRenderer());
            Reflect.set(r, "fog", new FogRenderer());
            Reflect.set(r, "lights", new LightRenderer());
            Reflect.set(r, "minimap", new MinimapRenderer());
            Reflect.set(r, "overlays", new OverlayRenderer(){
                @Override public void drawTop(){}
                @Override public void drawBottom(){}
            });
            Reflect.set(r, "pixelator", new Pixelator());

            Events.fire(new ClientLoadEvent());
            Events.fire(new WorldLoadEvent());
        });

        Reflect.set(r, "effectBuffer", new FrameBuffer());
        Reflect.set(r, "clearColor", new Color(0f, 0f, 0f, 1f));
        Reflect.set(r, "camShakeOffset", new Vec2());

        r.minZoom = 1.5f;
        r.maxZoom = 6f;
        r.minZoomInGame = 0.5f;
        r.maxZoomInGame = 6f;

        if(Vars.renderer != null){
            r.envRenderers = Vars.renderer.envRenderers;
            r.customBackgrounds = Vars.renderer.customBackgrounds;
            r.fluidFrames = Vars.renderer.fluidFrames;
            r.bubbles = Vars.renderer.bubbles;
            r.splashes = Vars.renderer.splashes;
            r.planets = Vars.renderer.planets;
            r.animateShields = Vars.renderer.animateShields;
            r.animateWater = Vars.renderer.animateWater;
            r.drawWeather = Vars.renderer.drawWeather;
            r.drawStatus = Vars.renderer.drawStatus;
            r.enableEffects = Vars.renderer.enableEffects;
            r.drawDisplays = Vars.renderer.drawDisplays;
            r.drawLight = Vars.renderer.drawLight;
            r.pixelate = Vars.renderer.pixelate;
            r.showPings = Vars.renderer.showPings;
            r.showOtherBuildPlans = Vars.renderer.showOtherBuildPlans;
        }else{
            r.envRenderers = new Seq<>();
            r.customBackgrounds = new ObjectMap<>();
            r.drawWeather = true;
            r.drawDisplays = true;
            r.drawLight = true;
            r.showPings = true;
            r.showOtherBuildPlans = true;
        }

        return r;
    }
}
