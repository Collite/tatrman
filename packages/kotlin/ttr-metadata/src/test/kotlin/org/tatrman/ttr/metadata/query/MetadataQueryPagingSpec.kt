package org.tatrman.ttr.metadata.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Path

/**
 * MD2 pull-down: paging window (MetadataServiceImpl lines 372–376). Default page
 * size 100, cap 1000, sort-key ordering `schemaCode.namespace.name`, stable
 * windows, afterKey resume, totalCount. The base64 wire token stays kantheon
 * (`PageTokenCodec`); this pins the library windowing over `afterKey`.
 */
class MetadataQueryPagingSpec :
    StringSpec({

        val q = queryFor(Path.of("src/test/resources/tatrman-repo/models"))
        val all = q.listObjects(MetadataQuery.ObjectFilter(), MetadataQuery.PageRequest(pageSize = 1000))

        "first page returns pageSize items + a resume key when more remain" {
            val p1 = q.listObjects(MetadataQuery.ObjectFilter(), MetadataQuery.PageRequest(pageSize = 2))
            p1.items.size shouldBe 2
            p1.totalCount shouldBe all.totalCount
            p1.nextAfterKey shouldNotBe null
        }

        "afterKey resumes without overlap and the last page has no resume key" {
            val size = 2
            // Key by full qname — leaf names collide across schemas (an ER attribute
            // and its er2db mapping can share a leaf name), so dedup must use qname.
            val seen = mutableListOf<org.tatrman.ttr.metadata.model.QualifiedName>()
            var after: String? = null
            do {
                val page =
                    q.listObjects(
                        MetadataQuery.ObjectFilter(),
                        MetadataQuery.PageRequest(afterKey = after, pageSize = size),
                    )
                seen += page.items.map { it.qname }
                after = page.nextAfterKey
            } while (after != null)
            // Every object seen exactly once; total matches.
            seen.size shouldBe all.totalCount
            seen.toSet().size shouldBe all.totalCount
        }

        "windows are stable across identical snapshots" {
            val a =
                q
                    .listObjects(
                        MetadataQuery.ObjectFilter(),
                        MetadataQuery.PageRequest(pageSize = 3),
                    ).items
                    .map { it.qname.name }
            val b =
                q
                    .listObjects(
                        MetadataQuery.ObjectFilter(),
                        MetadataQuery.PageRequest(pageSize = 3),
                    ).items
                    .map { it.qname.name }
            a shouldBe b
        }

        "page size is capped at 1000 and defaults to 100" {
            q
                .listObjects(
                    MetadataQuery.ObjectFilter(),
                    MetadataQuery.PageRequest(pageSize = 999_999),
                ).items.size shouldBeLessThanOrEqual
                1000
            MetadataQuery.DEFAULT_PAGE_SIZE shouldBe 100
            MetadataQuery.MAX_PAGE_SIZE shouldBe 1000
        }
    })
