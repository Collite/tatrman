// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.model.SchemaCode
import java.nio.file.Path

/**
 * MD2 pull-down: ListObjects filter pipeline (MetadataServiceImpl lines 355–369).
 * Kantheon twin: `ListObjectsPackageFilterSpec` + the schema/kind/tags/prefix
 * filter cases embedded in `MetadataServiceImpl.listObjects`.
 */
class MetadataQueryListObjectsSpec :
    StringSpec({

        val q = queryFor(Path.of("src/test/resources/tatrman-repo/models"))

        fun names(f: MetadataQuery.ObjectFilter) =
            q
                .listObjects(f, MetadataQuery.PageRequest(pageSize = 1000))
                .items
                .map { it.qname.name }
                .toSet()

        "kind filter — tables only" {
            val tables = names(MetadataQuery.ObjectFilter(kind = "table"))
            tables shouldContainAll setOf("accounts", "SALES_TXN")
            tables.contains("customer") shouldBe false
        }

        "schema filter — DB only returns db-schema objects" {
            val page =
                q.listObjects(
                    MetadataQuery.ObjectFilter(schema = SchemaCode.DB),
                    MetadataQuery.PageRequest(pageSize = 1000),
                )
            page.items.all { it.qname.schemaCode == SchemaCode.DB } shouldBe true
            page.items.map { it.qname.name }.toSet() shouldContainAll setOf("accounts", "SALES_TXN")
        }

        "package filter — sourceFile contains /<pkg>/ (ListObjectsPackageFilterSpec twin)" {
            val erp = names(MetadataQuery.ObjectFilter(pkg = "erp"))
            erp shouldContainAll setOf("accounts", "SALES_TXN", "customer", "sales_txn")
            names(MetadataQuery.ObjectFilter(pkg = "nosuch")) shouldBe emptySet()
        }

        "no filter — totalCount counts all objects" {
            val page = q.listObjects(MetadataQuery.ObjectFilter(), MetadataQuery.PageRequest(pageSize = 1000))
            page.totalCount shouldBe page.items.size
            (page.totalCount > 4) shouldBe true
        }
    })
