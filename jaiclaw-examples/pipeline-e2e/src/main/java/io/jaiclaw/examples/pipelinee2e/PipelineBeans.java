package io.jaiclaw.examples.pipelinee2e;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * Two stage-processor beans the e2e pipeline dispatches to. Kept trivial so
 * the pipeline's correctness (rather than the bean logic) is what the e2e
 * actually validates.
 */
@Configuration
public class PipelineBeans {

    @Bean
    public Function<String, String> upperCase() {
        return s -> s == null ? "" : s.toUpperCase();
    }

    @Bean
    public Function<String, String> addExclaim() {
        return s -> (s == null ? "" : s) + "!";
    }
}
