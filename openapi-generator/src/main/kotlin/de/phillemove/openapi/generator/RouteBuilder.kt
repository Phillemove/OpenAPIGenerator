package de.phillemove.openapi.generator

import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.*
import io.ktor.server.routing.*
import kotlinx.html.HTML
import java.util.*
import kotlin.collections.ArrayList
import kotlin.io.path.Path

/**
 * DataClasses to extract Response Information for building Response Handler Interface
 */
data class ResponseItems(
    val handlername: String,
    val returntype: LambdaTypeName,
    var status: MutableList<ResponseStatus>,
    val typename: String
)

data class ResponseStatus(
    val code: Int,
    val description: String,
    val responseschema: Schema?,
    val htmlresponse: TypeName?
)

/**
 * Data Class for extract and Map all ContentTypes with all their ResponseCodes for building Response Structure in Register Function
 */
data class ContentType(val contentType: String, var code: MutableList<ContentCode>)
data class ContentCode(val code: Int, val hasschema: Boolean)

/**
 * Initiates generation of Route Files and save it to filepath
 * @param routes ArrayList with Route objects
 * @param filepath Path where Route-Files should save to
 * @param packageName Name of package
 * @return String of Route Methods
 *
 */
fun generateRoutesFile(routes: ArrayList<Route>, filepath: String, packageName: String) {

    routes.forEach { route ->
        val filename = generateRouteName(route.path)
        val modelList = getRequiredModelNames(route)
        val file = FileSpec.builder(packageName, filename)
            .addImport("io.ktor.server.application", "call")
            .addImport("io.ktor.server.routing", "post")
            .addImport("io.ktor.server.routing", "get")
            .addImport("io.ktor.server.routing", "Routing")
            .addImport("io.ktor.server.response", "respond")
            .addImport("io.ktor.server.response", "respondText")
            .addImport("io.ktor.server.application", "ApplicationCall")
            .addImport("io.ktor.http", "HttpStatusCode")
            .addImport("io.ktor.server.request", "receive")
            .addImport("kotlin", "Unit")
            .addImport("kotlinx.html", "HTML")
            .addImport("io.ktor.server.html", "respondHtml")
            .addType(buildInterface(filename, route.method, packageName))
            .addFunction(buildRegisterFunction(filename, route.method, route.path, packageName))

        modelList.forEach { model ->
            file.addImport("$packageName.models", model)
        }
        file.build().writeTo(Path(filepath))
    }

}

/**
 * Generates RouteInterface with sealed Interface for Response, data class for Request and handler function and
 * returns TypeSpec with it
 * @param filename Name of File/Interface
 * @param method HTTP Methods of Route with all Information of this path
 * @param packageName Name of package
 * @return TypeSpec with Interface
 */
private fun buildInterface(filename: String, method: ArrayList<Method>, packageName: String): TypeSpec {

    val iFace = TypeSpec.interfaceBuilder(filename)

    method.forEach { httpMethod ->

        val response = httpMethod.response
        val parameters = httpMethod.parameters
        val requestBody = httpMethod.requestBody
        var description = ""
        var desc: String
        val paramList = mutableListOf<ResponseItems>()

        if (httpMethod.description != null) {
            description = httpMethod.description
        }

        val preparedByContentType = prepareContentTypes(response)

        // Fills ResponseItems List with extracted Information to build Response Handler
        preparedByContentType.forEach {
            it.value.forEach { value ->
                desc = value.description ?: "Response Code"
                when (it.key) {
                    "application/json" -> {
                        extractContentTypeInformation(
                            paramList,
                            "applicationJson",
                            "Json",
                            desc,
                            value.code.toInt(),
                            value.schema,
                            null,
                            packageName
                        )
                    }
                    "text/plain" -> {
                        extractContentTypeInformation(
                            paramList,
                            "textPlain",
                            "Text",
                            desc,
                            value.code.toInt(),
                            value.schema,
                            null,
                            packageName
                        )
                    }
                    "text/html" -> {
                        extractContentTypeInformation(
                            paramList, "textHtml", "Html",
                            desc, value.code.toInt(), null,
                            LambdaTypeName.get(returnType = UNIT, receiver = HTML::class.asTypeName()), packageName
                        )
                    }
                    "text/csv" -> {
                        extractContentTypeInformation(
                            paramList,
                            "textCsv",
                            "Csv",
                            desc,
                            value.code.toInt(),
                            value.schema,
                            null,
                            packageName
                        )
                    }
                    "text/css" -> {
                        extractContentTypeInformation(
                            paramList,
                            "textCss",
                            "Css",
                            desc,
                            value.code.toInt(),
                            value.schema,
                            null,
                            packageName
                        )
                    }
                    else -> {
                        val contentType = generateRouteName(it.key)
                        extractContentTypeInformation(
                            paramList,
                            contentType,
                            contentType,
                            desc,
                            value.code.toInt(),
                            value.schema,
                            null,
                            packageName
                        )
                    }
                }
            }
        }
        iFace.addType(buildResponseDataClass(httpMethod.method, paramList, packageName))

        buildRequestDataClass(iFace, requestBody, parameters, packageName, httpMethod.method)

        iFace.addFunction(
            buildAbstractFunction(
                requestBody,
                parameters,
                httpMethod.method,
                description,
                httpMethod.operationId?.replace("-", "")?.replace(" ", "")
            )
        )
    }

    return iFace.build()
}

