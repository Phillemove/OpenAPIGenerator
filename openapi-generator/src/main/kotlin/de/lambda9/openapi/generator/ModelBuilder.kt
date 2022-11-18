package de.lambda9.openapi.generator

import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.*
import kotlin.io.path.Path

/**
 * Data Class to extract Data for Model building
 */
data class ModelSchema(val name: String, val key: String, val description: String?, val scheme: Schema)

/**
 * Generate Schema models from OpenApi3 Schema
 * @param openapi OpenApi Model
 * @param path Filepath where generated files should be saved
 * @param packageName Name of package for files
 */
fun generateModels(openapi: OpenApi3?, path: String, packageName: String){

    val schema = arrayListOf<ModelSchema>()
    val required = arrayListOf<String>()
    val enums = arrayListOf<String>()

    openapi?.schemas?.forEach { (name, scheme) ->
        if (scheme.hasRequiredFields()) {
            scheme.requiredFields.forEach { required.add(it) }
        }
        if (scheme.hasProperties()) {
            scheme.properties.forEach { (key, value) ->
                schema.add(ModelSchema(name, key, scheme.description, value))
                if (value.hasEnums()) {
                    value.enums.forEach { enum -> enums.add(enum.toString()) }
                    buildEnum(enums, key, path, ("$packageName.models"))
                }
            }
            buildDataClass(schema, required, path, ("$packageName.models"))
            schema.clear()
            required.clear()
            enums.clear()
        }
    }
}

/**
 * generates ENUM Classfile from given ArrayList
 * @param values ArrayList with all ENUM-Types
 * @param name Name of Object, this ENUM belongs to
 * @param path Path where file should be saved
 * @param packageName Name of package
 */
private fun buildEnum(values: ArrayList<String>, name: String, path: String, packageName: String) {

    val file = FileSpec.builder(packageName, name + "Enum")
    val enumBuilder = TypeSpec.enumBuilder(name + "Enum")
    values.forEach { enumBuilder.addEnumConstant(it) }
    file.addType(enumBuilder.build()).build().writeTo(Path(path))
}

/**
 * Generates DataClass from given Schema
 * @param schema ArrayList of ModelSchema Elements
 * @param required Contains required field names
 * @param path Path where File should be saved
 * @param packageName Name of package
 */
private fun buildDataClass(schema: ArrayList<ModelSchema>, required: ArrayList<String>, path: String, packageName: String) {

    var isNotRequiredField: Boolean

    val file = FileSpec.builder(packageName, schema[0].name)
    val classBuilder = TypeSpec.classBuilder(schema[0].name).addModifiers(KModifier.DATA).addAnnotation(kotlinx.serialization.Serializable::class)
    classBuilder.addKdoc("Model-Class for ${schema[0].name} \n")
    schema[0].description?.let { classBuilder.addKdoc(it) }

    val ctor = FunSpec.constructorBuilder()
    schema.forEach {
        val desc = if(it.scheme.description != null) {
            it.scheme.description
        } else {
            ""
        }
        isNotRequiredField = checkRequiredField(required, it.key)
        if(isNotRequiredField) {
            ctor.addParameter(ParameterSpec.builder(it.key, getClassNames(packageName, it.scheme).copy(nullable = true)).defaultValue("null").addKdoc(desc).build())
        } else {
            ctor.addParameter(ParameterSpec.builder(it.key, getClassNames(packageName, it.scheme).copy(nullable = false)).addKdoc(desc).build())
        }

    }
    classBuilder.primaryConstructor(ctor.build())

    schema.forEach {
        isNotRequiredField = checkRequiredField(required, it.key)
        classBuilder.addProperty(
            PropertySpec.builder(it.key, getClassNames(packageName, it.scheme).copy(nullable = isNotRequiredField))
                .initializer(it.key).build()
        )
    }
    file.addType(classBuilder.build())
    file.build().writeTo(Path(path))
}

/**
 * Checks if a given field is in required Array
 * @param required List of required parameters
 * @param field Field to check
 * @return Boolean value if field is required or not
 */
private fun checkRequiredField(required: ArrayList<String>, field: String): Boolean {

    var isNotRequired = true

    for (req in required) {
        if (req == field) {
            isNotRequired = false
        }
    }
    return isNotRequired
}