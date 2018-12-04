package domain;

import java.io.Serializable;

public class DataStreamProtocol {

    public static class StreamInit implements Serializable {
        public static final StreamInit INSTANCE = new StreamInit();
    }

    public static class StreamAck implements Serializable {
        public static final StreamAck INSTANCE = new StreamAck();
    }

    public static class StreamComplete implements Serializable {
        public static final StreamComplete INSTANCE = new StreamComplete();
    }

    public static class StreamFailure extends RuntimeException {
        public StreamFailure() {}
        public StreamFailure(Throwable ex) { super(ex); }
        public StreamFailure(String message, Throwable ex) { super(message, ex); }

    }
}