/**
 * Prepares a Map grouped by ContentType in Response
 * @param response ArrayList of Response Objects
 * @return Mutable Map of String and Response list - Key is ContentType
 */
private fun prepareContentTypes(response: ArrayList<Response>): MutableMap<String, List<Response>> {

    val groupedByContentType = response.groupBy { it.content }
    val responseList = mutableListOf<Response>()
    val preparedByContentType = mutableMapOf<String, List<Response>>()

    when {
        null in groupedByContentType && groupedByContentType.size == 1 -> {
            groupedByContentType.values.forEach {
                preparedByContentType["noContentType"] = it
            }
        }

        null in groupedByContentType && "*/*" in groupedByContentType && groupedByContentType.size == 2 -> {
            groupedByContentType.values.forEach {
                responseList += it
            }
            preparedByContentType["allContentTypes"] = responseList
            responseList.clear()
        }
        null in groupedByContentType || "*/*" in groupedByContentType -> {
            groupedByContentType.forEach {
                if (it.key == null || it.key == "*/*") {
                    responseList += it.value
                } else {
                    preparedByContentType["${it.key}"] = it.value
                }
            }
            if (responseList.isNotEmpty()) {
                preparedByContentType.forEach { type ->
                    preparedByContentType[type.key] = type.value + responseList
                }
                responseList.clear()
            }
        }
        else -> {
            groupedByContentType.forEach {
                preparedByContentType["${it.key}"] = it.value
            }
        }
    }
    return preparedByContentType
}

/**
 * Extracts ContentType Information, create Data Class objecs with it and fill it in List
 * @param paramList List to fill and with Information/Target
 * @param handlername Name of RoutingHandler
 * @param typename Name of Response Type
 * @param description Description
 * @param responsecode HTTP Response Code
 * @param schema Response Schema if specified
 * @param htmlresponse LambdaTypeName if it is an HTML Response Handler
 * @param packageName Name of Package
 * @return List of ResponseItems
 */
private fun extractContentTypeInformation(
    paramList: MutableList<ResponseItems>,
    handlername: String,
    typename: String,
    description: String,
    responsecode: Int,
    schema: Schema?,
    htmlresponse: TypeName?,
    packageName: String
): MutableList<ResponseItems> {
    val list = mutableListOf<ResponseStatus>()

    if (paramList.find { element -> element.handlername == handlername } != null) {
        val code = paramList.find { element -> element.handlername == handlername }!!.status
        code.add(ResponseStatus(responsecode, description, schema, htmlresponse))
        paramList.find { element -> element.handlername == handlername }!!.status = code
    } else {
        list.add(ResponseStatus(responsecode, description, schema, htmlresponse))
        paramList.add(
            ResponseItems(
                handlername,
                LambdaTypeName.get(
                    returnType = ClassName(packageName, "Response.$typename"),
                    receiver = ClassName(packageName, "Response.$typename.Helper")
                ), list, typename
            )
        )
    }
    return paramList
}

/**
 * Builds Request Data or Value Class and add it to Interface
 * @param iFace TypeSpec with Interface in it
 * @param requestBody RequestBody or null
 * @param parameters List of Parameters or null
 * @param packageName Name of package
 * @return TypeSpec Builder from Interface
 */
private fun buildRequestDataClass(
    iFace: TypeSpec.Builder,
    requestBody: RequestBody?,
    parameters: ArrayList<Parameters>?,
    packageName: String,
    method: String
): TypeSpec.Builder {
    if (requestBody != null && parameters != null && parameters.isNotEmpty()) {
        val data = TypeSpec.classBuilder(method + "Request")
            .addModifiers(KModifier.DATA)

        val ctor = FunSpec.constructorBuilder()
        ctor.addParameter(getRequestBodyParam(requestBody, packageName))
        parameters.forEach { ctor.addParameter(it.name.replace("-", ""), getClassNames(packageName, it.schema!!)) }
        data.primaryConstructor(ctor.build())

        data.addProperty(getRequestBodyProp(requestBody, packageName))
        parameters.forEach {
            data.addProperty(
                PropertySpec.builder(it.name.replace("-", ""), getClassNames(packageName, it.schema!!))
                    .initializer(it.name.replace("-", "")).build()
            )
        }
        iFace.addType(data.build())

    } else if (requestBody != null) {
        val data = TypeSpec.classBuilder(method + "Request")
            .addModifiers(KModifier.VALUE)
            .addAnnotation(JvmInline::class)
        val ctor = FunSpec.constructorBuilder()
        ctor.addParameter(getRequestBodyParam(requestBody, packageName))

        data.primaryConstructor(ctor.build())

        data.addProperty(getRequestBodyProp(requestBody, packageName))
        iFace.addType(data.build())

    } else if (parameters != null && parameters.isNotEmpty()) {

        val data = if (parameters.size > 1) {
            TypeSpec.classBuilder(method + "Request")
                .addModifiers(KModifier.DATA)
        } else {
            TypeSpec.classBuilder(method + "Request")
                .addModifiers(KModifier.VALUE)
                .addAnnotation(JvmInline::class)
        }

        val ctor = FunSpec.constructorBuilder()
        parameters.forEach { ctor.addParameter(it.name.replace("-", ""), getClassNames(packageName, it.schema!!)) }

        data.primaryConstructor(ctor.build())

        parameters.forEach {
            data.addProperty(
                PropertySpec.builder(it.name.replace("-", ""), getClassNames(packageName, it.schema!!))
                    .initializer(it.name.replace("-", "")).build()
            )
        }
        iFace.addType(data.build())
    }
    return iFace
}

