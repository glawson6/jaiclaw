package io.jaiclaw.rules.engine.loader;

/**
 * Exception thrown when rule loading fails.
 */
public class RuleLoadingException extends Exception {

    private final String loaderType;
    private final String location;

    public RuleLoadingException(String message) {
        super(message);
        this.loaderType = null;
        this.location = null;
    }

    public RuleLoadingException(String message, Throwable cause) {
        super(message, cause);
        this.loaderType = null;
        this.location = null;
    }

    public RuleLoadingException(String loaderType, String location, String message) {
        super(String.format("[%s] Failed to load rules from '%s': %s", loaderType, location, message));
        this.loaderType = loaderType;
        this.location = location;
    }

    public RuleLoadingException(String loaderType, String location, String message, Throwable cause) {
        super(String.format("[%s] Failed to load rules from '%s': %s", loaderType, location, message), cause);
        this.loaderType = loaderType;
        this.location = location;
    }

    public String getLoaderType() {
        return loaderType;
    }

    public String getLocation() {
        return location;
    }
}
