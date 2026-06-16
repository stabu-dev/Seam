package seam.runtime;

public final class SeamRuntimeConfig{
    public final int id;
    public final String name;
    public final int width;
    public final int height;

    private SeamRuntimeConfig(Builder builder){
        this.id = builder.id;
        this.name = builder.name;
        this.width = builder.width;
        this.height = builder.height;
    }

    public void validate(){
        if(id < 0){
            throw new IllegalArgumentException("Runtime id must be >= 0.");
        }

        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Runtime name cannot be blank.");
        }

        if(width <= 0 || height <= 0){
            throw new IllegalArgumentException("Runtime size must be positive.");
        }

    }

    public static Builder builder(){
        return new Builder();
    }

    public static final class Builder{
        private int id = -1;
        private String name = "runtime";
        private int width = 1;
        private int height = 1;

        public Builder id(int id){
            this.id = id;
            return this;
        }

        public Builder name(String name){
            this.name = name;
            return this;
        }

        public Builder size(int width, int height){
            this.width = width;
            this.height = height;
            return this;
        }

        public SeamRuntimeConfig build(){
            SeamRuntimeConfig config = new SeamRuntimeConfig(this);
            config.validate();
            return config;
        }
    }
}