/**
 * Builds Abstract Route Function
 * @param requestBody Request Body if exists
 * @param parameters List of Parameters if exists
 * @return Funspec with abstract Function
 */
private fun buildAbstractFunction(
    requestBody: RequestBody?,
    parameters: ArrayList<Parameters>?,
    httpMethod: String,
    description: String,
    operationId: String?
): FunSpec {

    val name = operationId ?: httpMethod

    val method = FunSpec.builder(name.lowercase()).addModifiers(KModifier.ABSTRACT).addKdoc(description)
        .returns(
            ClassName(
                "",
                httpMethod.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault()
                    ) else it.toString()
                })
        )

    if (requestBody != null && parameters != null && parameters.isNotEmpty()) {
        method.addParameter(
            "request", ClassName("", httpMethod + "Request")
        )
    } else if (requestBody != null) {
        method.addParameter(
            "request", ClassName("", httpMethod + "Request")
        )
    } else if (parameters != null && parameters.isNotEmpty()) {
        method.addParameter(
            "request", ClassName("", httpMethod + "Request")
        )
    }
    return method.build()
}

/**
 * Builds Response Data Classes
 * @param method HTTPMethod
 * @param parameters List of Parameters and Types this DataClass should get
 * @param packageName Name of Package
 * @return TypeSpec
 */
