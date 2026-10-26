package seam.core;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.util.*;
import mindustry.*;

/** Renders a {@link SubWorld} into an offscreen buffer. */
public class SubWorldRenderer{
    public @Nullable FrameBuffer buffer;

    public void resize(int width, int height){
        if(buffer != null){
            buffer.dispose();
            buffer = null;
        }
        buffer = new FrameBuffer(width, height);
        buffer.getTexture().setFilter(Texture.TextureFilter.nearest, Texture.TextureFilter.nearest);
    }

    public @Nullable FrameBuffer render(SubWorld sub, float camX, float camY, float viewW, float viewH){
        if(buffer == null || sub.renderer == null || Vars.headless) return null;

        if(sub.world.width() <= 0 || sub.world.height() <= 0){
            buffer.begin(Color.black);
            buffer.end();
            return buffer;
        }

        sub.context.run(() -> {
            Draw.flush();

            Camera cam = sub.camera;
            cam.position.set(camX, camY);
            cam.width = viewW;
            cam.height = viewH;
            cam.update();

            buffer.begin(Color.black);
            try{
                sub.renderer.draw();
                Draw.flush();
            }finally{
                buffer.end();
            }
        });

        return buffer;
    }

    public void dispose(){
        if(buffer != null){
            buffer.dispose();
            buffer = null;
        }
    }
}
