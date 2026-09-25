package dev.infrai.creator;

public final class InfraiCallException extends RuntimeException {
    private final String code;
    private final int status;

    public InfraiCallException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