private fun buildResponseDataClass(method: String, parameters: List<ResponseItems>, packageName: String): TypeSpec {

    val data = if (parameters.size > 1) {
        TypeSpec.classBuilder(method
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
            .addModifiers(KModifier.DATA)
    } else {
        TypeSpec.classBuilder(method
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
            .addModifiers(KModifier.VALUE)
            .addAnnotation(JvmInline::class)
    }

    val ctor = FunSpec.constructorBuilder()

    parameters.forEach {
        ctor.addParameter(it.handlername, it.returntype)
    }
    data.primaryConstructor(ctor.build())

    parameters.forEach {
        data.addProperty(PropertySpec.builder(it.handlername, it.returntype).initializer(it.handlername).build())
    }
    val responseIFace = TypeSpec.interfaceBuilder("Response").addModifiers(KModifier.SEALED)

    parameters.forEach {
        val responseContentType = TypeSpec.interfaceBuilder(it.typename)
            .addSuperinterface(ClassName("", "Response"))
            .addModifiers(KModifier.SEALED)

        val helperobject = TypeSpec.objectBuilder("Helper")

        it.status.forEach { status ->
            val name = getHttpStatusName(status.code)
            var funname = generateRouteName(name)

            funname = if (funname == "OK") {
                funname.lowercase()
            } else {
                funname.replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() }
            }

            val helperfunction = FunSpec.builder(funname).returns(ClassName("", it.typename))

            if (status.responseschema != null) {
                val responseClass = TypeSpec.classBuilder(name).addModifiers(KModifier.VALUE)
                    .addKdoc(status.description)
                    .addSuperinterface(ClassName("", it.typename))
                    .addAnnotation(kotlinx.serialization.Serializable::class)
                    .addAnnotation(JvmInline::class)

                val const = FunSpec.constructorBuilder()
                const.addParameter(
                    ParameterSpec.builder("value", getClassNames(packageName, status.responseschema)).build()
                )

                responseClass.primaryConstructor(const.build())
                responseClass.addProperty(
                    PropertySpec.builder(
                        "value",
                        getClassNames(packageName, status.responseschema)
                    ).initializer("value").build()
                )
                helperfunction.addParameter(
                    ParameterSpec.builder(
                        "value",
                        getClassNames(packageName, status.responseschema)
                    ).build()
                )
                helperfunction.addStatement("return $name(value)")
                responseContentType.addType(responseClass.build())
            } else if (status.htmlresponse != null) {
                val responseClass = TypeSpec.classBuilder(name).addModifiers(KModifier.VALUE)
                    .addKdoc(status.description)
                    .addSuperinterface(ClassName("", it.typename))
                    .addAnnotation(JvmInline::class)

                val const = FunSpec.constructorBuilder()
                const.addParameter(ParameterSpec.builder("value", status.htmlresponse).build())

                responseClass.primaryConstructor(const.build())
                responseClass.addProperty(
                    PropertySpec.builder("value", status.htmlresponse).initializer("value").build()
                )
                helperfunction.addParameter(ParameterSpec.builder("value", status.htmlresponse).build())
                helperfunction.addStatement("return $name(value)")
                responseContentType.addType(responseClass.build())
            } else {
                val responseObject = TypeSpec.objectBuilder(name).addSuperinterface(ClassName("", it.typename))
                helperfunction.addStatement("return $name")
                responseContentType.addType(responseObject.build())
            }
            helperobject.addFunction(helperfunction.build())
        }
        responseContentType.addType(helperobject.build())
        responseIFace.addType(responseContentType.build())
    }

    data.addType(responseIFace.build())

    return data.build()
}

/**
 * Extracts Model Names used in given Route
 * @param route API-Route
 * @return list of used Models
 * */
private fun getRequiredModelNames(route: Route): List<String> {
    val models = mutableListOf<String>()
    route.method.forEach { HTTPMethod ->
        val body = HTTPMethod.requestBody
        if (body != null) {
            if (body.schema != null) {
                when (body.schema.type) {
                    "array" -> {
                        if (body.schema.itemsSchema.name != null) {
                            models.add(body.schema.itemsSchema.name)
                        }
                    }
                    "object" -> models.add(body.schema.name)
                    else -> models.add(body.schema.name)
                }
            }
        }
        val res = HTTPMethod.response
        res.forEach { resp ->
            if (resp.schema != null) {
                if (resp.schema.name != null) {
                    when (resp.schema.type) {
                        "array" -> {
                            if (resp.schema.itemsSchema.name != null) {
                                models.add(resp.schema.itemsSchema.name)
                            }
                        }
                        "object" -> models.add(resp.schema.name)
                        else -> models.add(resp.schema.name)
                    }
                }
            }
        }
    }
    return models
}

/**
 * Returns Property from RequestBody Schema for building DataClass Parameter
 * @param requestBody Request Body
 * @param packageName Name of package
 * @return PropertySpec with build Property
 */
private fun getRequestBodyProp(requestBody: RequestBody, packageName: String): PropertySpec {

    var prop: PropertySpec = PropertySpec.builder("body", String::class).build()

    if (requestBody.schema != null) {
        prop = PropertySpec.builder("body", getClassNames(packageName, requestBody.schema)).initializer("body").build()
    }

    return prop
}

/**
 * Returns Parameter from RequestBody Schema for building DataClass Parameter
 * @param requestBody Request Body
 * @param packageName Name of package
 * @return ParameterSpec with build Parameter
 */
private fun getRequestBodyParam(requestBody: RequestBody, packageName: String): ParameterSpec {

    var param: ParameterSpec = ParameterSpec.builder("body", String::class).build()

    if (requestBody.schema != null) {
        param = ParameterSpec.builder("body", getClassNames(packageName, requestBody.schema)).build()
    }

    return param
}

/**
 * Builds Register function with Code in it
 * @param filename Name for parameter Type
 * @param method HTTP Methods of Route
 * @param path Endpoint Path of Route
 * @param packageName Name of Package
 * @return FunSpec for building Method
 */
private fun buildRegisterFunction(
    filename: String,
    method: ArrayList<Method>,
    path: String,
    packageName: String
): FunSpec {

    val function = FunSpec.builder("register").receiver(Routing::class)
        .addParameter("handler", ClassName(packageName, filename))
        .addParameter(
            ParameterSpec.builder("errorHandler", ClassName(packageName, "ErrorHandler"))
                .defaultValue("DefaultErrorHandler")
                .build()
        )

    method.forEach { buildRegisterFunction(function, filename, it, path, it.parameters, it.requestBody) }

    return function.build()
}

/**
 * Builds Register function with Code in it
 * @param function FunSpec Builder which will be extended
 * @param filename Name of file for dependencies
 * @param method HTTP Method of Route
 * @param path Endpoint Path of Route
 * @param parameters List of Parameters of Route
 * @param requestBody RequestBody of this Route if exists
 * @return FunSpec for building Method
 */
private fun buildRegisterFunction(
    function: FunSpec.Builder,
    filename: String,
    method: Method,
    path: String,
    parameters: ArrayList<Parameters>?,
    requestBody: RequestBody?
): FunSpec {

    var paramCheck = ""
    var data = ""
    var body = ""
    val methodfunc = method.operationId?.replace("-", "")?.replace(" ", "") ?: method.method
    val responseHandler: String
    var whenBlock = ""
    val contentType = mutableListOf<ContentType>()

    parameters?.forEach { param ->
        paramCheck += parameterValidation(param)
        data += param.name.replace("-", "")
            .replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() } + "!!, "
    }

    val reqObj = when (requestBody?.schema?.type) {
        "array" -> {
            "List<${requestBody.schema.itemsSchema?.name?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}>"
        }
        "object" -> {
            requestBody.schema.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        else -> {
            requestBody?.schema?.type
        }
    }

    val reqValid = if (requestBody != null) {
        requestBodyValidation(requestBody)
    } else {
        ""
    }

    if (requestBody != null && parameters != null && parameters.isNotEmpty()) {

        body = "val body = try {\n           " +
                "call.receive<$reqObj>()\n" +
                "\n           " +
                "} catch (t: Throwable) {\n           " +
                "with (errorHandler) {\n               " +
                "handleInternalError(t)\n           " +
                "}\n           " +
                "return@${method.method}\n       " +
                "}\n"

        responseHandler =
            "val responseHandler = handler.${methodfunc.lowercase()}($filename.${method.method}Request(body, $data))"

    } else if (parameters != null && parameters.isNotEmpty()) {

        responseHandler =
            "val responseHandler = handler.${methodfunc.lowercase()}($filename.${method.method}Request($data))"

    } else if (requestBody != null) {

        body = "val body = try {\n           " +
                "call.receive<$reqObj>()\n" +
                "\n           " +
                "} catch (t: Throwable) {\n           " +
                "with (errorHandler) {\n               " +
                "handleInternalError(t)\n           " +
                "}\n           " +
                "return@${method.method}\n       " +
                "}\n"

        responseHandler =
            "val responseHandler = handler.${methodfunc.lowercase()}($filename.${method.method}Request(body))"
    } else {
        responseHandler = "val responseHandler = handler.${methodfunc.lowercase()}()"
    }


    val res =
        method.method.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    // Prepare and extract information to map contentypes with resonse codes
    val preparedByContentType = prepareContentTypes(method.response)

    preparedByContentType.forEach {
        it.value.forEach { value ->
            val hasschema = value.schema != null
            when (it.key) {
                "application/json" -> {
                    fillResponseInformationList(contentType, "application/json", value.code.toInt(), hasschema)
                }
                "text/html" -> {
                    fillResponseInformationList(contentType, "text/html", value.code.toInt(), hasschema)
                }
                "text/plain" -> {
                    fillResponseInformationList(contentType, "text/plain", value.code.toInt(), hasschema)
                }
                "text/csv" -> {
                    fillResponseInformationList(contentType, "text/csv", value.code.toInt(), hasschema)
                }
                "text/css" -> {
                    fillResponseInformationList(contentType, "text/css", value.code.toInt(), hasschema)
                }
                else -> {
                    val cType = generateRouteName(it.key)
                    fillResponseInformationList(contentType, cType, value.code.toInt(), hasschema)
                }
            }
        }
    }

    whenBlock += "${buildResponseStructure(filename, res, contentType)} "

    function.addCode(
        """
                | ${method.method} ("$path") {
                |       val requestErrors = mutableListOf<ConstraintError>()
                |       $paramCheck
                |       
                |       $body
                |       $reqValid
                |       if (requestErrors.isEmpty()) {
                |           try {
                |               $responseHandler
                |               $whenBlock
                |           } catch (t: Throwable) {
                |               with(errorHandler) {
                |                   handleInternalError(t)
                |               } 
                |           }
                |        } else {
                |              with(errorHandler) {
                |                   handleConstraintErrors(requestErrors)
                |              }
                |        }         
                | }
                | 
                """.trimMargin()
    ).build()

    return function.build()
}

/**
 * Fill and update List with contenttype and responsecode information
 * @param contentType List with ContentType objects
 * @param contenttype Content Type as String
 * @param responsecode Responsecode as Int
 * @return Filled or updated List
 */
private fun fillResponseInformationList(
    contentType: MutableList<ContentType>,
    contenttype: String,
    responsecode: Int,
    hasschema: Boolean
): MutableList<ContentType> {

    val responseCode = mutableListOf<ContentCode>()
    if (contentType.find { element -> element.contentType == contenttype } != null) {
        val rescode = contentType.find { element -> element.contentType == contenttype }!!.code
        rescode.add(ContentCode(responsecode, hasschema))
        contentType.find { element -> element.contentType == contenttype }!!.code = rescode
    } else {
        responseCode.add(ContentCode(responsecode, hasschema))
        contentType.add(ContentType(contenttype, responseCode))
    }
    return contentType
}

/**
 * Checks if a Schema is validable
 * @param property Property to check or null
 * @param parameter Parameter to check or null
 * @param itemsschema Schema if an Array Structure has to be checked or null
 * @return Boolean with check result
 */
private fun isValdable(property: Map.Entry<String, Schema>?, parameter: Parameters?, itemsschema: Schema?): Boolean {
    var validable = false
    if (property != null) {
        if (property.value.minLength != null) {
            validable = true
        }
        if (property.value.maxLength != null) {
            validable = true
        }
        if (property.value.pattern != null) {
            validable = true
        }
        if (property.value.minItems != null && property.value.minItems > 0) {
            validable = true
        }
    }
    if (parameter != null) {
        if (parameter.schema?.minLength != null) {
            validable = true
        }
        if (parameter.schema?.maxLength != null) {
            validable = true
        }
        if (parameter.schema?.pattern != null) {
            validable = true
        }
        if (parameter.required) {
            validable = true
        }
    }
    if (itemsschema != null) {

        if (itemsschema.minLength != null) {
            validable = true
        }
        if (itemsschema.maxLength != null) {
            validable = true
        }
        if (itemsschema.pattern != null) {
            validable = true
        }
        if (itemsschema.minItems != null && itemsschema.minItems > 0) {
            validable = true
        }

    }
    return validable
}

/**
 * Builds RequestBody Validation code as String
 * @param requestBody RequestBody from which the validation should build
 * @return Validation code as String
 */
private fun requestBodyValidation(requestBody: RequestBody): String {
    var requestValid = ""

    // First check if RequestBody has a schema and if the schema has Properties and then for each Property if this is validable
    // After if it is not another object Type with the format binary
    if (requestBody.schema != null) {
        if (requestBody.schema.hasProperties()) {
            requestBody.schema.properties.forEach { property ->
                if (isValdable(property, null, null)) {
                    if (property.value.type != "object" && property.value.format != "binary") {
                        val varName = if (property.key != null) {
                            property.key.toString()
                        } else {
                            requestBody.schema.name.toString()
                        }
                        // Validation generation for non Array Types
                        if (property.value.type != "array") {
                            val minlength = if (property.value.minLength != null) {
                                property.value.minLength.toString()
                            } else {
                                null
                            }
                            val maxlength = if (property.value.maxLength != null) {
                                property.value.maxLength.toString()
                            } else {
                                null
                            }

                            if (property.value.pattern != null) {
                                val reg = if (property.value.pattern.contains("\\")) {
                                    property.value.pattern.replace("\\", "\\\\")
                                } else {
                                    property.value.pattern
                                }
                                requestValid += "val regex = \"$reg\".toRegex()\n " +
                                        "val $varName = body.$varName\n         " +
                                        "when {\n             " +
                                        "regex.matches(body.$varName) ->\n                  " +
                                        "requestErrors.add(ConstraintError.RegexMatch(\"${property.key}\", description =\n    \"does not match the specified form\", " +
                                        "regex = \"$reg\"))\n       "

                            } else {
                                requestValid += "val $varName = body.$varName\n         " +
                                        "when {\n             "
                            }
                            if (requestBody.schema.requiredFields.contains(property.value.name)) {
                                requestValid += "$varName == null ->\n                  " +
                                        "requestErrors.add(ConstraintError.MissingRequiredParam(\"${property.key}\", description = \n     \"is required Property and is not set\"))\n         "
                            }
                            if (minlength != null) {
                                requestValid += "$varName.length < $minlength ->\n                  " +
                                        "requestErrors.add(ConstraintError.MinLength(\"${property.key}\", description = \n" +
                                        "     \"does not match the specified minimum length\", min= $minlength))\n         "
                            }
                            if (maxlength != null) {
                                requestValid += "$varName.length > $maxlength ->\n                  " +
                                        "requestErrors.add(ConstraintError.MaxLength(\"${property.key}\", description = \n" +
                                        "     \"does not match the specified maximum length\", max= $maxlength))\n         "
                            }
                            requestValid += "}\n       "
                        } else {
                            // Validation generation for Array Types
                            val minlength = if (property.value.itemsSchema.minLength != null) {
                                property.value.itemsSchema.minLength.toString()
                            } else {
                                null
                            }
                            val maxlength = if (property.value.itemsSchema.maxLength != null) {
                                property.value.itemsSchema.maxLength.toString()
                            } else {
                                null
                            }
                            if (property.value.minItems != null && property.value.minItems > 0) {
                                requestValid += "val $varName = body.$varName\n         " +
                                        "when {\n             "

                                requestValid += if (requestBody.schema.requiredFields.contains(property.value.name)) {
                                    "$varName.size < ${property.value.minItems} ->\n                  " +
                                            "requestErrors.add(ConstraintError.MissingListItemError(\"$varName\", description = \n     " +
                                            "\"Does not match the specified minimum items Count\", min = ${property.value.minItems}))\n         "
                                } else {
                                    "($varName?.size?.let{ it < ${property.value.minItems} } ?: false) ->\n                  " +
                                            "requestErrors.add(ConstraintError.MissingListItemError(\"$varName\", description = \n     " +
                                            "\"Does not match the specified minimum items Count\", min = ${property.value.minItems}))\n         "
                                }
                            } else {
                                requestValid += "val $varName = body.$varName\n         " +
                                        "when {\n             "
                            }
                            if (requestBody.schema.requiredFields.contains(property.value.name)) {
                                requestValid += "$varName == null ->\n                  " +
                                        "requestErrors.add(ConstraintError.MissingRequiredParam(\"${property.key}\", description = \n     \"is required Property and is not set\"))\n         "
                            }

                            requestValid += "}\n       "

                            if (isValdable(null, null, property.value.itemsSchema)) {
                                if (property.value.itemsSchema.pattern != null) {
                                    val reg = if (property.value.itemsSchema.pattern.contains("\\")) {
                                        property.value.itemsSchema.pattern.replace("\\", "\\\\")
                                    } else {
                                        property.value.itemsSchema.pattern
                                    }
                                    requestValid += if (requestBody.schema.requiredFields.contains(property.value.name)) {
                                        "val itemsregex = \"$reg\".toRegex()\n          " +
                                                "$varName.forEach { item -> \n          " +
                                                "when {\n             " +
                                                "itemsregex.matches(item) ->\n                  " +
                                                "requestErrors.add(ConstraintError.RegexMatch(\"${property.key} item\", description =\n     \"does not match the specified form\", " +
                                                "regex = \"$reg\"))\n       "
                                    } else {
                                        "val itemsregex = \"$reg\".toRegex()\n          " +
                                                "$varName?.forEach { item -> \n          " +
                                                "when {\n             " +
                                                "itemsregex.matches(item) ->\n                  " +
                                                "requestErrors.add(ConstraintError.RegexMatch(\"${property.key} item\", description =\n     \"does not match the specified form\", " +
                                                "regex = \"$reg\"))\n       "
                                    }
                                } else {
                                    requestValid += if (requestBody.schema.requiredFields.contains(property.value.name)) {
                                        "$varName.forEach { item -> \n          " +
                                                "when {\n             "
                                    } else {
                                        "$varName?.forEach { item -> \n          " +
                                                "when {\n             "
                                    }
                                }
                                if (minlength != null) {
                                    requestValid += "item.length < $minlength ->\n                  " +
                                            "requestErrors.add(ConstraintError.MinLength(\"${property.key} item\", description = \n" +
                                            "     \"does not match the specified minimum length\", min= $minlength))\n             "
                                }
                                if (maxlength != null) {
                                    requestValid += "item.length > $maxlength ->\n                  " +
                                            "requestErrors.add(ConstraintError.MaxLength(\"${property.key} item\", description = \n" +
                                            "     \"does not match the specified maximum length\", max= $maxlength))\n             "
                                }

                                requestValid += "}\n         " +
                                        "}\n"
                            }
                        }
                    }
                }
            }
        }
    }
    return requestValid
}

/**
 * Builds Parameter Validation code as String
 * @param parameter Parameter from which the validation should build
 * @return Validation code as String
 */
private fun parameterValidation(parameter: Parameters): String {

    val varName = parameter.name.replace("-", "")
        .replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.getDefault()) else it.toString() }

    var paramValid = "val $varName = call.parameters[\"${parameter.name}\"]\n       "
    if (isValdable(null, parameter, null)) {
        val minlength = if (parameter.schema?.minLength != null) {
            parameter.schema.minLength.toString()
        } else {
            null
        }
        val maxlength = if (parameter.schema?.maxLength != null) {
            parameter.schema.maxLength.toString()
        } else {
            null
        }
        if (parameter.schema?.pattern != null) {
            val reg = if (parameter.schema.pattern.contains("\\")) {
                parameter.schema.pattern.replace("\\", "\\\\")
            } else {
                parameter.schema.pattern
            }
            paramValid += if (parameter.required) {
                "val regex = \"$reg\".toRegex()\n " +
                        "when {\n             " +
                        "$varName == null ->\n                  " +
                        "requestErrors.add(ConstraintError.MissingRequiredParam(\"${parameter.name}\", description =\n        \"is required Parameter and is not set\"))\n             " +
                        "regex.matches($varName) ->\n                  " +
                        "requestErrors.add(ConstraintError.RegexMatch(\"${parameter.name}\", description =\n        \"does not match the specified form\", \n regex = \"$reg\"))\n       "
            } else {
                "val regex = \"$reg\".toRegex()\n " +
                        "when {\n             " +
                        "regex.matches($varName?) ->\n                  " +
                        "requestErrors.add(ConstraintError.RegexMatch(\"${parameter.name}\", description =\n       \"does not match the specified form\", \n" +
                        " regex = \"$reg\"))\n       "
            }
        } else {
            paramValid += "when {\n             "
            if (parameter.required) {
                paramValid += "$varName == null ->\n                  " +
                        "requestErrors.add(ConstraintError.MissingRequiredParam(\"${parameter.name}\", description =\n        \"is required Parameter and is not set\"))\n             "
            }
        }
        if (minlength != null) {
            paramValid += "$varName.length < $minlength ->\n                  " +
                    "requestErrors.add(ConstraintError.MinLength(\"${parameter.name}\", description = \n        \"does not match the specified minimum length\", min= $minlength))\n             "

        }
        if (maxlength != null) {
            paramValid += "$varName.length > $maxlength ->\n                  " +
                    "requestErrors.add(ConstraintError.MaxLength(\"${parameter.name}\", description = \n" +
                    "        \"does not match the specified maximum length\", max= $maxlength))\n             "
        }

        paramValid += "}\n       "
    }


    return paramValid
}

