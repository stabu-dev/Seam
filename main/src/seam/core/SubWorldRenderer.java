package seam.core;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;

public class SubWorldRenderer{
    public @Nullable FrameBuffer buffer;
    public final Rect bounds = new Rect();

    public void resize(int width, int height){
        if(buffer != null) buffer.dispose();
        buffer = new FrameBuffer(width, height);
        buffer.getTexture().setFilter(Texture.TextureFilter.nearest, Texture.TextureFilter.nearest);
    }

    public FrameBuffer render(SubWorld subworld, float cameraX, float cameraY, float viewportWidth, float viewportHeight){
        if(buffer == null) return null;

        if(subworld.renderer == null && !Vars.headless){
            subworld.renderer = FakeRendererFactory.create(subworld);
        }

        if(subworld.renderer == null) return null;

        subworld.context.run(() -> {
            Draw.flush();

            Camera subCamera = subworld.camera;

            subCamera.position.set(cameraX, cameraY);
            subCamera.width = viewportWidth;
            subCamera.height = viewportHeight;
            subCamera.update();

            bounds.set(cameraX - viewportWidth / 2f, cameraY - viewportHeight / 2f, viewportWidth, viewportHeight);

            buffer.begin(Color.black);
            subworld.renderer.draw();
            Draw.flush();
            buffer.end();
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
