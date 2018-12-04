package dronesim;

public class ServiceUnresolved extends RuntimeException {
    public ServiceUnresolved() { super(); }
    public ServiceUnresolved(String msg) { super(msg); }
}
