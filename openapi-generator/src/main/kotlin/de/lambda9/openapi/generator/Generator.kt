package de.lambda9.openapi.generator

import java.io.File

/**
 * Generates Kotlin File with API Method in it
 * @param specFile Specification File
 * @param validation Flag if validation in parsing Spec file is wanted
 * @param target Path where the generated .kt file will be saved
 * @param packageName Name of Package of Project
 * */
fun generateApi(specFile: File, validation: Boolean, target: String, packageName: String) {

    //Parse and extract OpenAPiModel
    val openapi = parseSpecFile(specFile, validation)

    val routes = extractRoutes(openapi)
    val server = extractServers(openapi)

    //Create ModelClasses
    generateModels(openapi, target, packageName)

    //Create ErrorHandler for Server and Routes
    generateErrorHandler(target, packageName)

    //Create ApiModule File
    generateModuleFile(packageName, target, server, routes, openapi?.info?.title, openapi?.info?.version)

    //Create Route Interface Files
    generateRoutesFile(routes, target, packageName)

    println("All Files generated")
}
