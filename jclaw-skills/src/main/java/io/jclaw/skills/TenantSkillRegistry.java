package io.jclaw.skills;

import io.jclaw.core.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tenant-aware skill registry that filters skills based on tenant ID and version.
 * Skills with empty tenantIds in metadata are available to all tenants.
 */
public class TenantSkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantSkillRegistry.class);

    private final List<SkillDefinition> allSkills;
    private final Map<String, List<SkillDefinition>> tenantCache = new ConcurrentHashMap<>();

    public TenantSkillRegistry(List<SkillDefinition> skills) {
        this.allSkills = skills != null ? List.copyOf(skills) : List.of();
    }

    /**
     * Get skills available to a specific tenant.
     */
    public List<SkillDefinition> getSkillsForTenant(String tenantId) {
        if (tenantId == null) return allSkills;
        return tenantCache.computeIfAbsent(tenantId, this::resolveForTenant);
    }

    /**
     * Get a specific skill by name for a tenant.
     */
    public Optional<SkillDefinition> getSkill(String tenantId, String skillName) {
        return getSkillsForTenant(tenantId).stream()
                .filter(s -> s.name().equals(skillName))
                .findFirst();
    }

    /**
     * Get a specific version of a skill for a tenant.
     */
    public Optional<SkillDefinition> getSkill(String tenantId, String skillName, String version) {
        return getSkillsForTenant(tenantId).stream()
                .filter(s -> s.name().equals(skillName))
                .filter(s -> s.metadata().version().equals(version))
                .findFirst();
    }

    /**
     * Total number of registered skills.
     */
    public int size() {
        return allSkills.size();
    }

    /**
     * Invalidate the cache for a tenant (e.g., after skill configuration change).
     */
    public void invalidateCache(String tenantId) {
        tenantCache.remove(tenantId);
    }

    public void invalidateAllCaches() {
        tenantCache.clear();
    }

    private List<SkillDefinition> resolveForTenant(String tenantId) {
        var filtered = allSkills.stream()
                .filter(s -> s.metadata().isAvailableToTenant(tenantId))
                .collect(Collectors.toList());
        log.debug("Resolved {} skills for tenant {}", filtered.size(), tenantId);
        return List.copyOf(filtered);
    }
}
