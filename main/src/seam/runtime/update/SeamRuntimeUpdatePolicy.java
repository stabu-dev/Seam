package seam.runtime.update;

public final class SeamRuntimeUpdatePolicy{
    public final boolean enabled;

    public final boolean mutations;
    public final boolean teams;
    public final boolean logic;
    public final boolean waves;
    public final boolean objectives;
    public final boolean ai;
    public final boolean environment;
    public final boolean buildings;
    public final boolean power;
    public final boolean puddles;
    public final boolean fires;
    public final boolean weather;
    public final boolean bullets;
    public final boolean units;
    public final boolean sync;
    public final boolean draw;
    public final boolean collisions;

    private SeamRuntimeUpdatePolicy(Builder builder){
        this.enabled = builder.enabled;

        this.mutations = builder.mutations;
        this.teams = builder.teams;
        this.logic = builder.logic;
        this.waves = builder.waves;
        this.objectives = builder.objectives;
        this.ai = builder.ai;
        this.environment = builder.environment;
        this.buildings = builder.buildings;
        this.power = builder.power;
        this.puddles = builder.puddles;
        this.fires = builder.fires;
        this.weather = builder.weather;
        this.bullets = builder.bullets;
        this.units = builder.units;
        this.sync = builder.sync;
        this.draw = builder.draw;
        this.collisions = builder.collisions;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static SeamRuntimeUpdatePolicy disabled(){
        return builder()
        .enabled(false)
        .build();
    }

    public static SeamRuntimeUpdatePolicy none(){
        return builder()
        .enabled(true)
        .build();
    }

    public static SeamRuntimeUpdatePolicy buildingsOnly(){
        return builder()
        .enabled(true)
        .mutations(true)
        .teams(true)
        .environment(true)
        .buildings(true)
        .power(true)
        .build();
    }

    public static SeamRuntimeUpdatePolicy all(){
        return builder()
        .enabled(true)
        .mutations(true)
        .teams(true)
        .logic(true)
        .waves(true)
        .objectives(true)
        .ai(true)
        .environment(true)
        .buildings(true)
        .power(true)
        .puddles(true)
        .fires(true)
        .weather(true)
        .bullets(true)
        .units(true)
        .sync(true)
        .draw(true)
        .collisions(true)
        .build();
    }

    public boolean enabled(){
        return enabled;
    }

    public boolean mutations(){
        return mutations;
    }

    public boolean teams(){
        return teams;
    }

    public boolean buildings(){
        return buildings;
    }

    public boolean logic(){
        return logic;
    }

    public boolean waves(){
        return waves;
    }

    public boolean objectives(){
        return objectives;
    }

    public boolean ai(){
        return ai;
    }

    public boolean environment(){
        return environment;
    }

    public boolean power(){
        return power;
    }

    public boolean puddles(){
        return puddles;
    }

    public boolean fires(){
        return fires;
    }

    public boolean weather(){
        return weather;
    }

    public boolean bullets(){
        return bullets;
    }

    public boolean units(){
        return units;
    }

    public boolean sync(){
        return sync;
    }

    public boolean draw(){
        return draw;
    }

    public boolean collisions(){
        return collisions;
    }

    public boolean updateMutations(){
        return enabled && mutations;
    }

    public boolean updateTeams(){
        return enabled && teams;
    }

    public boolean updateLogic(){
        return enabled && logic;
    }

    public boolean updateWaves(){
        return enabled && waves;
    }

    public boolean updateObjectives(){
        return enabled && objectives;
    }

    public boolean updateAi(){
        return enabled && ai;
    }

    public boolean updateEnvironment(){
        return enabled && environment;
    }

    public boolean updateBuildings(){
        return enabled && buildings;
    }

    public boolean updatePower(){
        return enabled && power;
    }

    public boolean updatePuddles(){
        return enabled && puddles;
    }

    public boolean updateFires(){
        return enabled && fires;
    }

    public boolean updateWeather(){
        return enabled && weather;
    }

    public boolean updateBullets(){
        return enabled && bullets;
    }

    public boolean updateUnits(){
        return enabled && units;
    }

    public boolean updateSync(){
        return enabled && sync;
    }

    public boolean updateDraw(){
        return enabled && draw;
    }

    public boolean updateCollisions(){
        return enabled && collisions;
    }

    public SeamRuntimeUpdatePolicy withEnabled(boolean enabled){
        return toBuilder()
        .enabled(enabled)
        .build();
    }

    public Builder toBuilder(){
        return builder()
        .enabled(enabled)
        .mutations(mutations)
        .teams(teams)
        .logic(logic)
        .waves(waves)
        .objectives(objectives)
        .ai(ai)
        .environment(environment)
        .buildings(buildings)
        .power(power)
        .puddles(puddles)
        .fires(fires)
        .weather(weather)
        .bullets(bullets)
        .units(units)
        .sync(sync)
        .draw(draw)
        .collisions(collisions);
    }

    @Override
    public String toString(){
        return "SeamRuntimeUpdatePolicy{" +
        "enabled=" + enabled +
        ", mutations=" + mutations +
        ", teams=" + teams +
        ", logic=" + logic +
        ", waves=" + waves +
        ", objectives=" + objectives +
        ", ai=" + ai +
        ", environment=" + environment +
        ", buildings=" + buildings +
        ", power=" + power +
        ", puddles=" + puddles +
        ", fires=" + fires +
        ", weather=" + weather +
        ", bullets=" + bullets +
        ", units=" + units +
        ", sync=" + sync +
        ", draw=" + draw +
        ", collisions=" + collisions +
        '}';
    }

    public static final class Builder{
        private boolean enabled = true;

        private boolean mutations;
        private boolean teams;
        private boolean logic;
        private boolean waves;
        private boolean objectives;
        private boolean ai;
        private boolean environment;
        private boolean buildings;
        private boolean power;
        private boolean puddles;
        private boolean fires;
        private boolean weather;
        private boolean bullets;
        private boolean units;
        private boolean sync;
        private boolean draw;
        private boolean collisions;

        private Builder(){
        }

        public Builder enabled(boolean enabled){
            this.enabled = enabled;
            return this;
        }

        public Builder mutations(boolean mutations){
            this.mutations = mutations;
            return this;
        }

        public Builder teams(boolean teams){
            this.teams = teams;
            return this;
        }

        public Builder logic(boolean logic){
            this.logic = logic;
            return this;
        }

        public Builder waves(boolean waves){
            this.waves = waves;
            return this;
        }

        public Builder objectives(boolean objectives){
            this.objectives = objectives;
            return this;
        }

        public Builder ai(boolean ai){
            this.ai = ai;
            return this;
        }

        public Builder environment(boolean environment){
            this.environment = environment;
            return this;
        }

        public Builder buildings(boolean buildings){
            this.buildings = buildings;
            return this;
        }

        public Builder power(boolean power){
            this.power = power;
            return this;
        }

        public Builder puddles(boolean puddles){
            this.puddles = puddles;
            return this;
        }

        public Builder fires(boolean fires){
            this.fires = fires;
            return this;
        }

        public Builder weather(boolean weather){
            this.weather = weather;
            return this;
        }

        public Builder bullets(boolean bullets){
            this.bullets = bullets;
            return this;
        }

        public Builder units(boolean units){
            this.units = units;
            return this;
        }

        public Builder sync(boolean sync){
            this.sync = sync;
            return this;
        }

        public Builder draw(boolean draw){
            this.draw = draw;
            return this;
        }

        public Builder collisions(boolean collisions){
            this.collisions = collisions;
            return this;
        }

        public SeamRuntimeUpdatePolicy build(){
            return new SeamRuntimeUpdatePolicy(this);
        }
    }
}
