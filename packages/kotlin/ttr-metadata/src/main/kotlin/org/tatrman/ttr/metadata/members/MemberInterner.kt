// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

/**
 * A member-string interner: collapses equal-but-distinct [String] instances to a single canonical
 * object. Shared across a snapshot's domains ([MaterializedMemberSnapshot]) so a member value that
 * appears in several domains (a code reused across dimensions, a name shared by two attributes) is
 * held once — the large-dimension memory mitigation (architecture §7).
 *
 * Not thread-safe; a snapshot is materialized on one pass. It is NOT [String.intern] (the JVM string
 * pool is a permanent, GC-hostile home) — this pool is dropped with the snapshot.
 */
class MemberInterner {
    private val pool = HashMap<String, String>()

    fun intern(s: String): String = pool.getOrPut(s) { s }
}
