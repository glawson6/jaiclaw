package io.jaiclaw.pipeline.dashboard

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PipelineDashboardControllerSpec extends Specification {

    PipelineDashboardController controller = new PipelineDashboardController()
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build()

    def "GET /pipelines/dashboard serves the HTML shell"() {
        when:
        def result = mvc.perform(get("/pipelines/dashboard"))

        then:
        result.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Pipeline Dashboard")))
    }

    def "GET /pipelines/dashboard/whoami returns multiTenant=false when no tenant is set"() {
        given:
        TenantContextHolder.clear()

        when:
        def result = mvc.perform(get("/pipelines/dashboard/whoami"))

        then:
        result.andExpect(status().isOk())
                .andExpect(jsonPath('$.multiTenant').value(false))
                .andExpect(jsonPath('$.tenantId').isEmpty())
    }

    def "GET /pipelines/dashboard/whoami returns the current tenant when one is set"() {
        given:
        TenantContextHolder.set(new DefaultTenantContext("tenant-x", "Tenant X"))

        when:
        def result = mvc.perform(get("/pipelines/dashboard/whoami"))

        then:
        result.andExpect(status().isOk())
                .andExpect(jsonPath('$.multiTenant').value(true))
                .andExpect(jsonPath('$.tenantId').value("tenant-x"))
                .andExpect(jsonPath('$.tenantName').value("Tenant X"))

        cleanup:
        TenantContextHolder.clear()
    }
}
