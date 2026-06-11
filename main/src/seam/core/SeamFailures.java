package seam.core;

public final class SeamFailures{
    private SeamFailures(){
    }

    public static String describe(Throwable throwable, String fallback){
        if(throwable == null){
            return fallback;
        }

        String type = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();

        if(message == null || message.isEmpty()){
            return type;
        }

        return type + ": " + message;
    }
}
