package io.jaiclaw.wiki

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class WikiServiceSpec extends Specification {

    @TempDir
    Path tempDir

    WikiService service

    def setup() {
        def repo = new JsonFileWikiRepository(tempDir)
        service = new WikiService(repo)
    }

    def "create page returns page with generated id"() {
        when:
        def page = service.createPage("Test Page", "docs", ["tag1"], "Hello world", null)

        then:
        page.id() != null
        page.title() == "Test Page"
        page.category() == "docs"
        page.body() == "Hello world"
    }

    def "get page by title"() {
        given:
        service.createPage("My Page", "docs", [], "content", null)

        expect:
        service.getPage("My Page").isPresent()
        service.getPage("My Page").get().title() == "My Page"
    }

    def "get page by id"() {
        given:
        def page = service.createPage("By ID", null, [], "content", null)

        expect:
        service.getPage(page.id()).isPresent()
    }

    def "update body modifies page content"() {
        given:
        service.createPage("Updateable", null, [], "old", null)

        when:
        def updated = service.updateBody("Updateable", "new content")

        then:
        updated.isPresent()
        updated.get().body() == "new content"
    }

    def "delete page removes it"() {
        given:
        def page = service.createPage("Deleteme", null, [], "content", null)

        when:
        def deleted = service.deletePage(page.id())

        then:
        deleted
        service.getPage(page.id()).isEmpty()
    }

    def "list by category filters correctly"() {
        given:
        service.createPage("Page 1", "docs", [], "a", null)
        service.createPage("Page 2", "notes", [], "b", null)

        when:
        def docs = service.listByCategory("docs")

        then:
        docs.size() == 1
        docs[0].title() == "Page 1"
    }

    def "search by tag finds matching pages"() {
        given:
        service.createPage("Tagged", null, ["java", "spring"], "content", null)
        service.createPage("Other", null, ["python"], "content", null)

        when:
        def results = service.searchByTag("java")

        then:
        results.size() == 1
        results[0].title() == "Tagged"
    }
}
