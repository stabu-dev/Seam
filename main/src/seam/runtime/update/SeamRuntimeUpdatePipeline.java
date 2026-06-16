package seam.runtime.update;

import mindustry.*;
import mindustry.entities.*;
import seam.runtime.*;

public final class SeamRuntimeUpdatePipeline{
    private final SeamRuntimeUpdateTransaction update;

    public SeamRuntimeUpdatePipeline(SeamRuntimeUpdateTransaction update){
        if(update == null){
            throw new NullPointerException("update");
        }

        this.update = update;
    }

    public void stepClock(SeamRuntime runtime){
        stepClock(runtime, 1f);
    }

    public void stepClock(SeamRuntime runtime, float delta){
        validateDelta(delta);
        update.run(runtime, active -> stepClockRaw(active, delta));
    }

    public void stepClock(SeamRuntime runtime, int amount, float delta){
        if(amount < 0){
            throw new IllegalArgumentException("Clock step amount must not be negative.");
        }

        validateDelta(delta);

        update.run(runtime, active -> {
            for(int i = 0; i < amount; i++){
                stepClockRaw(active, delta);
            }
        });
    }

    public void stepSecondaryEntities(SeamRuntime runtime){
        update.run(runtime, active -> {
            Step.fire.run(active);
            Step.puddle.run(active);
            Step.weather.run(active);
            Step.label.run(active);
            Step.powerGraph.run(active);
        });
    }

    public void stepPrimaryEntities(SeamRuntime runtime){
        update.run(runtime, active -> {
            Step.player.run(active);
            Step.bullet.run(active);
            Step.unit.run(active);
            Step.build.run(active);
            Step.sync.run(active);
            Step.draw.run(active);
        });
    }

    public void stepBuilds(SeamRuntime runtime){
        run(runtime, Step.build);
    }

    public void stepBullets(SeamRuntime runtime){
        run(runtime, Step.bullet);
    }

    public void stepUnits(SeamRuntime runtime){
        run(runtime, Step.unit);
    }

    public void stepPowerGraph(SeamRuntime runtime){
        run(runtime, Step.powerGraph);
    }

    public void stepAsync(SeamRuntime runtime){
        update.run(runtime, active -> {
            if(Vars.asyncCore != null){
                Vars.asyncCore.begin();
                Vars.asyncCore.end();
            }
        });
    }

    public void stepGameplayBase(SeamRuntime runtime, float delta){
        validateDelta(delta);

        update.run(runtime, active -> {
            stepClockRaw(active, delta);

            Step.player.run(active);
            Step.bullet.run(active);
            Step.unit.run(active);
            Step.build.run(active);
            Step.sync.run(active);
            Step.draw.run(active);

            Step.fire.run(active);
            Step.puddle.run(active);
            Step.weather.run(active);
            Step.label.run(active);
            Step.powerGraph.run(active);

            if(Vars.asyncCore != null){
                Vars.asyncCore.begin();
                Vars.asyncCore.end();
            }
        });
    }

    private void run(SeamRuntime runtime, Step step){
        update.run(runtime, step::run);
    }

    private static void stepClockRaw(SeamRuntime runtime, float delta){
        runtime.state.tick++;
        runtime.state.updateId++;
        runtime.state.wavetime += delta;
    }

    private static void updateGroup(EntityGroup<?> group){
        if(group == null){
            throw new IllegalStateException("Runtime entity group is null.");
        }

        group.update();
    }

    private static void validateDelta(float delta){
        if(Float.isNaN(delta) || Float.isInfinite(delta)){
            throw new IllegalArgumentException("Clock step delta must be finite.");
        }

        if(delta < 0f){
            throw new IllegalArgumentException("Clock step delta must not be negative.");
        }
    }

    private enum Step{
        player{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.player);
            }
        },

        bullet{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.bullet);
            }
        },

        unit{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.unit);
            }
        },

        build{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.build);
            }
        },

        sync{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.sync);
            }
        },

        draw{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.draw);
            }
        },

        fire{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.fire);
            }
        },

        puddle{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.puddle);
            }
        },

        weather{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.weather);
            }
        },

        label{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.label);
            }
        },

        powerGraph{
            @Override
            void run(SeamRuntime runtime){
                updateGroup(runtime.groups.powerGraph);
            }
        };

        abstract void run(SeamRuntime runtime);
    }
}