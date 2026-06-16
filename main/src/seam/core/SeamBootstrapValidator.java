package seam.core;

import arc.util.*;
import seam.runtime.control.*;

public final class SeamBootstrapValidator{
    private SeamBootstrapValidator(){
    }

    public static void validate(){
        Log.info("[Seam] Running bootstrap checks...");

        try{
            SeamContextSlots.validateStructure();
            SeamContextSlots.validateCurrentValues();
            Log.info("[Seam] Bootstrap validation completed successfully.");
        }catch(Throwable e){
            throw new RuntimeException("Seam unsupported on this Mindustry build: runtime global layout mismatch.", e);
        }
    }
}