package io.jaiclaw.examples.agentmind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies bundled persona markdown files from {@code classpath:/personas/*.md}
 * to the configured runtime persona dir on application startup.
 *
 * <p>The {@code PersonaOverlayManager} reads from a filesystem path, but the
 * demo ships personas inside the example JAR. This seeder bridges the two —
 * it copies any bundled persona that is not already on disk, so user-authored
 * personas in the runtime dir are never overwritten.
 *
 * <p>Runs after the {@code PersonaOverlayManager} bean is constructed, so the
 * manager's first-pass load may see an empty dir; the demo's behaviour
 * subsequently picks them up on the next chat turn (the manager re-checks the
 * filesystem on each {@code reload()}).
 */
@Configuration
public class PersonaSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PersonaSeeder.class);

    @Value("${jaiclaw.agentmind.soul.personas.dir}")
    private String personasDir;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        seed();
    }

    void seed() {
        try {
            Path dir = Path.of(personasDir);
            Files.createDirectories(dir);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:/personas/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                Path target = dir.resolve(filename);
                if (Files.exists(target)) continue;
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("Seeded persona {} → {}", filename, target);
            }
        } catch (Exception e) {
            log.warn("Persona seeding failed: {}", e.getMessage());
        }
    }
}
