import de.lambda9.openapi.generator.generateApi
import java.io.File

fun main() {
    generateApi(File("specfiles/twitterwall.yaml"), false, "target",  "de.lambda9")
}