package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.DataType
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.Er2CncRoleDef
import org.tatrman.ttr.parser.model.Er2DbAttributeDef
import org.tatrman.ttr.parser.model.Er2DbEntityDef
import org.tatrman.ttr.parser.model.Er2DbRelationDef
import org.tatrman.ttr.parser.model.FkDef
import org.tatrman.ttr.parser.model.LocalizedStringListValue
import org.tatrman.ttr.parser.model.LocalizedStringValue
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.Reference
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.SearchHintsValue
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TableDef
import org.tatrman.ttr.parser.model.TaggedBlockValue
import org.tatrman.ttr.parser.model.TargetObjectValue
import org.tatrman.ttr.parser.model.ViewDef

// PropertyValue factories — the modeler model requires a `source` on every variant
// (and `parts` on IdValue); tests don't care about positions, so use UNKNOWN.
private val L = SourceLocation.UNKNOWN

private fun idv(path: String) = PropertyValue.IdValue(Reference(path), path.split("."), L)

private fun strv(s: String) = PropertyValue.StringValue(s, L)

private fun numv(n: Double) = PropertyValue.NumberValue(n, L)

private fun objv(entries: Map<String, PropertyValue>) = PropertyValue.ObjectValue(entries, L)

private fun listv(items: List<PropertyValue>) = PropertyValue.ListValue(items, L)

