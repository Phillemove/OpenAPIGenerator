package de.lambda9.openapi.generator

import com.reprezen.kaizen.oasparser.OpenApi3Parser
import com.reprezen.kaizen.oasparser.model3.*
import java.io.File

/**
 * Parses a given OpenApi .yaml or .json SpecFile with KaiZenParser into an OpenApi3 Model,
 * witch than can be accessed.
 * @param file Spec YAML File
 * @param validate Boolean if validation is wanted or not
 * @return Returns OpenApi3 Model or null
 * */
fun parseSpecFile(file: File, validate: Boolean): OpenApi3? {

    val openapi: OpenApi3 = OpenApi3Parser().parse(file, validate)

    return if (!validate || openapi.isValid) {
        openapi
    } else {
        null
    }
}