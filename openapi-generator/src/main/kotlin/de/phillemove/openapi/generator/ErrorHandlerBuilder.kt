package de.phillemove.openapi.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import kotlin.io.path.Path
import kotlin.reflect.KClass

/**
 * Generates ErrorHandler Interfaces and DefaultErrorHandler
 * @param filepath Path where File should be generated
 * @param packageName Package of generated File
 */
fun generateErrorHandler (filepath: String, packageName: String) {

    val file = FileSpec.builder(packageName, "ErrorHandler")
        .addImport("io.ktor.server.application", "call")
        .addImport("io.ktor.server.response", "respond")
        .addImport(HttpStatusCode::class.java.packageName, HttpStatusCode::class.simpleName!!)

    file.addType(buildErrorHandlerInterface(packageName))
    file.addType(buildDefaultErrorHandler(packageName))
    file.addType(buildConstraintErrorInterface(packageName))

    file.build().writeTo(Path(filepath))
}

/**
 * Generates sealed interface for ConstraintErrors
 * @param packageName Package of generated File
 * @return TypeSpec with Interface
 */
private fun buildConstraintErrorInterface(packageName: String): TypeSpec {

    val iFace = TypeSpec.interfaceBuilder("ConstraintError").addModifiers(KModifier.SEALED)

    iFace.addType(
        buildDataClass(packageName, "MissingRequiredParam", listOf(
        "name" to String::class.asClassName(),
        "description" to String::class.asClassName(),
    ))
    )

    iFace.addType(
        buildDataClass(packageName, "MinLength", listOf(
        "name" to String::class.asClassName(),
        "description" to String::class.asClassName(),
        "min" to Int::class.asClassName(),
    ))
    )

    iFace.addType(
        buildDataClass(packageName, "MaxLength", listOf(
        "name" to String::class.asClassName(),
        "description" to String::class.asClassName(),
        "max" to Int::class.asClassName(),
    ))
    )

    iFace.addType(
        buildDataClass(packageName, "RegexMatch", listOf(
        "name" to String::class.asClassName(),
        "description" to String::class.asClassName(),
        "regex" to String::class.asClassName(),
    ))
    )
    iFace.addType(
        buildDataClass(packageName, "MissingListItemError", listOf(
        "name" to String::class.asClassName(),
        "description" to String::class.asClassName(),
        "min" to Int::class.asClassName()
    ))
    )
    return iFace.build()
}

/**
 * Builds Dataclass with Parameters from given List
 * @param packageName Package of generated File
 * @param classname Name Dataclass should get
 * @param parameters List of Parameters for Dataclass
 * @return TypeSpec with Dataclass
 */
private fun buildDataClass(packageName: String, classname: String, parameters: List<Pair<String, ClassName>>): TypeSpec {

    val data = TypeSpec.classBuilder(classname).addModifiers(KModifier.DATA)
        .addAnnotation(kotlinx.serialization.Serializable::class)
        .addSuperinterface(ClassName(packageName, "ConstraintError"))

    val ctor = FunSpec.constructorBuilder()
    parameters.forEach { (name, type) -> ctor.addParameter(name, type) }
    data.primaryConstructor(ctor.build())

    parameters.forEach { (name, type) -> data.addProperty(PropertySpec.builder(name, type).initializer(name).build()) }

    return data.build()
}

/**
 * Builds ErrorHandler Interface with abstract Methods
 * @param packageName Package of generated File
 * @return TypeSpec with Interface
 */
private fun buildErrorHandlerInterface(packageName: String): TypeSpec {

    val iFace = TypeSpec.interfaceBuilder("ErrorHandler")

    iFace.addFunction(getConstraintErrorFunction(packageName, KModifier.ABSTRACT).build())

    iFace.addFunction(getInternalErrorFunction(KModifier.ABSTRACT).build())

    return iFace.build()

}

/**
 * Builds DefaultErrorHandler Object
 * @param packageName Package of generated File
 * @return TypeSpec with Object
 */
private fun buildDefaultErrorHandler(packageName: String): TypeSpec {

    val obj = TypeSpec.objectBuilder("DefaultErrorHandler")
        .addSuperinterface(ClassName(packageName, "ErrorHandler"))

    obj.addFunction(getConstraintErrorFunction(packageName, KModifier.OVERRIDE).addStatement("call.respond(HttpStatusCode.BadRequest, errors)").build())
    obj.addFunction(
        getInternalErrorFunction(KModifier.OVERRIDE)
        .addStatement("call.respond(HttpStatusCode.InternalServerError, (t.message ?: \"\") +  \n (t.cause?.message.let{\"|\$it\"}) ?: \"\")").build())

    return obj.build()
}

/**
 * Sets import for PipelineContext as Class name correctly
 * @param left KClass Param
 * @param right KClass Param
 * @return Class as TypeName
 * */
private fun pipelineContext(left: KClass<*>, right: KClass<*>): TypeName =
    PipelineContext::class.parameterizedBy(left).plusParameter(right)

/**
 * Builds ConstraintErrorFunction with Modifier
 * @param packageName Package of generated File
 * @param modifier Modifier Function should have
 */
private fun getConstraintErrorFunction(packageName: String, modifier: KModifier): FunSpec.Builder {

    val param = ParameterSpec.builder("errors", ClassName("kotlin.collections", "List")
        .parameterizedBy(ClassName(packageName, "ConstraintError"))).build()

    val constraint = FunSpec.builder("handleConstraintErrors").addModifiers(modifier).addModifiers(KModifier.SUSPEND)
        .addParameter(param)
    constraint.receiver(pipelineContext(Unit::class, ApplicationCall::class))

    return constraint
}

/**
 * Builds InternalErrorFunction with Modifier
 * @param modifier Modifier Function should have
 */
private fun getInternalErrorFunction( modifier: KModifier): FunSpec.Builder {

    val internal = FunSpec.builder("handleInternalError").addModifiers(modifier).addModifiers(KModifier.SUSPEND)
        .addParameter("t", Throwable::class)
    internal.receiver(pipelineContext(Unit::class, ApplicationCall::class))

    return internal
}
