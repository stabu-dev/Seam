package seam.runtime.control;

import seam.runtime.*;

public final class SeamContextSlot<T>{
    public interface Reader<T>{
        T get();
    }

    public interface Writer<T>{
        void set(T value);
    }

    public interface RuntimeReader<T>{
        T get(SeamRuntime runtime);
    }

    private final String name;
    private final boolean required;
    private final Reader<T> reader;
    private final Writer<T> writer;
    private final RuntimeReader<T> runtimeReader;

    private SeamContextSlot(String name, boolean required, Reader<T> reader, Writer<T> writer, RuntimeReader<T> runtimeReader){
        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Context slot name must not be blank.");
        }

        if(reader == null){
            throw new NullPointerException("reader");
        }

        if(writer == null){
            throw new NullPointerException("writer");
        }

        this.name = name;
        this.required = required;
        this.reader = reader;
        this.writer = writer;
        this.runtimeReader = runtimeReader;
    }

    public static <T> SeamContextSlot<T> runtime(String name, boolean required, Reader<T> reader, Writer<T> writer, RuntimeReader<T> runtimeReader){
        if(runtimeReader == null){
            throw new NullPointerException("runtimeReader");
        }

        return new SeamContextSlot<>(name, required, reader, writer, runtimeReader);
    }

    public static <T> SeamContextSlot<T> protectedProcess(String name, boolean required, Reader<T> reader, Writer<T> writer){
        return new SeamContextSlot<>(name, required, reader, writer, null);
    }

    public String name(){
        return name;
    }

    public boolean required(){
        return required;
    }

    public boolean runtimeOwned(){
        return runtimeReader != null;
    }

    public T read(){
        return reader.get();
    }

    public void write(T value){
        writer.set(value);
    }

    public T runtimeValue(SeamRuntime runtime){
        if(runtimeReader == null){
            throw new IllegalStateException("Context slot '" + name + "' is not runtime-owned.");
        }

        return runtimeReader.get(runtime);
    }

    public void install(SeamRuntime runtime){
        write(runtimeValue(runtime));
    }

    public Captured capture(){
        return new Captured(this, read());
    }

    public void validateCurrentValuePresent(){
        if(required && read() == null){
            throw new IllegalStateException("[Seam validation] Context slot '" + name + "' is null.");
        }
    }

    public void validateRuntimeInstalled(SeamRuntime runtime){
        T actual = read();
        T expected = runtimeValue(runtime);

        if(actual != expected){
            throw new IllegalStateException("[Seam validation] Context slot '" + name + "' is not installed.");
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreObject(Object value){
        write((T)value);
    }

    public static final class Captured{
        private final SeamContextSlot<?> slot;
        private final Object value;

        private Captured(SeamContextSlot<?> slot, Object value){
            this.slot = slot;
            this.value = value;
        }

        public String name(){
            return slot.name();
        }

        public void restore(){
            slot.restoreObject(value);
        }
    }
}