class TtrRendererSpec :
    StringSpec({

        "renders RoleDef as def role block" {
            val role =
                RoleDef(
                    name = "fact",
                    source = SourceLocation.UNKNOWN,
                    description = "A fact entity",
                    tags = listOf("core"),
                    label =
                        LocalizedStringValue(
                            byLanguage = mapOf("cs" to "Faktová entita", "en" to "Fact entity"),
                        ),
                )
            val rendered = TtrRenderer.renderDef(role)
            rendered shouldContain "def role fact"
            rendered shouldContain "cs: \"Faktová entita\""
            rendered shouldContain "en: \"Fact entity\""
            rendered shouldContain "description: \"A fact entity\""
        }

        "renders RoleDef without optional fields" {
            val role =
                RoleDef(
                    name = "structural",
                    source = SourceLocation.UNKNOWN,
                )
            val rendered = TtrRenderer.renderDef(role)
            rendered shouldContain "def role structural"
            rendered.shouldNotContain("description")
        }

        "renders TableDef with columns" {
            val table =
                TableDef(
                    name = "customers",
                    source = SourceLocation.UNKNOWN,
                    description = "Customer table",
                    tags = emptyList(),
                    primaryKey = listOf("id"),
                    columns =
                        listOf(
                            ColumnDef(
                                name = "id",
                                source = SourceLocation.UNKNOWN,
                                type = DataType(name = "int"),
                                isKey = true,
                            ),
                            ColumnDef(
                                name = "name",
                                source = SourceLocation.UNKNOWN,
                                type = DataType(name = "text"),
                            ),
                        ),
                    indices = emptyList(),
                    constraints = emptyList(),
                )
            val rendered = TtrRenderer.renderDef(table)
            rendered shouldContain "def table customers"
            rendered shouldContain "def column id"
            rendered shouldContain "def column name"
            rendered shouldContain "isKey: true"
        }

        "renders TableDef with empty columns" {
            val table =
                TableDef(
                    name = "empty_table",
                    source = SourceLocation.UNKNOWN,
                    columns = emptyList(),
                )
            val rendered = TtrRenderer.renderDef(table)
            rendered shouldContain "def table empty_table"
        }

        "renders Er2DbEntityDef with table target" {
            val def =
                Er2DbEntityDef(
                    name = "customer_map",
                    source = SourceLocation.UNKNOWN,
                    entity = Reference("er.customer"),
                    target =
                        TargetObjectValue(
                            obj = objv(mapOf("table" to idv("db.dbo.customers"))),
                            source = SourceLocation.UNKNOWN,
                        ),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "def er2db_entity customer_map"
            rendered shouldContain "entity: er.customer"
            rendered shouldContain "table: db.dbo.customers"
        }

        "renders Er2DbEntityDef with query target" {
            val def =
                Er2DbEntityDef(
                    name = "filtered_sales",
                    source = SourceLocation.UNKNOWN,
                    entity = Reference("er.sales"),
                    target =
                        TargetObjectValue(
                            obj = objv(mapOf("query" to idv("query.query.sales_filter"))),
                            source = SourceLocation.UNKNOWN,
                        ),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "def er2db_entity filtered_sales"
            rendered shouldContain "query: query.query.sales_filter"
        }

        "renders RelationDef with from/to" {
            val def =
                RelationDef(
                    name = "customer_orders",
                    source = SourceLocation.UNKNOWN,
                    from = idv("er.customer"),
                    to = idv("er.order"),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "def relation customer_orders"
            rendered shouldContain "from: er.customer"
            rendered shouldContain "to: er.order"
        }

        "renders Er2CncRoleDef" {
            val def =
                Er2CncRoleDef(
                    name = "entity_fact",
                    source = SourceLocation.UNKNOWN,
                    entity = Reference("er.sales"),
                    role = Reference("cnc.role.fact"),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "def er2cnc_role entity_fact"
            rendered shouldContain "entity: er.sales"
            rendered shouldContain "role: cnc.role.fact"
        }

        "renders Er2DbAttributeDef with column target" {
            val def =
                Er2DbAttributeDef(
                    name = "customer_name_attr",
                    source = SourceLocation.UNKNOWN,
                    attribute = Reference("er.customer.name"),
                    target =
                        TargetObjectValue(
                            obj = objv(mapOf("column" to idv("db.dbo.customers.name"))),
                            source = SourceLocation.UNKNOWN,
                        ),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "def er2db_attribute customer_name_attr"
            rendered shouldContain "attribute: er.customer.name"
            rendered shouldContain "column: db.dbo.customers.name"
        }

        "renders Er2DbAttributeDef with expression target" {
            val def =
                Er2DbAttributeDef(
                    name = "full_name_expr",
                    source = SourceLocation.UNKNOWN,
                    attribute = Reference("er.person.full_name"),
                    target =
                        TargetObjectValue(
                            obj = objv(mapOf("expression" to strv("CONCAT(first_name, ' ', last_name)"))),
                            source = SourceLocation.UNKNOWN,
                        ),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "def er2db_attribute full_name_expr"
            rendered shouldContain "expression: \"CONCAT(first_name, ' ', last_name)\""
        }

        "renders Er2DbRelationDef" {
            val def =
                Er2DbRelationDef(
                    name = "customer_orders_rel",
                    source = SourceLocation.UNKNOWN,
                    relation = Reference("er.customer_orders"),
                    fk = Reference("db.dbo.fk_customer_orders"),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "def er2db_relation customer_orders_rel"
            rendered shouldContain "relation: er.customer_orders"
            rendered shouldContain "fk: db.dbo.fk_customer_orders"
        }

        "renders TableDef with primaryKey" {
            val table =
                TableDef(
                    name = "orders",
                    source = SourceLocation.UNKNOWN,
                    primaryKey = listOf("order_id", "line_num"),
                    columns = emptyList(),
                )
            val rendered = TtrRenderer.renderDef(table)
            rendered shouldContain "primaryKey: [\"order_id\", \"line_num\"]"
        }

        "renders TableDef with description containing special chars" {
            val table =
                TableDef(
                    name = "test",
                    source = SourceLocation.UNKNOWN,
                    description = "Has a quote: \" and colon: : and comma,",
                    columns = emptyList(),
                )
            val rendered = TtrRenderer.renderDef(table)
            rendered shouldContain "description:"
            rendered.shouldNotContain("\\n")
            rendered.shouldNotContain("\"\"")
        }

        "renders RelationDef with cardinality object" {
            val def =
                RelationDef(
                    name = "rel",
                    source = SourceLocation.UNKNOWN,
                    cardinality =
                        objv(
                            mapOf(
                                "fromMin" to numv(0.0),
                                "fromMax" to numv(1.0),
                                "toMin" to numv(0.0),
                                "toMax" to numv(-1.0),
                            ),
                        ),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "cardinality:"
            rendered shouldContain "fromMin"
            rendered shouldContain "fromMax"
        }

        "renders multiple definitions sorted by kind then name" {
            val definitions =
                listOf(
                    Er2DbEntityDef(
                        name = "zzz_last",
                        source = SourceLocation.UNKNOWN,
                        entity = Reference("er.x"),
                        target =
                            TargetObjectValue(
                                obj = objv(emptyMap()),
                                source = SourceLocation.UNKNOWN,
                            ),
                    ),
                    RoleDef(
                        name = "aaa_first",
                        source = SourceLocation.UNKNOWN,
                    ),
                )
            val rendered = TtrRenderer.render(definitions)
            val idxA = rendered.indexOf("def role aaa_first")
            val idxZ = rendered.indexOf("def er2db_entity zzz_last")
            rendered shouldContain "def role aaa_first"
            rendered shouldContain "def er2db_entity zzz_last"
            (idxA < idxZ) shouldBe true
        }

        "RoleDef round-trips through TtrLoader" {
            val role =
                RoleDef(
                    name = "test_role",
                    source = SourceLocation.UNKNOWN,
                    description = "A test role",
                    label = LocalizedStringValue(byLanguage = mapOf("en" to "Test Role")),
                )
            val rendered = TtrRenderer.renderDef(role)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
            val parsed = result.definitions.single() as RoleDef
            parsed.name shouldBe "test_role"
            parsed.description shouldBe "A test role"
            parsed.label?.byLanguage?.get("en") shouldBe "Test Role"
        }

        "TableDef round-trips through TtrLoader" {
            val intType = DataType("int")
            val table =
                TableDef(
                    name = "test_table",
                    source = SourceLocation.UNKNOWN,
                    primaryKey = listOf("id"),
                    columns =
                        listOf(
                            ColumnDef(
                                name = "id",
                                source = SourceLocation.UNKNOWN,
                                type = intType,
                                isKey = true,
                            ),
                        ),
                )
            val rendered = TtrRenderer.renderDef(table)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
            val parsed = result.definitions.single() as TableDef
            parsed.name shouldBe "test_table"
            parsed.primaryKey shouldBe listOf("id")
            parsed.columns.size shouldBe 1
            parsed.columns[0].name shouldBe "id"
            parsed.columns[0].isKey shouldBe true
        }

        "ViewDef round-trips through TtrLoader" {
            val view =
                ViewDef(
                    name = "test_view",
                    source = SourceLocation.UNKNOWN,
                    columns = emptyList(),
                )
            val rendered = TtrRenderer.renderDef(view)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
        }

        "QueryDef with a tagged sourceText block round-trips through TtrRenderer" {
            // embedded-sql 1.5.1: parse → render → re-parse, then assert the
            // TaggedBlockValue is structurally equal (modulo SourceLocation).
            val src = "def query q {\n  sourceText: \"\"\"ms-sql\nSELECT 1\n\"\"\"\n}"
            val parsed = TtrLoader.parseString(src)
            parsed.ok shouldBe true
            val original = (parsed.definitions[0] as QueryDef).sourceTextBlock as TaggedBlockValue

            val rendered = TtrRenderer.renderDef(parsed.definitions[0])
            val reparsed = TtrLoader.parseString(rendered)
            reparsed.ok shouldBe true
            val roundTripped = (reparsed.definitions[0] as QueryDef).sourceTextBlock as TaggedBlockValue

            stripSource(roundTripped) shouldBe stripSource(original)
            roundTripped.tag shouldBe "ms-sql"
            roundTripped.dialect shouldBe "tsql"
            roundTripped.value shouldBe "SELECT 1"
        }

        "RelationDef round-trips through TtrLoader" {
            val relation =
                RelationDef(
                    name = "test_rel",
                    source = SourceLocation.UNKNOWN,
                    from = idv("er.entity.a"),
                    to = idv("er.entity.b"),
                    cardinality =
                        objv(
                            mapOf(
                                "fromMin" to numv(0.0),
                                "fromMax" to numv(1.0),
                                "toMin" to numv(0.0),
                                "toMax" to numv(-1.0),
                            ),
                        ),
                    join = emptyList(),
                )
            val rendered = TtrRenderer.renderDef(relation)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
        }

        "Er2DbEntityDef round-trips through TtrLoader" {
            val mapping =
                Er2DbEntityDef(
                    name = "test_entity_map",
                    source = SourceLocation.UNKNOWN,
                    entity = Reference("er.entity.test"),
                    target =
                        TargetObjectValue(
                            obj = objv(mapOf("table" to idv("db.dbo.test"))),
                            source = SourceLocation.UNKNOWN,
                        ),
                )
            val rendered = TtrRenderer.renderDef(mapping)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
        }

        "Er2DbAttributeDef round-trips through TtrLoader" {
            val mapping =
                Er2DbAttributeDef(
                    name = "test_attr_map",
                    source = SourceLocation.UNKNOWN,
                    attribute = Reference("er.entity.test.id"),
                    target =
                        TargetObjectValue(
                            obj = objv(mapOf("column" to idv("db.dbo.test.id"))),
                            source = SourceLocation.UNKNOWN,
                        ),
                )
            val rendered = TtrRenderer.renderDef(mapping)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
        }

        "Er2DbRelationDef round-trips through TtrLoader" {
            val mapping =
                Er2DbRelationDef(
                    name = "test_rel_map",
                    source = SourceLocation.UNKNOWN,
                    relation = Reference("er.relation.test"),
                    fk = Reference("db.dbo.test_fk"),
                )
            val rendered = TtrRenderer.renderDef(mapping)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
        }

        "FkDef round-trips through TtrLoader" {
            val fk =
                FkDef(
                    name = "test_fk",
                    source = SourceLocation.UNKNOWN,
                    from = listv(listOf(idv("db.dbo.a.id"))),
                    to = listv(listOf(idv("db.dbo.b.id"))),
                )
            val rendered = TtrRenderer.renderDef(fk)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
        }

        "Er2CncRoleDef round-trips through TtrLoader" {
            val mapping =
                Er2CncRoleDef(
                    name = "test_cnc_role",
                    source = SourceLocation.UNKNOWN,
                    entity = Reference("er.entity.test"),
                    role = Reference("cnc.role.fact"),
                )
            val rendered = TtrRenderer.renderDef(mapping)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
        }

        "QueryDef with typed parameters round-trips through TtrLoader" {
            val query =
                QueryDef(
                    name = "topCustomers",
                    source = SourceLocation.UNKNOWN,
                    language = "SQL",
                    sourceText = "select er.entity.zakaznik",
                    parameters =
                        listOf(
                            objv(
                                linkedMapOf(
                                    "name" to idv("limit"),
                                    "type" to idv("int"),
                                    "label" to strv("Počet zákazníků"),
                                ),
                            ),
                            objv(
                                linkedMapOf(
                                    "name" to idv("region"),
                                    "type" to idv("text"),
                                ),
                            ),
                        ),
                )
            val rendered = TtrRenderer.renderDef(query)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
            val parsed = result.definitions.single() as QueryDef
            // Compare structurally ignoring source (the parsed values carry real positions).
            parsed.parameters.map { stripSource(it) } shouldBe query.parameters.map { stripSource(it) }
        }

        "renderFile emits schema directive then definitions" {
            val role = RoleDef(name = "fact", source = SourceLocation.UNKNOWN)
            val rendered = TtrRenderer.renderFile("cnc", "role", listOf(role))
            rendered shouldContain "model cnc schema role"
            rendered shouldContain "def role fact"
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.definitions
                .single()
                .let { it as RoleDef }
                .name shouldBe "fact"
        }

        "renderFile with null schema omits schema clause" {
            val entity = EntityDef(name = "MyEntity", source = SourceLocation.UNKNOWN)
            val rendered = TtrRenderer.renderFile("er", null, listOf(entity))
            rendered shouldContain "model er"
            rendered.shouldNotContain("namespace")
        }

        "renderString uses triple-quoted form for strings containing newline" {
            val raw = "line one\nline two"
            val rendered = TtrRenderer.renderString(raw)
            rendered shouldBe "\"\"\"line one\nline two\"\"\""
        }

        "renderString falls back to escaped form when content contains triple-quote" {
            val raw = "has \"\"\" inside"
            val rendered = TtrRenderer.renderString(raw)
            rendered shouldContain "\\\""
            rendered.shouldNotContain("\"\"\"")
        }

        // entity-level `roles:` shorthand must round-trip.
        "EntityDef with roles shorthand round-trips through TtrLoader" {
            val entity =
                EntityDef(
                    name = "sales",
                    source = SourceLocation.UNKNOWN,
                    roles = listOf(Reference("cnc.role.fact"), Reference("cnc.role.transaction")),
                )
            val rendered = TtrRenderer.renderDef(entity)
            rendered shouldContain "roles: [cnc.role.fact, cnc.role.transaction]"

            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            val parsed = result.definitions.single() as EntityDef
            parsed.name shouldBe "sales"
            parsed.roles.map { it.path } shouldBe listOf("cnc.role.fact", "cnc.role.transaction")
        }

        "EntityDef without roles renders no roles fragment (back-compat)" {
            val entity = EntityDef(name = "plain", source = SourceLocation.UNKNOWN)
            val rendered = TtrRenderer.renderDef(entity)
            rendered.shouldNotContain("roles:")
        }

        "EntityDef with multiline description round-trips through TtrLoader" {
            val entity =
                EntityDef(
                    name = "MultiLine",
                    source = SourceLocation.UNKNOWN,
                    description = "First line.\nSecond line.",
                )
            val rendered = TtrRenderer.renderDef(entity)
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            val parsed = result.definitions.single() as EntityDef
            parsed.name shouldBe "MultiLine"
            parsed.description?.contains("First line") shouldBe true
            parsed.description?.contains("Second line") shouldBe true
        }

        "attribute searchable renders inside the search block, not top-level" {
            val def =
                AttributeDef(
                    name = "x",
                    source = SourceLocation.UNKNOWN,
                    search = SearchHintsValue(searchable = true),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "search {"
            rendered shouldContain "searchable: true"
            rendered shouldNotContain " searchable: true,"
            val count = Regex("search \\{").findAll(rendered).count()
            count shouldBe 1
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            result.errors.isEmpty() shouldBe true
            val parsed = result.definitions.single() as AttributeDef
            parsed.search.searchable shouldBe true
        }

        "column searchable renders inside the search block" {
            val def =
                ColumnDef(
                    name = "c",
                    source = SourceLocation.UNKNOWN,
                    type = DataType("text"),
                    search = SearchHintsValue(searchable = true),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "search {"
            rendered shouldContain "searchable: true"
            rendered shouldNotContain " searchable: true,"
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            val parsed = result.definitions.single() as ColumnDef
            parsed.search.searchable shouldBe true
        }

        "searchable merges with other search content into one block" {
            val def =
                AttributeDef(
                    name = "x",
                    source = SourceLocation.UNKNOWN,
                    search =
                        SearchHintsValue(
                            searchable = true,
                            keywords = LocalizedStringListValue(byLanguage = mapOf("cs" to listOf("klicove slovo"))),
                        ),
                )
            val rendered = TtrRenderer.renderDef(def)
            val count = Regex("search \\{").findAll(rendered).count()
            count shouldBe 1
            rendered shouldContain "searchable: true"
            rendered shouldContain "keywords"
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            val parsed = result.definitions.single() as AttributeDef
            parsed.search.searchable shouldBe true
            parsed.search.keywords.byLanguage["cs"] shouldBe listOf("klicove slovo")
        }

        "fuzzy renders only inside the search block" {
            val def =
                AttributeDef(
                    name = "x",
                    source = SourceLocation.UNKNOWN,
                    search = SearchHintsValue(searchable = true, fuzzy = true),
                )
            val rendered = TtrRenderer.renderDef(def)
            rendered shouldContain "search {"
            rendered shouldContain "fuzzy: true"
            rendered.shouldNotContain(" fuzzy: true,")
            val result = TtrLoader.parseString(rendered)
            result.ok shouldBe true
            val parsed = result.definitions.single() as AttributeDef
            parsed.search.fuzzy shouldBe true
        }

        "renderFile emits package then imports then schema then defs" {
            val role = RoleDef(name = "fact", source = SourceLocation.UNKNOWN)
            val imports = listOf("cnc.sales.Product", "er.sales.*")
            val rendered =
                TtrRenderer.renderFile(
                    schemaCode = "cnc",
                    namespace = "role",
                    definitions = listOf(role),
                    packageName = "cnc",
                    imports = imports,
                )
            val lines = rendered.lines()
            val pkgLine = lines.indexOfFirst { it.startsWith("package cnc") }
            val importLines =
                lines.filter { it.startsWith("import ") }
            val schemaLine = lines.indexOfFirst { it.startsWith("model cnc schema role") }
            val defLine = lines.indexOfFirst { it.contains("def role fact") }
            pkgLine shouldBe 0
            importLines[0] shouldBe "import cnc.sales.Product"
            importLines[1] shouldBe "import er.sales.*"
            // No blank line between package/imports and schema per grammar document rule
            schemaLine shouldBe 3
            defLine shouldBe 5
        }

        "renderFile emits package then single import then schema then defs" {
            val entity = EntityDef(name = "MyEntity", source = SourceLocation.UNKNOWN)
            val rendered =
                TtrRenderer.renderFile(
                    schemaCode = "er",
                    namespace = null,
                    definitions = listOf(entity),
                    packageName = "sales",
                    imports = listOf("db.dbo.customers"),
                )
            val lines = rendered.lines()
            lines[0] shouldBe "package sales"
            lines[1] shouldBe "import db.dbo.customers"
            lines[2] shouldBe "model er"
            lines[4] shouldContain "def entity MyEntity"
        }

        "renderFile with no package emits imports then schema then defs" {
            val role = RoleDef(name = "fact", source = SourceLocation.UNKNOWN)
            val rendered =
                TtrRenderer.renderFile(
                    schemaCode = "cnc",
                    namespace = "role",
                    definitions = listOf(role),
                    packageName = null,
                    imports = listOf("er.sales.*"),
                )
            val lines = rendered.lines()
            lines[0] shouldBe "import er.sales.*"
            lines[1] shouldBe "model cnc schema role"
            lines[3] shouldContain "def role fact"
        }

        "renderFile with no imports skips import section" {
            val entity = EntityDef(name = "MyEntity", source = SourceLocation.UNKNOWN)
            val rendered =
                TtrRenderer.renderFile(
                    schemaCode = "er",
                    namespace = null,
                    definitions = listOf(entity),
                    packageName = "sales",
                    imports = emptyList(),
                )
            val lines = rendered.lines()
            lines[0] shouldBe "package sales"
            lines[1] shouldBe "model er"
            lines[3] shouldContain "def entity MyEntity"
        }

        "renderFile with no package and no imports emits only schema and defs" {
            val role = RoleDef(name = "fact", source = SourceLocation.UNKNOWN)
            val rendered = TtrRenderer.renderFile("cnc", "role", listOf(role), null, emptyList())
            val lines = rendered.lines()
            lines[0] shouldBe "model cnc schema role"
            lines[2] shouldContain "def role fact"
        }
    })

/** Recursively rewrites every PropertyValue source to UNKNOWN so two trees can be compared by structure. */
private fun stripSource(v: PropertyValue): PropertyValue =
    when (v) {
        is PropertyValue.StringValue -> v.copy(source = L)
        is PropertyValue.TripleStringValue -> v.copy(source = L)
        is PropertyValue.NumberValue -> v.copy(source = L)
        is PropertyValue.BoolValue -> v.copy(source = L)
        is PropertyValue.NullValue -> v.copy(source = L)
        is PropertyValue.IdValue -> v.copy(ref = v.ref.copy(source = L), source = L)
        is PropertyValue.ListValue -> PropertyValue.ListValue(v.items.map { stripSource(it) }, L)
        is PropertyValue.ObjectValue -> PropertyValue.ObjectValue(v.entries.mapValues { stripSource(it.value) }, L)
        is PropertyValue.FunctionCall -> PropertyValue.FunctionCall(v.name, v.args.map { stripSource(it) }, L)
        is TaggedBlockValue -> v.copy(tagSource = L, valueSource = L, source = L)
    }
