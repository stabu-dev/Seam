package seam.content;

import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.meta.*;
import seam.world.blocks.*;

public class SeamBlocks{
    public static SubWorldMonitor monitor;

    public static void load(){
        monitor = new SubWorldMonitor("subworld-monitor"){{
            requirements(Category.effect, ItemStack.with(Items.copper, 1));
            buildVisibility = BuildVisibility.shown;
            size = 5;
        }};
    }
}