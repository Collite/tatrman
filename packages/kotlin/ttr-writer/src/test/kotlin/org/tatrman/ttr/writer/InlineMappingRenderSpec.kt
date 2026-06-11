package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.MappingColumnBareId
import org.tatrman.ttr.parser.model.MappingColumnEntry
import org.tatrman.ttr.parser.model.MappingColumnObject
import org.tatrman.ttr.parser.model.MappingPropertyBareId
import org.tatrman.ttr.parser.model.MappingPropertyBlock
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.Reference
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TargetObjectValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Stage 1 (yaml-converter-inline-split) — `TtrRenderer` must emit the v2.1 inline
 * `mapping:` property on entity / attribute / relation defs. Golden shapes:
 * `samples/2.1/er.ttr` (artikl = entity-level block; produkt = per-attribute
 * short forms). RED until `renderMapping` is wired in.
 */
private val L = SourceLocation.UNKNOWN

private fun idv(path: String) = PropertyValue.IdValue(Reference(path), path.split("."), L)

private fun objv(entries: Map<String, PropertyValue>) = PropertyValue.ObjectValue(entries, L)

class InlineMappingRenderSpec :
    StringSpec({

        "1.1 attribute short form: mapping: <bareId>" {
            val attr =
                AttributeDef(
                    name = "id_produktu",
                    source = L,
                    isKey = true,
                    mapping = MappingPropertyBareId(Reference("IDSKUPZBOZI"), L),
                )
            TtrRenderer.renderDef(attr) shouldContain "mapping: IDSKUPZBOZI"
        }

        "1.2 attribute block form: mapping: { target: { column: ... } }" {
            val attr =
                AttributeDef(
                    name = "název_produktu",
                    source = L,
                    mapping =
                        MappingPropertyBlock(
                            target = TargetObjectValue(objv(mapOf("column" to idv("NAZEV_SKUPZBOZI"))), L),
                            source = L,
                        ),
                )
            TtrRenderer.renderDef(attr) shouldContain "mapping: { target: { column: NAZEV_SKUPZBOZI } }"
        }

        "1.3 entity-level block with target + mixed columns map" {
            val entity =
                EntityDef(
                    name = "artikl",
                    source = L,
                    mapping =
                        MappingPropertyBlock(
                            target = TargetObjectValue(objv(mapOf("table" to idv("db.dbo.QZBOZI_DF"))), L),
                            columns =
                                listOf(
                                    // bare-id form: plain column
                                    MappingColumnEntry("id_artiklu", MappingColumnBareId(Reference("IDZBOZI"), L), L),
                                    // object form: explicit target
                                    MappingColumnEntry(
                                        "kód_artiklu",
                                        MappingColumnObject(objv(mapOf("target" to idv("KOD_ZBOZI"))), L),
                                        L,
                                    ),
                                    // object form: nested column target
                                    MappingColumnEntry(
                                        "název_artiklu",
                                        MappingColumnObject(objv(mapOf("target" to objv(mapOf("column" to idv("NAZEV_ZBOZI"))))), L),
                                        L,
                                    ),
                                ),
                            source = L,
                        ),
                )
            val r = TtrRenderer.renderDef(entity)
            r shouldContain "mapping: { target: { table: db.dbo.QZBOZI_DF }, columns: {"
            r shouldContain "id_artiklu: IDZBOZI"
            r shouldContain "kód_artiklu: { target: KOD_ZBOZI }"
            r shouldContain "název_artiklu: { target: { column: NAZEV_ZBOZI } }"
        }

        "1.4 relation FK short form: mapping: <fkRef>" {
            val rel =
                RelationDef(
                    name = "artikl_produkt",
                    source = L,
                    from = idv("er.entity.artikl"),
                    to = idv("er.entity.produkt"),
                    mapping = MappingPropertyBareId(Reference("db.dbo.fk_artikl_produkt"), L),
                )
            TtrRenderer.renderDef(rel) shouldContain "mapping: db.dbo.fk_artikl_produkt"
        }

        "1.4b relation FK wrapped block form: mapping: { fk: <fkRef> }" {
            val rel =
                RelationDef(
                    name = "artikl_podprodukt",
                    source = L,
                    mapping = MappingPropertyBlock(fk = Reference("db.dbo.fk_artikl_podprodukt"), source = L),
                )
            TtrRenderer.renderDef(rel) shouldContain "mapping: { fk: db.dbo.fk_artikl_podprodukt }"
        }

        "1.5 round-trip samples/2.1/er.ttr: inline mappings survive + render is a fixed point" {
            val src = Files.readString(locateSample("samples/2.1/er.ttr"))
            val parsed1 = TtrLoader.parseString(src)
            parsed1.ok shouldBe true

            val text1 = TtrRenderer.render(parsed1.definitions)
            val parsed2 = TtrLoader.parseString(text1)
            parsed2.ok shouldBe true

            // The inline mappings must survive parse → render → parse, not be dropped.
            val artikl = parsed2.definitions.filterIsInstance<EntityDef>().first { it.name == "artikl" }
            artikl.mapping.shouldBeInstanceOf<MappingPropertyBlock>()
            val produkt = parsed2.definitions.filterIsInstance<EntityDef>().first { it.name == "produkt" }
            produkt.attributes.first { it.name == "id_produktu" }.mapping.shouldBeInstanceOf<MappingPropertyBareId>()
            produkt.attributes.first { it.name == "název_produktu" }.mapping.shouldBeInstanceOf<MappingPropertyBlock>()
            val rel = parsed2.definitions.filterIsInstance<RelationDef>().first { it.name == "artikl_produkt" }
            rel.mapping.shouldBeInstanceOf<MappingPropertyBareId>()
            val relW = parsed2.definitions.filterIsInstance<RelationDef>().first { it.name == "artikl_podprodukt" }
            relW.mapping.shouldBeInstanceOf<MappingPropertyBlock>()

            // render∘parse is a fixed point — no structural drift through the round trip.
            val text2 = TtrRenderer.render(parsed2.definitions)
            text2 shouldBe text1
        }
    })

/** Walk up from the test working dir to find a repo-relative sample file. */
private fun locateSample(relative: String): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve(relative)
        if (Files.isRegularFile(candidate)) return candidate
        dir = dir.parent
    }
    error("could not locate $relative from ${Paths.get("").toAbsolutePath()}")
}
