package de.lambda9.openapi.generator

import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Path
import com.reprezen.kaizen.oasparser.model3.Schema


/**
 * Data Classes used to extract the Information from OpenApi3 Model into it
 */
data class Server (val url: String, val description: String?)
data class Route (val path: String, val method: ArrayList<Method>)
data class Method (val method: String, val parameters: ArrayList<Parameters>?, val requestBody: RequestBody?, val response: ArrayList<Response>, val description: String?, val operationId: String?)
data class Parameters (val name: String, val location: String, val required: Boolean, val schema: Schema?)
data class RequestBody (val content: String, val schema: Schema?)
data class Response (val code: String, val description: String?, val content: String?, val schema: Schema?)

/**
 * Extracts Server Information from OpenApi3 Model and Create Server instance of it
 * @param openapi OpenApi3 Model
 * @return ArrayList of all Servers
 */
fun extractServers(openapi: OpenApi3?): ArrayList<Server> {

    val servers = arrayListOf<Server>()

    openapi?.servers?.forEach { it -> servers.add(Server(it.url, it.description)) }

    return servers
}

/**
 * Extracts Response Information from OpenApi3 Model and Create Response instance of it
 * @param operation Operation field of OpenApi3 Model
 * @return ArrayList of all Responses
 */
private fun extractResponses(operation: Operation): ArrayList<Response> {

    val responses = arrayListOf<Response>()

    operation.responses.values.forEach {
        if (it.hasContentMediaTypes()) {
            it.contentMediaTypes.values.forEach { v ->
                responses.add(
                    Response(
                        Overlay.of(it).pathInParent,
                        it.description,
                        Overlay.of(v).pathInParent,
                        v.schema
                    )
                )
            }
        } else {
            responses.add(
                Response(
                    Overlay.of(it).pathInParent,
                    it.description,
                    null,
                    null
                )
            )
        }
    }

    return responses
}

/**
 * Extracts RequestBody Information from OpenApi3 Model, if existing and Create RequestBody instance of it
 * @param operation Operation field of OpenApi3 Model
 * @return RequestBody instance or null
 */
private fun extractRequestBody(operation: Operation): RequestBody? {

    var requestBody: RequestBody? = null

    if (operation.requestBody.hasContentMediaTypes()) {
        operation.requestBody.contentMediaTypes.values.forEach {
            requestBody = RequestBody(Overlay.of(it).pathInParent, it.schema)
        }
    }

    return requestBody
}

/**
 * Extracts Parameter Information from OpenApi3 Model if existing and Create Parameter instance of it
 * @param operation Operation field of OpenApi3 Model
 * @return ArrayList of all Parameters or empty List
 */
private fun extractParameters(operation: Operation): ArrayList<Parameters> {

        val params = arrayListOf<Parameters>()

        operation.parameters.forEach { params.add(Parameters(it.name, it.`in`, it.required, it.schema)) }

        return params
}

/**
 * Extracts HTTP-Methods Information from, OpenApi3 Model and create Method instance of it
 * @param path API Endpoint path
 * @return ArrayList of all HTTP Methods
 */
private fun extractMethods(path: Path): ArrayList<Method> {
    val methods = arrayListOf<Method>()

    path.operations.forEach { (method, operation) ->
        methods.add(Method(method, extractParameters(operation), extractRequestBody(operation), extractResponses(operation), operation.description, operation.operationId))
    }

    return methods
}

/**
 * Extracts Route Information from OpenApi3 Model and Create Route instance of it
 * @param openapi OpenApi3 Model
 * @return ArrayList of all Routes
 */
fun extractRoutes(openapi: OpenApi3?): ArrayList<Route> {

    val route = arrayListOf<Route>()

    openapi?.paths?.forEach {
        route.add(Route(Overlay.of(it.value).pathInParent, extractMethods(it.value)))
    }

    return route
}