package seam.world.blocks;

import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.gen.*;
import mindustry.world.*;
import seam.*;
import seam.core.*;

/** Test block that hosts and displays a small isolated subworld. */
public class SubWorldMonitor extends Block{
    public SubWorldMonitor(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
    }

    public class SubWorldMonitorBuild extends Building{
        public SubWorld subworld;
        public SubWorldRenderer view;
        public final TextureRegion region = new TextureRegion();
        public FrameBuffer frame;

        @Override
        public void created(){
            super.created();

            subworld = Seam.createWorld();
            subworld.load(block.size, block.size, tiles -> {
                int s = block.size;
                for(int x = 0; x < s; x++){
                    for(int y = 0; y < s; y++){
                        Block floor = Blocks.stone;
                        Block wall = Blocks.air;

                        float n = Simplex.noise2d(1, 2, 0.5f, 0.2f, x, y);
                        if(n > 0.6f) floor = Blocks.dirt;
                        else if(n > 0.4f) floor = Blocks.sand;

                        if(Simplex.noise2d(2, 2, 0.5f, 0.1f, x, y) > 0.7f){
                            wall = Blocks.stoneWall;
                        }

                        tiles.set(x, y, new Tile(x, y, floor, Blocks.air, wall));
                    }
                }
            });
            subworld.state.set(State.playing);

            view = new SubWorldRenderer();
            view.resize(block.size * 32, block.size * 32);

            Seam.monitors.add(this);
        }

        public void renderSubWorld(){
            if(subworld == null || view == null) return;

            float cx = (block.size - 1) * 4f;
            float cy = (block.size - 1) * 4f;
            float span = block.size * 8f;
            frame = view.render(subworld, cx, cy, span, span);
        }

        @Override
        public void draw(){
            super.draw();
            if(frame == null) return;

            Draw.color();
            region.set(frame.getTexture());
            region.set(0, 0, frame.getWidth(), frame.getHeight());
            region.flip(false, true);
            Draw.rect(region, x, y, block.size * 8f, block.size * 8f);
        }

        @Override
        public void onRemoved(){
            Seam.monitors.remove(this);
            if(subworld != null){
                Seam.removeWorld(subworld);
                subworld = null;
            }
            if(view != null){
                view.dispose();
                view = null;
            }
            frame = null;
            super.onRemoved();
        }
    }
}
