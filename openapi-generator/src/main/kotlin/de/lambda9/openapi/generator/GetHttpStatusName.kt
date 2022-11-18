package de.lambda9.openapi.generator

import io.ktor.http.*

/**
 * Converts HTTP Status code to its corresponding name
 * @param code HTTP Status Code as int
 * @return Converted code as String
 */
fun getHttpStatusName(code: Int): String {
    return HttpStatusCode.fromValue(code).description.replace("\\s+", "").replace("-", "")
        .replace(" ", "")
}