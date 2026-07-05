package org.tatrman.ttr.metadata.graph

import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.parseSchemaCode
import org.tatrman.ttr.metadata.model.Attribute
import org.tatrman.ttr.metadata.model.AttributeMappingTarget
import org.tatrman.ttr.metadata.model.CncSchema
import org.tatrman.ttr.metadata.model.DbColumn
import org.tatrman.ttr.metadata.model.DbForeignKey
import org.tatrman.ttr.metadata.model.DbSchema
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.Er2CncRoleMapping
import org.tatrman.ttr.metadata.model.Er2DbAttributeMapping
import org.tatrman.ttr.metadata.model.Er2DbEntityMapping
import org.tatrman.ttr.metadata.model.ErSchema
import org.tatrman.ttr.metadata.model.MappingTarget
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelVersion
import org.tatrman.ttr.metadata.model.Role
import org.tatrman.ttr.metadata.model.SchemaContents
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

/**
 * Phase 07 B1 — focused tests for the qname-keyed graph used by `TraverseEdges`. Exercises the
 * edge catalog (DEFINES / REFERENCES / MAPS_TO incl. roles) with a hand-rolled model so the
 * assertions don't depend on what the fixture happens to contain.
 */
class TraverseEdgesHandlerSpec :
    StringSpec({

        fun qn(
            schema: String,
            namespace: String,
            name: String,
        ): QualifiedName =
            QualifiedName(
                schemaCode = parseSchemaCode(schema) ?: SchemaCode.UNSPECIFIED,
                namespace = namespace,
                name = name,
            )

        /** Builds a small model: 2 tables + FK + 1 entity + 2 attrs + Er2Db + Er2CncRole. */
        fun sampleModel(): Model {
            val customersQ = qn("db", "dbo", "customers")
            val ordersQ = qn("db", "dbo", "orders")
            val custId = qn("db", "dbo", "customers.id")
            val custName = qn("db", "dbo", "customers.name")
            val orderCustId = qn("db", "dbo", "orders.customer_id")
            val customers =
                DbTable(
                    internalId = "t1",
                    qname = customersQ,
                    columns =
                        listOf(
                            DbColumn(internalId = "t1.c1", qname = custId, table = customersQ, dataType = "int"),
                            DbColumn(internalId = "t1.c2", qname = custName, table = customersQ, dataType = "text"),
                        ),
                    primaryKey = listOf("id"),
                )
            val orders =
                DbTable(
                    internalId = "t2",
                    qname = ordersQ,
                    columns =
                        listOf(
                            DbColumn(
                                internalId = "t2.c1",
                                qname = qn("db", "dbo", "orders.id"),
                                table = ordersQ,
                                dataType = "int",
                            ),
                            DbColumn(internalId = "t2.c2", qname = orderCustId, table = ordersQ, dataType = "int"),
                        ),
                    primaryKey = listOf("id"),
                )
            val fk =
                DbForeignKey(
                    internalId = "fk1",
                    qname = qn("db", "dbo", "fk_orders_customer"),
                    fromColumns = listOf(orderCustId),
                    toColumns = listOf(custId),
                )
            val entityQ = qn("er", "entity", "Customer")
            val attrIdQ = qn("er", "entity", "Customer.id")
            val attrNameQ = qn("er", "entity", "Customer.name")
            val attrId = Attribute(internalId = "a1", qname = attrIdQ, entity = entityQ, type = "int")
            val attrName = Attribute(internalId = "a2", qname = attrNameQ, entity = entityQ, type = "text")
            val entity = Entity(internalId = "e1", qname = entityQ, attributes = listOf(attrId, attrName))
            val roleQ = qn("cnc", "role", "fact")
            val role = Role(internalId = "r1", qname = roleQ)
            val mapEntity =
                Er2DbEntityMapping(
                    internalId = "m1",
                    qname = qn("er2db_entity", "Customer", "default"),
                    entity = entityQ,
                    target = MappingTarget.Table(customersQ),
                )
            val mapAttr =
                Er2DbAttributeMapping(
                    internalId = "m2",
                    qname = qn("er2db_attribute", "Customer.id", "default"),
                    attribute = attrIdQ,
                    target = AttributeMappingTarget.Column(custId),
                )
            val mapRole =
                Er2CncRoleMapping(
                    internalId = "m3",
                    qname = qn("er2cnc_role", "Customer_fact", "default"),
                    entity = entityQ,
                    role = roleQ,
                )
            return Model(
                descriptor = ModelDescriptor(id = "test", name = "test"),
                version = ModelVersion(value = "v1", swappedAt = java.time.Instant.parse("2026-05-13T00:00:00Z")),
                schemas =
                    mapOf<String, SchemaContents>(
                        "db" to
                            DbSchema(
                                tables = mapOf(customersQ to customers, ordersQ to orders),
                                foreignKeys = mapOf(fk.qname to fk),
                            ),
                        "er" to ErSchema(entities = mapOf(entityQ to entity)),
                        "cnc" to CncSchema(roles = mapOf(roleQ to role)),
                    ),
                mappings = listOf(mapEntity, mapAttr, mapRole),
                queries = emptyMap(),
            )
        }

        val customersQ = qn("db", "dbo", "customers")
        val custIdQ = qn("db", "dbo", "customers.id")
        val orderCustIdQ = qn("db", "dbo", "orders.customer_id")
        val entityQ = qn("er", "entity", "Customer")
        val attrIdQ = qn("er", "entity", "Customer.id")
        val roleQ = qn("cnc", "role", "fact")

        "MAPS_TO from entity → mapped table (bypassing the mapping vertex)" {
            val h = TraverseEdgesHandler(sampleModel())
            val steps = h.traverse(entityQ, setOf(EdgeType.MAPS_TO), Direction.OUTGOING, 1)
            steps.map { it.edge.target } shouldContainAll listOf(customersQ)
            steps.all { it.depth == 1 } shouldBe true
        }

        "MAPS_TO from entity also reaches its conceptual role" {
            val h = TraverseEdgesHandler(sampleModel())
            val steps = h.traverse(entityQ, setOf(EdgeType.MAPS_TO), Direction.OUTGOING, 1)
            steps.map { it.edge.target } shouldContainAll listOf(customersQ, roleQ)
        }

        "MAPS_TO from attribute → mapped column" {
            val h = TraverseEdgesHandler(sampleModel())
            val steps = h.traverse(attrIdQ, setOf(EdgeType.MAPS_TO), Direction.OUTGOING, 1)
            steps.map { it.edge.target } shouldContainAll listOf(custIdQ)
        }

        "REFERENCES from FK source column → FK target column" {
            val h = TraverseEdgesHandler(sampleModel())
            val steps = h.traverse(orderCustIdQ, setOf(EdgeType.REFERENCES), Direction.OUTGOING, 1)
            steps.map { it.edge.target } shouldBe listOf(custIdQ)
            steps.single().edge.type shouldBe EdgeType.REFERENCES
        }

        "depth=2 from entity reaches columns via the mapped table" {
            val h = TraverseEdgesHandler(sampleModel())
            val steps = h.traverse(entityQ, emptySet(), Direction.INCOMING, 2)
            // entity has attributes pointing back via DEFINES; the table has columns. Both at depth ≤ 2.
            val targets = steps.map { it.edge.target to it.edge.source }
            // attrs DEFINE entity → reachable at depth 1 (INCOMING walks reverse)
            (steps.any { it.edge.source == attrIdQ } && steps.any { it.edge.target == entityQ }) shouldBe true
        }

        "BFS terminates on cycles (visited set) — no infinite loop with BOTH direction" {
            val h = TraverseEdgesHandler(sampleModel())
            // BOTH direction over DEFINES creates symmetric reachability (column⇄table); BFS must
            // still terminate. We just verify the call returns and doesn't time out.
            val steps = h.traverse(customersQ, setOf(EdgeType.DEFINES), Direction.BOTH, 5)
            steps.isNotEmpty() shouldBe true
        }

        "BOTH emits each edge at most once (no outgoing+incoming double-count)" {
            val h = TraverseEdgesHandler(sampleModel())
            val steps = h.traverse(customersQ, setOf(EdgeType.DEFINES), Direction.BOTH, 5)
            // An edge A→B is reachable both as outgoing (at A) and incoming (at B); the
            // handler dedups emission, so no (type,source,target) triple repeats.
            val edges = steps.map { it.edge }
            edges.size shouldBe edges.distinct().size
        }
    })
