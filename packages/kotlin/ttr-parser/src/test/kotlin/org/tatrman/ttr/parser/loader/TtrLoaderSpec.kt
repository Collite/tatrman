package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.Er2CncRoleDef
import org.tatrman.ttr.parser.model.ModelDef
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.QueryDef
import org.tatrman.ttr.parser.model.RelationDef
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.TableDef

class TtrLoaderSpec :
    StringSpec({

        "parses an empty document" {
            val r = TtrLoader.parseString("")
            r.ok shouldBe true
            r.definitions shouldHaveSize 0
            r.schemaDirective shouldBe null
        }

        "parses a model definition" {
            val r =
                TtrLoader.parseString(
                    """
                    def project erp_v1 {
                        description: "ERP v1 model"
                        version: "1.0.0"
                        tags: ["v1", "erp"]
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.definitions shouldHaveSize 1
            val m = r.definitions[0]
            m.shouldBeInstanceOf<ModelDef>()
            m.name shouldBe "erp_v1"
            m.description shouldBe "ERP v1 model"
            m.version shouldBe "1.0.0"
            m.tags shouldBe listOf("v1", "erp")
        }

        "parses a schema directive + a table with inline columns" {
            val r =
                TtrLoader.parseString(
                    """
                    model db schema dbo

                    def table customers {
                        description: "Customer master"
                        primaryKey: ["id"]
                        columns: [
                            def column id { type: int, isKey: true },
                            def column name { type: text }
                        ]
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.schemaDirective?.schemaCode shouldBe "db"
            r.schemaDirective?.namespace shouldBe "dbo"
            val t = r.definitions[0]
            t.shouldBeInstanceOf<TableDef>()
            t.name shouldBe "customers"
            t.primaryKey shouldBe listOf("id")
            t.columns shouldHaveSize 2
            t.columns[0].name shouldBe "id"
            t.columns[0].type?.name shouldBe "int"
            t.columns[0].isKey shouldBe true
            t.columns[1].type?.name shouldBe "text"
        }

        "parses accented identifiers (Czech/Latin Extended letters)" {
            val r =
                TtrLoader.parseString(
                    """
                    def table uživatelé {
                        description: "Uživatelé systému"
                        primaryKey: ["id_uživatele"]
                        columns: [
                            def column id_uživatele { type: int, isKey: true },
                            def column jméno { type: text },
                            def column příjmení { type: text }
                        ]
                    }

                    def entity objednávka {
                        description: "Objednávka zákazníka"
                        labelPlural: "Objednávky"
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.definitions shouldHaveSize 2

            val table = r.definitions[0] as TableDef
            table.name shouldBe "uživatelé"
            table.description shouldBe "Uživatelé systému"
            table.primaryKey shouldBe listOf("id_uživatele")
            table.columns[0].name shouldBe "id_uživatele"
            table.columns[1].name shouldBe "jméno"
            table.columns[2].name shouldBe "příjmení"

            val entity = r.definitions[1] as EntityDef
            entity.name shouldBe "objednávka"
            entity.description shouldBe "Objednávka zákazníka"
            entity.labelPlural shouldBe "Objednávky"
        }

        "parses a structured physical type with length / precision" {
            val r =
                TtrLoader.parseString(
                    """
                    def column total {
                        type: { type: decimal, length: 19, precision: 5 }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val c = r.definitions[0] as ColumnDef
            c.type?.name shouldBe "decimal"
            c.type?.length shouldBe 19
            c.type?.precision shouldBe 5
        }

        "parses an entity with inline attributes" {
            val r =
                TtrLoader.parseString(
                    """
                    model er

                    def entity Customer {
                        description: "A customer"
                        labelPlural: "Customers"
                        nameAttribute: name
                        aliases: ["client", "buyer"]
                        attributes: [
                            def attribute id { type: int, isKey: true },
                            def attribute name { type: text }
                        ]
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val e = r.definitions[0] as EntityDef
            e.name shouldBe "Customer"
            e.labelPlural shouldBe "Customers"
            e.nameAttribute?.path shouldBe "name"
            e.aliases shouldBe listOf("client", "buyer")
            e.attributes shouldHaveSize 2
            (e.attributes[0] as AttributeDef).isKey shouldBe true
        }

        "parses a triple-quoted query source with dedent" {
            val r =
                TtrLoader.parseString(
                    """
                    model query

                    def query topCustomers {
                        language: SQL
                        sourceText: ${'"'}${'"'}${'"'}
                            SELECT id, name
                            FROM customers
                            ORDER BY id DESC
                        ${'"'}${'"'}${'"'}
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val q = r.definitions[0] as QueryDef
            q.language shouldBe "SQL"
            // After dedent, the sourceText should NOT have the leading spaces.
            q.sourceText!!.shouldContain("SELECT id, name")
            q.sourceText!!.lines().none { it.startsWith("    SELECT") } shouldBe true
        }

        "surfaces a syntax error with file:line:column" {
            val r = TtrLoader.parseString("def project { description: \"x\" }", fileLabel = "test.ttr")
            r.ok shouldBe false
            r.errors shouldHaveSize 1
            r.errors[0].file shouldBe "test.ttr"
            r.errors[0].line shouldBe 1
        }

        "rejects unknown property kinds at the lexer/parser layer" {
            // `notARealProp` is not a registered property keyword; the parser
            // surfaces a syntax error rather than silently swallowing it.
            val r =
                TtrLoader.parseString(
                    """
                    def project X { notARealProp: "y" }
                    """.trimIndent(),
                )
            r.ok shouldBe false
        }

        "comments are ignored" {
            val r =
                TtrLoader.parseString(
                    """
                    // a line comment
                    /* a
                       block
                       comment */
                    def project M { description: "x" }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.definitions shouldHaveSize 1
        }

        "= and : are interchangeable as property separators" {
            val r =
                TtrLoader.parseString(
                    """
                    def project X {
                        description = "with equals"
                        version: "1"
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            (r.definitions[0] as ModelDef).description shouldBe "with equals"
        }

        // ----- Phase 2.2 — cnc.role + value_labels + display_label -----

        "parses a def role with a localised label" {
            val r =
                TtrLoader.parseString(
                    """
                    model cnc

                    def role fact {
                        label { cs: "Faktová entita", en: "Fact entity" }
                        description: "Measurable event entity."
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.schemaDirective?.schemaCode shouldBe "cnc"
            val role = r.definitions[0] as RoleDef
            role.name shouldBe "fact"
            role.description shouldContain "Measurable"
            role.label?.byLanguage?.get("cs") shouldBe "Faktová entita"
            role.label?.byLanguage?.get("en") shouldBe "Fact entity"
        }

        "parses a long-form def er2cnc_role mapping" {
            val r =
                TtrLoader.parseString(
                    """
                    def er2cnc_role objednavka_is_fact {
                        entity: er.entity.objednavka
                        role: cnc.cnc.role.fact
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val m = r.definitions[0] as Er2CncRoleDef
            m.name shouldBe "objednavka_is_fact"
            m.entity?.path shouldBe "er.entity.objednavka"
            m.role?.path shouldBe "cnc.cnc.role.fact"
        }

        "preserves the short-form role path verbatim (no resolution in TtrLoader.parseString)" {
            val r =
                TtrLoader.parseString(
                    """
                    def er2cnc_role objednavka_is_fact {
                        entity: er.entity.objednavka
                        role: cnc.role.fact
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val m = r.definitions[0] as Er2CncRoleDef
            m.name shouldBe "objednavka_is_fact"
            m.role?.path shouldBe "cnc.role.fact"
        }

        "parses an entity with the roles shorthand" {
            val r =
                TtrLoader.parseString(
                    """
                    def entity Objednavka {
                        roles: [fact, transaction]
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val e = r.definitions[0] as EntityDef
            e.roles.map { it.path } shouldBe listOf("fact", "transaction")
        }

        "parses an entity with displayLabel and an attribute with valueLabels + displayLabel" {
            val r =
                TtrLoader.parseString(
                    """
                    def entity Zakaznik {
                        displayLabel { cs: "Zákazník", en: "Customer" }
                        attributes: [
                            def attribute STAV {
                                type: int
                                displayLabel { cs: "Stav", en: "Status" }
                                valueLabels {
                                    "1": { cs: "Aktivní", en: "Active" }
                                    "2": { cs: "Neaktivní", en: "Inactive" }
                                    "3": { cs: "Pozastavený", en: "Suspended" }
                                }
                            }
                        ]
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val e = r.definitions[0] as EntityDef
            e.displayLabel?.byLanguage?.get("cs") shouldBe "Zákazník"
            e.displayLabel?.byLanguage?.get("en") shouldBe "Customer"
            val a = e.attributes.single()
            a.name shouldBe "STAV"
            a.displayLabel?.byLanguage?.get("cs") shouldBe "Stav"
            a.valueLabels.keys shouldBe setOf("1", "2", "3")
            a.valueLabels["1"]?.byLanguage?.get("cs") shouldBe "Aktivní"
            a.valueLabels["2"]?.byLanguage?.get("en") shouldBe "Inactive"
        }

        "missing displayLabel and valueLabels default to absent / empty" {
            val r =
                TtrLoader.parseString(
                    """
                    def attribute name {
                        type: text
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val a = r.definitions[0] as AttributeDef
            a.displayLabel shouldBe null
            a.valueLabels.isEmpty() shouldBe true
        }

        "missing roles list defaults to empty on entity" {
            val r =
                TtrLoader.parseString(
                    """
                    def entity X {}
                    """.trimIndent(),
                )
            r.ok shouldBe true
            (r.definitions[0] as EntityDef).roles.isEmpty() shouldBe true
        }

        // ----- Search feature -----

        "parses a query with a complete search block" {
            val r =
                TtrLoader.parseString(
                    """
                    def query customersList {
                        sourceText: "select er.entity.zakaznik"
                        search {
                            keywords {
                                cs: ["zákazníci", "zákazník", "klienti"]
                                en: ["customers", "clients"]
                            }
                            patterns: [
                                "seznam (vsech |všech |)zákazníků",
                                "list (of |)customers"
                            ]
                            descriptions {
                                cs: ["Vrátí seznam všech aktivních zákazníků."]
                                en: ["Returns the list of all active customers."]
                            }
                            examples: ["Seznam všech zákazníků", "Show all customers"]
                            aliases: ["all-customers", "customer-list"]
                        }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val q = r.definitions[0] as QueryDef
            q.search.keywords.byLanguage["cs"] shouldBe listOf("zákazníci", "zákazník", "klienti")
            q.search.keywords.byLanguage["en"] shouldBe listOf("customers", "clients")
            q.search.patterns shouldHaveSize 2
            q.search.patterns[1] shouldBe "list (of |)customers"
            q.search.descriptions.byLanguage["cs"]
                ?.shouldHaveSize(1)
            q.search.examples shouldBe listOf("Seznam všech zákazníků", "Show all customers")
            q.search.aliases shouldBe listOf("all-customers", "customer-list")
        }

        "parses an entity with a partial search block" {
            val r =
                TtrLoader.parseString(
                    """
                    def entity Zakaznik {
                        search {
                            keywords { cs: ["zákazník"] }
                        }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val e = r.definitions[0] as EntityDef
            e.search.keywords.byLanguage["cs"] shouldBe listOf("zákazník")
            e.search.patterns.isEmpty() shouldBe true
            e.search.aliases.isEmpty() shouldBe true
        }

        "parses an attribute with an empty search block" {
            val r =
                TtrLoader.parseString(
                    """
                    def attribute STAV {
                        type: int
                        search { }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val a = r.definitions[0] as AttributeDef
            a.search shouldBe
                org.tatrman.ttr.parser.model
                    .SearchHintsValue()
        }

        "parses a role with a search block" {
            val r =
                TtrLoader.parseString(
                    """
                    def role fact {
                        label { cs: "Faktová", en: "Fact" }
                        search {
                            keywords { en: ["fact", "facts"] }
                            aliases: ["fact-table"]
                        }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val role = r.definitions[0] as RoleDef
            role.search.keywords.byLanguage["en"] shouldBe listOf("fact", "facts")
            role.search.aliases shouldBe listOf("fact-table")
        }

        "missing search block defaults to empty SearchHintsValue on each kind" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q { sourceText: "select 1" }
                    def entity E {}
                    def attribute A { type: int }
                    def role R { label { cs: "X" } }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            (r.definitions[0] as QueryDef).search shouldBe
                org.tatrman.ttr.parser.model
                    .SearchHintsValue()
            (r.definitions[1] as EntityDef).search shouldBe
                org.tatrman.ttr.parser.model
                    .SearchHintsValue()
            (r.definitions[2] as AttributeDef).search shouldBe
                org.tatrman.ttr.parser.model
                    .SearchHintsValue()
            (r.definitions[3] as RoleDef).search shouldBe
                org.tatrman.ttr.parser.model
                    .SearchHintsValue()
        }

        "removed searchHint keyword on a column produces a parse error" {
            val r =
                TtrLoader.parseString(
                    """
                    def column id {
                        type: int
                        searchHint: "customer id"
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe false
            r.errors.isNotEmpty() shouldBe true
        }

        "empty search block emits a warning" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q { sourceText: "select 1", search { } }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("empty") } shouldBe true
        }

        "unknown language code emits a warning" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q {
                        sourceText: "select 1"
                        search { keywords { xx: ["foo"] } }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("'xx'") } shouldBe true
        }

        "duplicate language entry in keywords emits a warning (last write wins)" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q {
                        sourceText: "select 1"
                        search {
                            keywords {
                                cs: ["first"]
                                cs: ["second"]
                            }
                        }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("duplicate language entry 'cs'") } shouldBe true
            (r.definitions[0] as QueryDef).search.keywords.byLanguage["cs"] shouldBe listOf("second")
        }

        "duplicate key in an object value emits a warning (not silently dropped)" {
            val r =
                TtrLoader.parseString(
                    """
                    def relation R {
                        from: { a: db.dbo.first, a: db.dbo.second }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("duplicate key 'a'") } shouldBe true
            // Last-write-wins is preserved (the Map divergence is documented + now surfaced).
            val from = (r.definitions[0] as RelationDef).from as PropertyValue.ObjectValue
            (from.entries["a"] as PropertyValue.IdValue).ref.path shouldBe "db.dbo.second"
        }

        "keyword token containing whitespace emits a warning" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q {
                        sourceText: "select 1"
                        search { keywords { cs: ["zákazníci ano"] } }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("contains whitespace") } shouldBe true
        }

        "duplicate pattern in patterns list emits a warning" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q {
                        sourceText: "select 1"
                        search {
                            patterns: [
                                "list customers",
                                "list customers"
                            ]
                        }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("duplicate pattern") } shouldBe true
        }

        "appendix-example query round-trips through walker" {
            val r =
                TtrLoader.parseString(
                    """
                    def query customersList {
                        sourceText: ${'"'}${'"'}${'"'}
                            select er.entity.zakaznik
                        ${'"'}${'"'}${'"'}
                        description: "Lists all active customers, sorted by name."
                        search {
                            keywords {
                                cs: ["zákazníci", "klienti"]
                                en: ["customers"]
                            }
                            patterns: [
                                "seznam (vsech |všech |)zákazníků",
                                "list (of |)customers"
                            ]
                            descriptions {
                                cs: ["Vrátí seznam všech aktivních zákazníků."]
                                en: ["Returns the list of all active customers."]
                            }
                            examples: ["Seznam všech zákazníků", "Show all customers"]
                            aliases: ["customer-list"]
                        }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val q = r.definitions[0] as QueryDef
            q.description shouldBe "Lists all active customers, sorted by name."
            q.search.keywords.byLanguage["cs"]
                ?.size shouldBe 2
            q.search.keywords.byLanguage["en"]
                ?.size shouldBe 1
            q.search.patterns.size shouldBe 2
            q.search.descriptions.byLanguage["cs"]
                ?.size shouldBe 1
            q.search.examples.size shouldBe 2
            q.search.aliases.size shouldBe 1
        }

        // ----- v2 grammar: searchable/fuzzy inside search block -----

        "top-level searchable on a column is now a parse error" {
            val r =
                TtrLoader.parseString(
                    """
                    def column id {
                        type: int
                        searchable: true
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe false
            r.errors.isNotEmpty() shouldBe true
        }

        "search block searchable and fuzzy parse onto SearchHintsValue" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q {
                        sourceText: "select 1"
                        search { searchable: true, fuzzy: true }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val q = r.definitions[0] as QueryDef
            q.search.searchable shouldBe true
            q.search.fuzzy shouldBe true
        }

        // v2.0.0 (contracts.md §2.5 / AST-NAMING.md): searchable lives ONLY inside
        // the search block; the old top-level ColumnDef.searchable / AttributeDef.searchable
        // convenience accessor is gone. SearchBlockSpec guards its absence by reflection.
        "column searchable lives inside its search block" {
            val r =
                TtrLoader.parseString(
                    """
                    def column name {
                        type: text
                        search { searchable: true }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val c = r.definitions[0] as ColumnDef
            c.search.searchable shouldBe true
        }

        "attribute searchable lives inside its search block" {
            val r =
                TtrLoader.parseString(
                    """
                    def attribute name {
                        type: text
                        search { searchable: true }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val a = r.definitions[0] as AttributeDef
            a.search.searchable shouldBe true
        }

        "fuzzy defaults to false when absent" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q {
                        sourceText: "select 1"
                        search { keywords { cs: ["x"] } }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val q = r.definitions[0] as QueryDef
            q.search.fuzzy shouldBe false
            q.search.searchable shouldBe false
        }

        "fuzzy without searchable emits a fuzzy-without-searchable warning" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q { sourceText: "select 1", search { fuzzy: true } }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("fuzzy-without-searchable") } shouldBe true
        }

        "fuzzy with searchable emits no fuzzy warning" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q { sourceText: "select 1", search { searchable: true, fuzzy: true } }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("fuzzy-without-searchable") } shouldBe false
        }

        "duplicate search sub-property is an error" {
            val r =
                TtrLoader.parseString(
                    """
                    def query Q { sourceText: "select 1", search { patterns: ["a"], patterns: ["b"] } }
                    """.trimIndent(),
                )
            r.ok shouldBe false
            r.errors.any { it.message.contains("duplicate-search-property") } shouldBe true
        }

        "captures a package declaration" {
            val r =
                TtrLoader.parseString(
                    """
                    package er.sales
                    model er

                    def entity X {}
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.packageName shouldBe "er.sales"
        }

        "captures named and wildcard imports" {
            val r =
                TtrLoader.parseString(
                    """
                    import cnc.role.fact
                    import db.dbo.*

                    def entity X {}
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.imports shouldHaveSize 2
            r.imports[0].target shouldBe "cnc.role.fact"
            r.imports[0].wildcard shouldBe false
            r.imports[1].target shouldBe "db.dbo"
            r.imports[1].wildcard shouldBe true
        }

        "default (no package) yields null packageName and empty imports" {
            val r = TtrLoader.parseString("def entity X {}")
            r.ok shouldBe true
            r.packageName shouldBe null
            r.imports.isEmpty() shouldBe true
        }

        "a graph block in a .ttr file is ignored with a warning" {
            val r =
                TtrLoader.parseString(
                    """
                    graph g { model er }
                    def entity X {}
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.definitions.any { it.name == "X" } shouldBe true
            r.warnings.any { it.message.contains("graph") } shouldBe true
        }
    })
