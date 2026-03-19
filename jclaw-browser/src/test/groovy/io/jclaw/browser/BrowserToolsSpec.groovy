package io.jclaw.browser

import io.jclaw.core.tool.ToolResult
import spock.lang.Specification

class BrowserToolsSpec extends Specification {

    def "all() returns 8 browser tools"() {
        given:
        def service = new BrowserService(BrowserConfig.DEFAULT)

        when:
        def tools = BrowserTools.all(service)

        then:
        tools.size() == 8
        tools.collect { it.definition().name() }.containsAll([
            "browser_navigate", "browser_click", "browser_type",
            "browser_screenshot", "browser_evaluate", "browser_read_page",
            "browser_list_tabs", "browser_close_tab"
        ])
    }

    def "all tools are in Browser section"() {
        given:
        def tools = BrowserTools.all(new BrowserService(BrowserConfig.DEFAULT))

        expect:
        tools.every { it.definition().section() == "Browser" }
    }

    def "navigate tool requires url parameter"() {
        given:
        def service = new BrowserService(BrowserConfig.DEFAULT)
        def tool = BrowserTools.all(service).find { it.definition().name() == "browser_navigate" }

        when:
        def result = tool.execute([:], null)

        then:
        result instanceof ToolResult.Error
    }
}
