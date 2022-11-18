package de.lambda9.openapi.generator

import com.squareup.kotlinpoet.*
import io.ktor.server.application.*
import kotlin.io.path.Path
import kotlin.collections.ArrayList

/**
 * Function builds a Kotlin file (.kt) with KotlinPoet.
 * This file then will fill with an API-Module implementation.
 * @param packageName Name of Package the files belong to
 * @param path Filepath where file should save to
 * @param server ArrayList of Server Objects
 * @param routes ArrayList of Route Objects
 * @param title API Title
 * @param version API Version
 * @return KotlinPoet FileSpec which then can be created
 */
fun generateModuleFile(packageName: String, path: String, server: ArrayList<Server>, routes: ArrayList<Route>, title: String?, version: String?) {

    var servers = "Servers: \n"

    server.forEach { servers += it.url + " " + it.description + "\n" }

    buildModuleFile(
        routes,
        servers,
        title,
        version,
        packageName
    ).writeTo(Path(path))
}

/**
 * Generates Module from given Parameters
 * @param routes API-Routes
 * @param servers API-Server
 * @param title API Title
 * @param version API Version
 * @param packageName Name of Package from Server-File
 * @return KotlinPoet FileSpec which can be created
 */
private fun buildModuleFile(
    routes: ArrayList<Route>,
    servers: String,
    title: String?,
    version: String?,
    packageName: String
): FileSpec {

    var reg = ""

    val module = FunSpec.builder("apiModule")
        .addKdoc("apiModule of Server. Routing calls routing Functions from Routing modules")
        .receiver(Application::class)


    routes.forEach { route ->

        val name = generateRouteName(route.path)

        val param: String = if(route.path == "/") {
            "root"
        } else {
            route.path.replace("/", "").replace("{", "").replace("}", "")
        }

        module.addParameter( ParameterSpec.builder(param, ClassName(packageName, name)).build())
        reg += "register( $param ) \n   "
    }

    return FileSpec.builder(packageName, "ApiModule")
        .addFileComment("$title $version")
        .addFileComment(servers)
        .addImport("io.ktor.server.application", "install")
        .addImport("io.ktor.serialization.kotlinx.json", "json")
        .addImport("io.ktor.server.plugins.contentnegotiation", "ContentNegotiation")
        .addImport("io.ktor.server.routing", "routing")
        .addFunction(
                module.addCode(
                    """
                    |install(ContentNegotiation){
                    |   json()
                    |}
                    |
                    |routing {
                    |   $reg 
                    |}
                    |""".trimMargin()
                )
                .build()
        )
        .build()
}