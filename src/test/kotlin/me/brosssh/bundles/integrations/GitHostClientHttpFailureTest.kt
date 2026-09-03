package me.brosssh.bundles.integrations

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import me.brosssh.bundles.integrations.common.RepoRef
import me.brosssh.bundles.integrations.gitea.GiteaHostClient
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.integrations.gitlab.GitlabHostClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GitHostClientHttpFailureTest {
    @Test
    fun `GitHub exposes unsuccessful repository status`() = runBlocking {
        assertStatus(HttpStatusCode.NotFound) { client ->
            GithubClient(client, "https://api.github.test").getRepo(REPO)
        }
    }

    @Test
    fun `GitLab exposes unsuccessful repository status`() = runBlocking {
        assertStatus(HttpStatusCode.Gone) { client ->
            GitlabHostClient(client, "https://gitlab.test").getRepo(REPO)
        }
    }

    @Test
    fun `Gitea exposes unsuccessful repository status`() = runBlocking {
        assertStatus(HttpStatusCode(451, "Unavailable For Legal Reasons")) { client ->
            GiteaHostClient(client, "https://gitea.test").getRepo(REPO)
        }
    }

    private suspend fun assertStatus(
        status: HttpStatusCode,
        request: suspend (HttpClient) -> Unit
    ) {
        val client = HttpClient(MockEngine { respond("{}", status) })

        val error = assertFailsWith<ClientRequestException> {
            request(client)
        }

        assertEquals(status.value, error.response.status.value)
        client.close()
    }

    private companion object {
        val REPO = RepoRef("example", "patches")
    }
}
