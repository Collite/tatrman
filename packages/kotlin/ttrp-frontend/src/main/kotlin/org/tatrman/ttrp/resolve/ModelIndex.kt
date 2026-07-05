package org.tatrman.ttrp.resolve

import org.tatrman.ttr.metadata.model.Attribute
import org.tatrman.ttr.metadata.model.DbColumn
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.ModelObject
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.Relation
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.parseSchemaCode
import org.tatrman.ttr.metadata.query.ErBindingResult
import org.tatrman.ttr.metadata.query.MetadataQuery
import org.tatrman.ttr.metadata.registry.RegistrySnapshot

/** A resolved `import <pkg>(.<tier>)?.*` scope entry (D-b). */
data class ImportScope(
    val pkg: String,
    val tier: SchemaCode?,
    val raw: String,
)

/**
 * A read-only façade over a ttr-metadata [RegistrySnapshot] for TTR-P resolution
 * (D-a sub-1: the qname path/package derives the tier — no kind sigils). Everything
 * goes THROUGH ttr-metadata; ttrp-frontend never parses `.ttrm` directly.
 */
class ModelIndex(
    val snapshot: RegistrySnapshot,
) {
    private val query = MetadataQuery(snapshot)
    private val objects: List<ModelObject> =
        snapshot.model
            .objectByQname()
            .values
            .toList()

    /** True iff some model object lives in package [pkg] (dir-path convention: `.` → `/`). */
    fun packageExists(pkg: String): Boolean {
        val path = "/" + pkg.replace('.', '/') + "/"
        return objects.any { it.sourceFile.contains(path) }
    }

    private fun inScope(
        obj: ModelObject,
        imports: List<ImportScope>,
    ): Boolean =
        imports.any { imp ->
            obj.sourceFile.contains("/" + imp.pkg.replace('.', '/') + "/") &&
                (imp.tier == null || obj.qname.schemaCode == imp.tier)
        }

    /** Loadable model objects (db table / er entity) with the given simple name in import scope (D-b-iii). */
    fun findLoadable(
        name: String,
        imports: List<ImportScope>,
    ): List<ModelObject> =
        objects.filter {
            (it is DbTable || it is Entity) && it.qname.name == name && inScope(it, imports)
        }

    /**
     * Loadable objects matching [name] case-INSENSITIVELY in scope — used to DETECT a
     * same-name clash (C2-d/D-b no-first-wins), e.g. er entity `sales_txn` vs db table
     * `SALES_TXN` both reachable under an all-tier import → the caller must qualify.
     */
    fun findLoadableCi(
        name: String,
        imports: List<ImportScope>,
    ): List<ModelObject> =
        objects.filter {
            (it is DbTable || it is Entity) && it.qname.name.equals(name, ignoreCase = true) && inScope(it, imports)
        }

    /** A full-qname load like `erp.accounts` → the object named [name] in package [pkg] (imports not required). */
    fun findByPackage(
        pkg: String,
        name: String,
    ): List<ModelObject> {
        val path = "/" + pkg.replace('.', '/') + "/"
        return objects.filter {
            (it is DbTable || it is Entity) && it.qname.name == name && it.sourceFile.contains(path)
        }
    }

    /** Entities in import scope by simple name (used to resolve a load's underlying entity). */
    fun findEntity(
        name: String,
        imports: List<ImportScope>,
    ): Entity? = objects.filterIsInstance<Entity>().firstOrNull { it.qname.name == name && inScope(it, imports) }

    /** Relations in import scope by simple name. */
    fun findRelations(
        name: String,
        imports: List<ImportScope>,
    ): List<Relation> = objects.filterIsInstance<Relation>().filter { it.qname.name == name && inScope(it, imports) }

    fun tableColumns(table: DbTable): List<Pair<String, String>> =
        table.columns.map { it.qname.name.substringAfterLast('.') to it.dataType }

    fun entityAttributes(entity: Entity): List<Pair<String, String>> =
        entity.attributes.map { it.qname.name.substringAfterLast('.') to it.type }

    fun erToDb(qname: QualifiedName): ErBindingResult = query.erToDb(qname)

    /** All db columns of a table (for join-condition / column typing). */
    fun columnsOf(table: DbTable): List<DbColumn> = table.columns

    fun attributesOf(entity: Entity): List<Attribute> = entity.attributes

    companion object {
        /** Parse `import erp.er.*` → ImportScope(pkg="erp", tier=ER). Last segment as a tier when it is a schema code. */
        fun importScope(
            qnameParts: List<String>,
            raw: String,
        ): ImportScope {
            if (qnameParts.size >= 2) {
                val last = qnameParts.last()
                val tier = parseSchemaCode(last)
                if (tier != null) {
                    return ImportScope(qnameParts.dropLast(1).joinToString("."), tier, raw)
                }
            }
            return ImportScope(qnameParts.joinToString("."), null, raw)
        }
    }
}
