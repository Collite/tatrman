package org.tatrman.ttrp.expr

/**
 * The static type vocabulary for TTR-P expressions (S23). It mirrors the TTR
 * db-schema attribute-type vocabulary VERBATIM — the `typeValue` rule in
 * `packages/grammar/src/TTR.g4` (the S23 anchor): `text int float bool datetime
 * string boolean number integer double object list char varchar decimal date
 * timestamp` (+ `id` for a custom/domain type).
 *
 * Aliases collapse to one canonical form each, so the coercion lattice reasons over
 * a single spelling. Canonical forms (documented per B-T5 / Q9-4):
 *   - `int`, `integer`                       -> [Integer]   (canonical "integer")
 *   - `bool`, `boolean`                       -> [Bool]      (canonical "bool")
 *   - `char`, `varchar`, `string`, `text`     -> [Str]       (canonical "string")
 *   - `float`                                 -> [Float]     (canonical "float")
 *   - `double`                                -> [Double]    (canonical "double")
 *   - `number`                                -> [Number]    (canonical "number")
 *   - `decimal`                               -> [Decimal]   (canonical "decimal", optional precision/scale)
 *   - `date`                                  -> [Date]
 *   - `timestamp`                             -> [Timestamp]
 *   - `datetime`                              -> [Datetime]
 *   - `object`                                -> [Obj]       (canonical "object")
 *   - `list`                                  -> [Lst]       (canonical "list")
 *   - anything else (grammar `id`)            -> [Named]     (custom/domain type, verbatim spelling)
 *
 * All types are nullable by default — there is NO not-null type in v1 expressions;
 * canonical SQL 3VL is the semantics, not a type flag (B-T5, forced by A4).
 */
sealed class TtrpType(
    val canonical: String,
) {
    object Integer : TtrpType("integer")

    object Float : TtrpType("float")

    object Double : TtrpType("double")

    object Number : TtrpType("number")

    data class Decimal(
        val precision: Int? = null,
        val scale: Int? = null,
    ) : TtrpType("decimal")

    object Bool : TtrpType("bool")

    object Str : TtrpType("string")

    object Date : TtrpType("date")

    object Timestamp : TtrpType("timestamp")

    object Datetime : TtrpType("datetime")

    object Obj : TtrpType("object")

    object Lst : TtrpType("list")

    data class Named(
        val name: String,
    ) : TtrpType(name)

    /** The coercion KIND — the equivalence class widening is confined to (B-T5). */
    val kind: Kind
        get() =
            when (this) {
                is Integer, is Float, is Double, is Number, is Decimal -> Kind.NUMERIC
                is Bool -> Kind.BOOL
                is Str -> Kind.STRING
                is Date, is Timestamp, is Datetime -> Kind.TEMPORAL
                is Obj -> Kind.OBJECT
                is Lst -> Kind.LIST
                is Named -> Kind.OTHER
            }

    /** Canonical spelling ignoring precision/scale — used for type-equality in tests and coercion. */
    override fun toString(): String = canonical

    enum class Kind { NUMERIC, BOOL, STRING, TEMPORAL, OBJECT, LIST, OTHER }

    companion object {
        /**
         * Normalizes a surface type spelling (S23) to its canonical [TtrpType],
         * collapsing aliases. Unknown spellings become a [Named] custom type (the
         * grammar's `id` case). [precision]/[scale] apply only to `decimal`.
         */
        fun parse(
            spelling: String,
            precision: Int? = null,
            scale: Int? = null,
        ): TtrpType =
            when (spelling.lowercase()) {
                "int", "integer" -> Integer
                "float" -> Float
                "double" -> Double
                "number" -> Number
                "decimal" -> Decimal(precision, scale)
                "bool", "boolean" -> Bool
                "char", "varchar", "string", "text" -> Str
                "date" -> Date
                "timestamp" -> Timestamp
                "datetime" -> Datetime
                "object" -> Obj
                "list" -> Lst
                else -> Named(spelling)
            }
    }
}