/**
 * Builds the Response Structure for the Register Function
 * @param routename Name of API-Route
 * @param responsetitle Name of Response Data-Class
 * @param contenttypes List of all specified contenttypes to this response
 * @return String with responsestructure
 */
private fun buildResponseStructure(routename: String, responsetitle: String, contenttypes: List<ContentType>): String {

    var responsestructure = ""

    if (contenttypes[0].contentType != "NoContentType" && contenttypes[0].contentType != "AllContentTypes") {
        responsestructure += "val accept = call.request.headers.get(\"Accept\")\n               " +
                "when(accept){\n                 "
    }

    contenttypes.forEach { cType ->
        if (cType.contentType == "NoContentType" || cType.contentType == "AllContentTypes") {
            responsestructure += "val res = responseHandler.${cType.contentType}($routename.$responsetitle.Response.${cType.contentType}.Helper) \n                            " +
                    "when(res) { \n                                "
            cType.code.forEach { code ->
                val name = getHttpStatusName(code.code)
                responsestructure += if (code.hasschema) {
                    "is $routename.$responsetitle.Response.${cType.contentType}.$name -> {\n                                   " +
                            "call.response.status(HttpStatusCode.$name)\n                              " +
                            "call.respond(res)\n                                " +
                            "}\n                                "
                } else {
                    "is $routename.$responsetitle.Response.${cType.contentType}.$name -> {\n                                   " +
                            "call.response.status(HttpStatusCode.$name)\n                              " +
                            "}\n                                "
                }
            }
            responsestructure += "} \n               "
        } else {
            responsestructure += "\"${cType.contentType}\" -> {\n                          "
            val handlername = when (cType.contentType) {
                "application/json" -> "applicationJson"
                "text/html" -> "textHtml"
                "text/plain" -> "textPlain"
                "text/csv" -> "textCsv"
                "text/css" -> "textCss"
                else -> cType.contentType
            }
            val type = when (cType.contentType) {
                "application/json" -> "Json"
                "text/html" -> "Html"
                "text/plain" -> "Text"
                "text/csv" -> "Csv"
                "text/css" -> "Css"
                else -> cType.contentType
            }
            responsestructure += "val res = responseHandler.$handlername($routename.$responsetitle.Response.$type.Helper) \n                            " +
                    "when(res) { \n                                "
            cType.code.forEach { code ->
                val name = getHttpStatusName(code.code)
                responsestructure += "is $routename.$responsetitle.Response.$type.$name -> {\n                                   "

                when (cType.contentType) {
                    "application/json" -> {
                        responsestructure += if (code.hasschema) {
                            "call.response.status(HttpStatusCode.$name)\n                                   " +
                                    "call.respond(res.value)\n                                " +
                                    "}\n                                "
                        } else {
                            "call.response.status(HttpStatusCode.$name)\n                                   " +
                                    "}\n                                "
                        }
                    }
                    "text/html" -> {
                        responsestructure += "call.respondHtml (HttpStatusCode.$name){\n                                     " +
                                "with(res){\n                                        " +
                                "value()\n                                     " +
                                "}\n                                   " +
                                "}\n                                " +
                                "}\n                                "

                    }
                    "text/plain" -> {
                        responsestructure += if (code.hasschema) {
                            "call.response.status(HttpStatusCode.$name)\n                                   " +
                                    "call.respondText(res.value)\n                                " +
                                    "}\n                                "
                        } else {
                            "call.response.status(HttpStatusCode.$name)\n                                   " +
                                    "}\n                                "
                        }
                    }
                    else -> {
                        responsestructure += if (code.hasschema) {
                            "call.response.status(HttpStatusCode.$name)\n                              " +
                                    "call.respond(res)\n                                " +
                                    "}\n                                "
                        } else {
                            "call.response.status(HttpStatusCode.$name)\n                              " +
                                    "}\n                                "
                        }
                    }
                }
            }
            responsestructure += "}\n                  " +
                    "}\n                  "

        }
    }
    // Generate Default Response Option
    if (contenttypes[0].contentType != "NoContentType" && contenttypes[0].contentType != "AllContentTypes") {
        responsestructure += "\"*/*\" -> {\n                            "
        val handlername = when (contenttypes[0].contentType) {
            "application/json" -> "applicationJson"
            "text/html" -> "textHtml"
            "text/plain" -> "textPlain"
            "text/csv" -> "textCsv"
            "text/css" -> "textCss"
            else -> contenttypes[0].contentType
        }
        val type = when (contenttypes[0].contentType) {
            "application/json" -> "Json"
            "text/html" -> "Html"
            "text/plain" -> "Text"
            "text/csv" -> "Csv"
            "text/css" -> "Css"
            else -> contenttypes[0].contentType
        }
        responsestructure += "val res = responseHandler.$handlername($routename.$responsetitle.Response.$type.Helper) \n                            " +
                "when(res) { \n                                "

        contenttypes[0].code.forEach { code ->
            val name = getHttpStatusName(code.code)
            responsestructure += "is $routename.$responsetitle.Response.$type.$name -> {\n                                   "

            when (contenttypes[0].contentType) {
                "application/json" -> {
                    responsestructure += if (code.hasschema) {
                        "call.response.status(HttpStatusCode.$name)\n                                   " +
                                "call.respond(res.value)\n                                " +
                                "}\n                                "
                    } else {
                        "call.response.status(HttpStatusCode.$name)\n                                   " +
                                "}\n                                "
                    }
                }
                "text/html" -> {
                    responsestructure += "call.respondHtml (HttpStatusCode.$name){\n                                     " +
                            "with(res){\n                                        " +
                            "value()\n                                     " +
                            "}\n                                   " +
                            "}\n                                " +
                            "}\n                                "

                }
                "text/plain" -> {
                    responsestructure += if (code.hasschema) {
                        "call.response.status(HttpStatusCode.$name)\n                                   " +
                                "call.respondText(res.value)\n                                " +
                                "}\n                                "
                    } else {
                        "call.response.status(HttpStatusCode.$name)\n                                   " +
                                "}\n                                "
                    }
                }
                else -> {
                    responsestructure += if (code.hasschema) {
                        "call.response.status(HttpStatusCode.$name)\n                              " +
                                "call.respond(res)\n                                " +
                                "}\n                                "
                    } else {
                        "call.response.status(HttpStatusCode.$name)\n                              " +
                                "}\n                                "
                    }
                }
            }
        }
        // Else Branch in Response, when no ContentType matches
        responsestructure += "}\n                  " +
                "}\n                  " +
                "else -> {\n                              " +
                "call.response.status(HttpStatusCode.UnsupportedMediaType)\n                              " +
                "call.respondText(\n                                  " +
                "\"The ContentType \$accept is not Supported here\"\n                              " +
                ") \n                       " +
                "}\n                 " +
                "}"
    }
    return responsestructure
}
