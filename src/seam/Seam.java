package seam;

import mindustry.mod.*;
import seam.compat.*;
import seam.core.*;

/** Mod entry point. Sets up Seam's deferred-work hooks and mod compatibility checks. */
public class Seam extends Mod{
    @Override
    public void init(){
        Context.initHooks();
        Compat.install();
    }
}
