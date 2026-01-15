package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.graphQLRoute() {

    route("/hasura/v1/graphql") {

        post({
            summary = "Hasura GraphQL endpoint"
            description = """
                This is the **GraphQL endpoint powered by Hasura**.

                ### How to use
                - Send a **POST** request
                - `Content-Type: application/json`
                - Body must contain a GraphQL query

                ### Example request
                ```json
                {
                  "query": "query { bundle { id version } }"
                }
                ```

                ### Tools
                - GraphiQL: `/hasura`
                - Docs: https://hasura.io/docs

                > ⚠️ This endpoint is **not REST**.
                > Use GraphQL clients or the Hasura Console.
            """.trimIndent()

            tags = listOf("GraphQL")

            request {
                body<Map<String, Any>> {
                    description = "GraphQL request payload"
                }
            }

            response {
                HttpStatusCode.OK to {
                    description = "GraphQL response"
                    body<Map<String, Any>>()
                }
            }
        }) {
            // This endpoint exists ONLY for Swagger documentation
        }
    }
}
