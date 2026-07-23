package seam.content;

import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.meta.*;
import seam.world.blocks.*;

//just for funny testing
public class SeamBlocks{
    public static SubWorldMonitor monitorBlock;

    public static void load(){
        monitorBlock = new SubWorldMonitor("subworld-monitor"){{
            requirements(Category.effect, ItemStack.with(Items.copper, 1));
            buildVisibility = BuildVisibility.shown;
            size = 5;
        }};
    }
}
