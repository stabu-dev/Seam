package seam.runtime.schedule;

import arc.*;
import arc.util.*;

public final class SeamRuntimeFrameClock{
    public FrameDelta sample(){
        return delta(sampleStateDelta(), sampleTimeDelta());
    }

    public FrameDelta fixed(float delta){
        return delta(delta, delta);
    }

    public FrameDelta delta(float stateDelta, float timeDelta){
        validateDelta("stateDelta", stateDelta);
        validateDelta("timeDelta", timeDelta);

        return new FrameDelta(stateDelta, timeDelta);
    }

    private static float sampleStateDelta(){
        if(Core.graphics == null){
            return sanitizeDelta(Time.delta);
        }

        return sanitizeDelta(Core.graphics.getDeltaTime() * 60f);
    }

    private static float sampleTimeDelta(){
        return sanitizeDelta(Time.delta);
    }

    private static float sanitizeDelta(float delta){
        if(Float.isNaN(delta) || Float.isInfinite(delta)){
            return 0f;
        }

        return Math.max(delta, 0f);
    }

    private static void validateDelta(String name, float delta){
        if(Float.isNaN(delta) || Float.isInfinite(delta)){
            throw new IllegalArgumentException("Runtime frame " + name + " must be finite.");
        }

        if(delta < 0f){
            throw new IllegalArgumentException("Runtime frame " + name + " must not be negative.");
        }
    }

    public static final class FrameDelta{
        public final float stateDelta;
        public final float timeDelta;

        private FrameDelta(float stateDelta, float timeDelta){
            this.stateDelta = stateDelta;
            this.timeDelta = timeDelta;
        }
    }
}