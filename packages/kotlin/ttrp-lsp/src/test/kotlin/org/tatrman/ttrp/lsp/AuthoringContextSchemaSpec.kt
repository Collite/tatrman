package org.tatrman.ttrp.lsp

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** The committed example instances (docs) must validate against the committed schema (T4.2.5). */
class AuthoringContextSchemaSpec :
    StringSpec({
        val schemaPath = Path.of("../../../docs/ttr-p/architecture/authoring-context.schema.json")
        val examplesDir = Path.of("../../../docs/ttr-p/architecture/examples/authoring-context")

        listOf("hero-at-cursor.json", "bare-sql-no-cursor.json").forEach { name ->
            "example $name validates against authoring-context.schema.json" {
                val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                val schema = factory.getSchema(Files.readString(schemaPath))
                val instance = Files.readString(examplesDir.resolve(name))
                schema.validate(instance, InputFormat.JSON).map { it.message } shouldBe emptyList()
            }
        }
    })
