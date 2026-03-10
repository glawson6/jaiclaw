package io.jclaw.shell.commands.setup;

import org.springframework.shell.component.context.ComponentContext;

import java.util.NoSuchElementException;

public interface WizardStep {

    String name();

    /**
     * Execute this wizard step, populating the result.
     * @return false to abort the wizard
     */
    boolean execute(OnboardResult result);

    /**
     * Safely get a value from a ComponentContext, returning null if the key is missing.
     */
    static <T> T getOrNull(ComponentContext<?> context, String key, Class<T> type) {
        try {
            return context.get(key, type);
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
