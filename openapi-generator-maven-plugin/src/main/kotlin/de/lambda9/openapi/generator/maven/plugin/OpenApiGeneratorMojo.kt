package de.lambda9.openapi.generator.maven.plugin

import de.lambda9.openapi.generator.generateApi
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

@Mojo(name = "openapi")
open class OpenApiGeneratorMojo: AbstractMojo() {

    /**
     * The Maven project.
     */
    @Parameter(
        property = "project",
        required = true,
        readonly = true
    )
    var project: MavenProject? = null

    @Parameter(property = "specFile")
    var specFile: String? = null

    @Parameter(property = "packageName")
    var packageName: String? = null

    @Parameter(property = "targetDirectory")
    var targetDirectory: String? = null

    @Parameter(property = "specValidation")
    var specValidation: Boolean = false

    override fun execute() {
        generateApi(
            specFile = File(specFile!!),
            validation = specValidation,
            target =  targetDirectory!!,
            packageName = packageName!!
        )
        project?.addCompileSourceRoot(targetDirectory)
    }
}