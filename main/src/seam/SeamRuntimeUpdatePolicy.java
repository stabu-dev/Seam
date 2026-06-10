package seam;

public final class SeamRuntimeUpdatePolicy{
    public final boolean enabled;

    public final boolean mutations;
    public final boolean teams;
    public final boolean buildings;
    public final boolean power;
    public final boolean puddles;
    public final boolean fires;
    public final boolean weather;

    public final boolean bullets;
    public final boolean units;
    public final boolean sync;
    public final boolean draw;

    private SeamRuntimeUpdatePolicy(Builder builder){
        this.enabled = builder.enabled;

        this.mutations = builder.mutations;
        this.teams = builder.teams;
        this.buildings = builder.buildings;
        this.power = builder.power;
        this.puddles = builder.puddles;
        this.fires = builder.fires;
        this.weather = builder.weather;

        this.bullets = builder.bullets;
        this.units = builder.units;
        this.sync = builder.sync;
        this.draw = builder.draw;
    }

    public static SeamRuntimeUpdatePolicy disabled(){
        return builder()
        .enabled(false)
        .build();
    }

    public static SeamRuntimeUpdatePolicy buildingsOnly(){
        return builder()
        .enabled(true)
        .mutations(true)
        .teams(true)
        .buildings(true)
        .power(true)
        .puddles(true)
        .fires(true)
        .weather(true)
        .build();
    }

    public static SeamRuntimeUpdatePolicy fullUnsafe(){
        return builder()
        .enabled(true)
        .mutations(true)
        .teams(true)
        .buildings(true)
        .power(true)
        .puddles(true)
        .fires(true)
        .weather(true)
        .bullets(true)
        .units(true)
        .sync(true)
        .draw(true)
        .build();
    }

    public SeamRuntimeUpdatePolicy withEnabled(boolean enabled){
        return copy()
        .enabled(enabled)
        .build();
    }

    public Builder copy(){
        return builder()
        .enabled(enabled)
        .mutations(mutations)
        .teams(teams)
        .buildings(buildings)
        .power(power)
        .puddles(puddles)
        .fires(fires)
        .weather(weather)
        .bullets(bullets)
        .units(units)
        .sync(sync)
        .draw(draw);
    }

    public static Builder builder(){
        return new Builder();
    }

    @Override
    public String toString(){
        return "SeamRuntimeUpdatePolicy{" +
        "enabled=" + enabled +
        ", mutations=" + mutations +
        ", teams=" + teams +
        ", buildings=" + buildings +
        ", power=" + power +
        ", puddles=" + puddles +
        ", fires=" + fires +
        ", weather=" + weather +
        ", bullets=" + bullets +
        ", units=" + units +
        ", sync=" + sync +
        ", draw=" + draw +
        '}';
    }

    public static final class Builder{
        private boolean enabled = true;

        private boolean mutations;
        private boolean teams;
        private boolean buildings;
        private boolean power;
        private boolean puddles;
        private boolean fires;
        private boolean weather;

        private boolean bullets;
        private boolean units;
        private boolean sync;
        private boolean draw;

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

        public SeamRuntimeUpdatePolicy build(){
            return new SeamRuntimeUpdatePolicy(this);
        }
    }
}