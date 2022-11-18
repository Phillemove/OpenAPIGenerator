package de.lambda9.openapi.generator

import java.util.*

/**
 * Function generates Name for Route Interfaces from given API-Path in PascalCase
 * @param path API-Endpoint Path as String
 * @return Path-Name String in PascalCase
 */
fun generateRouteName(path: String): String {

    var name = ""

    if(path == "/") {
        name += "Root"
    } else {
        val words = path.split("/")

        words.forEach { word ->
            name += word.replace("{", "").replace("}", "").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
    return name
}