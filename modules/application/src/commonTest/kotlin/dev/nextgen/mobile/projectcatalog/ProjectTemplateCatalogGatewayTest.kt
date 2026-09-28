package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateAiOperationCapability
import dev.nextgen.mobile.domain.project.TemplateSelectionResult
import dev.nextgen.mobile.network.DeviceConnectivity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectTemplateCatalogGatewayTest {
    @Test
    fun `family read validates the public response and sends no account credentials`() {
        val transport = QueueCatalogTransport(AccountHttpResponse(200, familiesResponse))

        val result = runSuspendTest { gateway(transport).listFamilies() }

        val loaded = assertIs<ProjectTemplateCatalogGatewayResult.Loaded<List<ProjectTemplateRemoteFamily>>>(result)
        assertEquals(5, loaded.value.size)
        assertEquals(2, loaded.value.single { it.family == ProjectTemplateFamily.LITERATURE_REVIEW }.selectableTemplateCount)
        assertEquals(CatalogRequest("GET", "https://api.evidrilo.test/v1/project-template-families", emptyMap()), transport.requests.single())
    }

    @Test
    fun `template list maps only contract-valid published summaries`() {
        val transport = QueueCatalogTransport(AccountHttpResponse(200, summaryResponse))

        val result = runSuspendTest {
            gateway(transport).listTemplates(ProjectTemplateFamily.LITERATURE_REVIEW)
        }

        val loaded = assertIs<ProjectTemplateCatalogGatewayResult.Loaded<List<ProjectTemplateSummary>>>(result)
        assertEquals(listOf("review-outline"), loaded.value.map(ProjectTemplateSummary::id))
        assertEquals("A source-comparison outline.", loaded.value.single().summary)
        assertEquals(
            CatalogRequest("GET", "https://api.evidrilo.test/v1/project-templates?family=literature_review&limit=24", emptyMap()),
            transport.requests.single(),
        )
    }

    @Test
    fun `template list follows a bounded next cursor without repeating requests`() {
        val firstPage = summaryResponse.replace("\"nextAfterTemplateId\":null", "\"nextAfterTemplateId\":\"review-outline\"")
        val secondPage = summaryResponse
            .replace("\"id\":\"review-outline\"", "\"id\":\"review-outline-2\"")
            .replace("\"nextAfterTemplateId\":null", "\"nextAfterTemplateId\":null")
        val transport = QueueCatalogTransport(
            AccountHttpResponse(200, firstPage),
            AccountHttpResponse(200, secondPage),
        )

        val result = runSuspendTest {
            gateway(transport).listTemplates(ProjectTemplateFamily.LITERATURE_REVIEW)
        }

        val loaded = assertIs<ProjectTemplateCatalogGatewayResult.Loaded<List<ProjectTemplateSummary>>>(result)
        assertEquals(listOf("review-outline", "review-outline-2"), loaded.value.map(ProjectTemplateSummary::id))
        assertEquals(
            listOf(
                "https://api.evidrilo.test/v1/project-templates?family=literature_review&limit=24",
                "https://api.evidrilo.test/v1/project-templates?family=literature_review&limit=24&afterTemplateId=review-outline",
            ),
            transport.requests.map(CatalogRequest::url),
        )
    }

    @Test
    fun `template detail is mapped and accepted by the domain readiness gate`() {
        val transport = QueueCatalogTransport(AccountHttpResponse(200, detailResponse))

        val result = runSuspendTest {
            gateway(transport).getTemplate("review-outline", 3, ProjectTemplateFamily.LITERATURE_REVIEW)
        }

        val loaded = assertIs<ProjectTemplateCatalogGatewayResult.Loaded<ProjectTemplateDefinition>>(result)
        assertEquals("review-outline", loaded.value.id)
        assertEquals(3, loaded.value.version)
        assertEquals(ProjectTemplateFamily.LITERATURE_REVIEW, loaded.value.family)
        assertEquals(2, loaded.value.examples.size)
        assertTrue(loaded.value.examples.all { it.reviewed })
        assertIs<TemplateSelectionResult.Selected>(selectPublishedTemplate(loaded.value))
        assertEquals(
            CatalogRequest(
                "GET",
                "https://api.evidrilo.test/v1/project-templates/review-outline/versions/3",
                emptyMap(),
            ),
            transport.requests.single(),
        )
    }

    @Test
    fun `template detail accepts explicit stage-scoped AI capabilities`() {
        val response = detailResponse.replace(
            "\"inputFieldIds\":[\"question\"]",
            "\"inputFieldIds\":[\"question\"],\"aiOperations\":[{" +
                "\"id\":\"summarize_selected_material\",\"inputFieldIds\":[\"question\"]," +
                "\"outputFieldIds\":[\"question\"]}]",
        )

        val result = runSuspendTest {
            gateway(QueueCatalogTransport(AccountHttpResponse(200, response)))
                .getTemplate("review-outline", 3, ProjectTemplateFamily.LITERATURE_REVIEW)
        }

        val loaded = assertIs<ProjectTemplateCatalogGatewayResult.Loaded<ProjectTemplateDefinition>>(result)
        assertEquals(
            listOf(ProjectTemplateAiOperationCapability(
                "summarize_selected_material",
                listOf("question"),
                listOf("question"),
            )),
            loaded.value.steps.single().aiOperations,
        )
    }

    @Test
    fun `malformed response is an error rather than an empty catalog`() {
        val transport = QueueCatalogTransport(
            AccountHttpResponse(200, """{"schema":"evidrilo.project-template-families","version":"1","families":[],"requestId":"request-12345","unexpected":true}"""),
        )

        val result = runSuspendTest { gateway(transport).listFamilies() }

        assertEquals("INVALID_CATALOG_RESPONSE", assertIs<ProjectTemplateCatalogGatewayResult.Failed>(result).code)
    }

    @Test
    fun `published detail without a reviewed example fails closed`() {
        val transport = QueueCatalogTransport(
            AccountHttpResponse(200, detailResponse.replace("\"reviewed\":true", "\"reviewed\":false")),
        )

        val result = runSuspendTest {
            gateway(transport).getTemplate("review-outline", 3, ProjectTemplateFamily.LITERATURE_REVIEW)
        }

        assertEquals("TEMPLATE_NOT_READY", assertIs<ProjectTemplateCatalogGatewayResult.Failed>(result).code)
    }

    @Test
    fun `legacy response without example kind stays readable but is not selectable for a new project`() {
        val legacy = detailResponse.replace(Regex(",\\\"kind\\\":\\\"(?:normal|edge_or_conflicting)\\\""), "")
        val result = runSuspendTest {
            gateway(QueueCatalogTransport(AccountHttpResponse(200, legacy)))
                .getTemplate("review-outline", 3, ProjectTemplateFamily.LITERATURE_REVIEW)
        }

        val loaded = assertIs<ProjectTemplateCatalogGatewayResult.Loaded<ProjectTemplateDefinition>>(result)
        assertTrue(loaded.value.examples.all { it.kind == dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind.UNSPECIFIED })
        assertEquals(
            TemplateSelectionResult.Unavailable("TEMPLATE_NOT_READY"),
            selectPublishedTemplate(loaded.value),
        )
    }

    @Test
    fun `detail from a different family is rejected`() {
        val transport = QueueCatalogTransport(
            AccountHttpResponse(
                200,
                detailResponse.replace("\"family\":\"literature_review\"", "\"family\":\"observational_survey\""),
            ),
        )

        val result = runSuspendTest {
            gateway(transport).getTemplate("review-outline", 3, ProjectTemplateFamily.LITERATURE_REVIEW)
        }

        assertEquals("INVALID_CATALOG_RESPONSE", assertIs<ProjectTemplateCatalogGatewayResult.Failed>(result).code)
    }

    @Test
    fun `offline and unconfigured states remain distinct from an empty catalog`() {
        val offline = runSuspendTest {
            gateway(FailingCatalogTransport(DeviceConnectivity.OFFLINE)).listFamilies()
        }
        val unconfiguredTransport = QueueCatalogTransport(AccountHttpResponse(200, familiesResponse))
        val unconfigured = runSuspendTest {
            ProjectTemplateCatalogGateway(
                ProjectTemplateClientConfiguration(""),
                unconfiguredTransport,
            ).listFamilies()
        }

        assertEquals(ProjectTemplateCatalogUnavailableReason.OFFLINE, assertIs<ProjectTemplateCatalogGatewayResult.Unavailable>(offline).reason)
        assertEquals(ProjectTemplateCatalogUnavailableReason.NOT_CONFIGURED, assertIs<ProjectTemplateCatalogGatewayResult.Unavailable>(unconfigured).reason)
        assertTrue(unconfiguredTransport.requests.isEmpty())
    }

    @Test
    fun `not found is terminal while service errors are retryable`() {
        val notFound = runSuspendTest {
            gateway(QueueCatalogTransport(AccountHttpResponse(404, "{}"))).getTemplate(
                "review-outline", 3, ProjectTemplateFamily.LITERATURE_REVIEW,
            )
        }
        val unavailable = runSuspendTest {
            gateway(QueueCatalogTransport(AccountHttpResponse(503, "{}"))).listFamilies()
        }

        assertEquals("TEMPLATE_NOT_FOUND", assertIs<ProjectTemplateCatalogGatewayResult.Failed>(notFound).code)
        assertEquals(false, assertIs<ProjectTemplateCatalogGatewayResult.Failed>(notFound).retryable)
        assertEquals(true, assertIs<ProjectTemplateCatalogGatewayResult.Failed>(unavailable).retryable)
    }

    @Test
    fun `cancellation is propagated to the caller`() {
        val transport = FailingCatalogTransport(DeviceConnectivity.ONLINE, CancellationException("cancelled"))

        val cancellation = runCatching { runSuspendTest { gateway(transport).listFamilies() } }.exceptionOrNull()

        assertIs<CancellationException>(cancellation)
    }

    private fun gateway(transport: AccountHttpTransport) = ProjectTemplateCatalogGateway(
        ProjectTemplateClientConfiguration("https://api.evidrilo.test"),
        transport,
    )

    private fun selectPublishedTemplate(template: ProjectTemplateDefinition): TemplateSelectionResult =
        ProjectTemplateCatalogFlow.choose(
            dev.nextgen.mobile.domain.project.ProjectTemplateCatalogSnapshot(1, listOf(template)),
            template.id,
            template.version,
        )

    private companion object {
        val familiesResponse = """
            {"schema":"evidrilo.project-template-families","version":"1","families":[
              {"family":{"id":"experimental_laboratory","displayName":"Experimental and laboratory work"},"selectableTemplateCount":0},
              {"family":{"id":"observational_survey","displayName":"Observational and survey studies"},"selectableTemplateCount":0},
              {"family":{"id":"literature_review","displayName":"Literature reviews"},"selectableTemplateCount":2},
              {"family":{"id":"qualitative_interview_field_study","displayName":"Qualitative interviews and field studies"},"selectableTemplateCount":0},
              {"family":{"id":"design_engineering","displayName":"Design and engineering projects"},"selectableTemplateCount":0}
            ],"requestId":"request-12345"}
        """.trimIndent()

        val summaryResponse = """
            {"schema":"evidrilo.project-template-catalog","version":"1","templates":[
              {"id":"review-outline","version":3,"family":"literature_review","title":"Compare sources","summary":"A source-comparison outline.","publication":"published"}
            ],"nextAfterTemplateId":null,"requestId":"request-12345"}
        """.trimIndent()

        val detailResponse = """
            {"schema":"evidrilo.project-template-detail","version":"1","template":{
              "id":"review-outline","version":3,"family":"literature_review","title":"Compare sources",
              "summary":"A source-comparison outline.","intendedOutput":"A traceable synthesis outline.",
              "inputFields":[{"id":"question","kind":"research_question","label":"Review question","required":true}],
              "steps":[{"id":"scope","title":"Set the scope","inputFieldIds":["question"]}],
              "methodSpecificLimitations":["This structure does not judge source quality."],
              "provenanceRequirements":["Record source identifiers and selection reasons."],
              "accessibilityExpectations":["Use descriptive labels and preserve reading order."],
              "examples":[
                {"id":"example-one","summary":"Reviewed normal structure example.","reviewed":true,"kind":"normal"},
                {"id":"example-edge","summary":"Reviewed edge structure example.","reviewed":true,"kind":"edge_or_conflicting"}
              ],
              "publication":"published","publishedAt":"2026-09-26T00:00:00Z"
            },"requestId":"request-12345"}
        """.trimIndent()
    }
}

private data class CatalogRequest(val method: String, val url: String, val headers: Map<String, String>)

private class QueueCatalogTransport(
    private vararg val responses: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<CatalogRequest>()
    private var index = 0

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += CatalogRequest(method, url, headers)
        val response = responses.getOrElse(index) { responses.lastOrNull() ?: AccountHttpResponse(500, "") }
        index += 1
        return response
    }
}

private class FailingCatalogTransport(
    override val deviceConnectivity: DeviceConnectivity,
    private val failure: Exception = IllegalStateException("transport unavailable"),
) : AccountHttpTransport {
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse = throw failure
}
