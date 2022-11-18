package de.phillemove.openapi.generator

import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.math.*

/**
 * Checks Type of name and returns correct ClassName
 * @param packageName Name of package
 * @param schema Openapi Schema
 * @return TypeName as ClassName
 */
fun getClassNames(packageName: String, schema: Schema): TypeName {
    return when(schema.type) {
        "string" -> {
            when(schema.format) {
                "uuid" -> UUID::class.asClassName()
                "datetime" -> LocalDateTime::class.asClassName()
                "date" -> LocalDate::class.asClassName()
                "binary" -> ByteArray::class.asClassName()
                "byte" -> String::class.asClassName()
                else -> String::class.asClassName()
            }
        }
        "integer" -> {
            when(schema.format) {
                "int64" -> Long::class.asClassName()
                "bigint" -> BigInteger::class.asClassName()
                else -> Int::class.asClassName()
            }
        }
        "number" -> {
            when(schema.format) {
                "float" -> Float::class.asClassName()
                "bigdecimal" -> BigDecimal::class.asClassName()
                else -> Double::class.asClassName()
            }}
        "bool" -> Boolean::class.asClassName()
        "object" -> {
            if(schema.hasProperties()) {
                ClassName(packageName, schema.name)
            } else {
                Map::class.parameterizedBy(String::class).plusParameter(Any::class.asClassName().copy(true))
            }
        }
        "array" -> {
            ClassName("kotlin.collections", "List").parameterizedBy(getClassNames(packageName, schema.itemsSchema))
        }

        else -> ClassName(packageName, schema.name)
    }

}