package com.alteryx.kyx

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import java.util.HashMap

data class Properties(val p: MutableMap<String, Any?> = HashMap()) : MutableMap<String, Any?> by p {
}

@Serializable
enum class DataType {

    BOOLEAN,
    TEXT, CHAR, VARCHAR, NVARCHAR,
    PATH,
    INT, LONG, SHORT,
    FLOAT, DOUBLE,
    DECIMAL,
    DATE, TIME, DATETIME, TIMESTAMP //TODO INTERVAL ETC
    //TODO SPATIAL ETC
    //todo ints in different meanings (javaint, cint, sqlint apod)
    //todo which types require scale and precision
    ,
    UNKNOWN;

    companion object {

        fun from(dt: String): DataType {
            return when (dt.uppercase()) {
                "CHARACTER VARYING", "V_STRING" -> VARCHAR
                "INTEGER", "INT32" -> DataType.INT
                "INT16", "SMALLINT" -> DataType.SHORT
                "INT64", "BIGINT" -> DataType.LONG
                "WSTRING", "V_WSTRING" -> DataType.NVARCHAR
                "TIMESTAMP WITHOUT TIME ZONE" -> DataType.TIMESTAMP
                "NUMERIC" -> DataType.DECIMAL
                "DOUBLE PRECISION" -> DataType.DOUBLE
                "REAL" -> DataType.FLOAT
                "STRING" -> DataType.TEXT
                "BOOL", "BIT" -> DataType.BOOLEAN
                else -> {
                    try {
                        DataType.valueOf(dt.uppercase())
                    } catch (e: IllegalArgumentException) {
//                        println("Unknown type $dt")
                        DataType.UNKNOWN
                    }
                }
            }
        }

        fun IS_NUMERIC(it: DataType): Boolean {
            return listOf(DOUBLE, FLOAT, INT, LONG, SHORT, DECIMAL).contains(it)
        }

        fun IS_TEXTUAL(it: DataType): Boolean {
            return listOf(TEXT, CHAR, VARCHAR, NVARCHAR, PATH).contains(it)
        }

        fun toAlteryxString(it: DataType): String {
            return when (it) {
                BOOLEAN -> "Boolean"
                TEXT, CHAR, VARCHAR, NVARCHAR -> "V_WSTRING"
                PATH -> "W_VSTRING"
                INT -> "Int32"
                LONG -> "Int64"
                SHORT -> "Int16"
                FLOAT -> "Float"
                DOUBLE -> "Double"
                DECIMAL -> "Decimal"
                DATE, TIME, DATETIME, TIMESTAMP -> "Date"
                else -> "Unknown"

            }
        }
    }

}

@Serializable
data class Column(
    val name: String,
    var type: DataType = DataType.UNKNOWN,
    val scale: Int = 0,
    val precision: Int = 0,
) {
    override fun toString(): String {
        return "$name ($type)"
    }
}

@Serializable
data class Function(
    val name: String,
    val aliases: List<String> = listOf(),
    val returnType: DataType,
    val isAggregate: Boolean = false,
    val numParams: Int = 0,
    val params: List<Column> = listOf(),
    val operator: Boolean = false
)

@Serializable
data class Metadata(
    val columns: MutableList<Column> = mutableListOf()
) : MutableList<Column> by columns {
    val columnNames: List<String>
        get() = columns.map { it.name }

    fun readFromJsonString(json: String) {
        columns.clear()
        columns.addAll(Json.decodeFromString(json))
    }

    fun readFromDelimitedString(text: String) {
        columns.clear()
        val textColumns = text.split(" ", ",")
        columns.addAll(
            textColumns.map {
                val parts = it.split(":")
                val name = parts[0]
                val type = if (parts.size > 1) DataType.valueOf(parts[1]) else DataType.UNKNOWN
                val scale = if (parts.size > 2) parts[2].toInt() else -1
                val precision = if (parts.size > 3) parts[3].toInt() else -1
                Column(name, type, scale, precision)
            }
        )
    }

    constructor(string: String) : this() {
        readFromJsonString(string)
    }

    override fun toString(): String {
        return columns.joinToString(" | ")
    }

    fun copy(): Metadata {
        val m = Metadata()
        m.apply {
            addAll(this@Metadata.columns.map { it.copy() })
        }
        return m
    }
}



@Serializable
class DataSource {
    var Type: String? = null
    var File: String? = null
    var DSN: String? = null
    var Db: String? = null
    var Schema: String? = null
    var Table: String? = null
    var Query: String? = null
    var Username: String? = null
    var Password: String? = null

    val fileType: SupportedFileTypes?
        get() = File?.let { getFileType(it) }
}

@Serializable
data class CodeCompletionRequest (
    val model : String = "code-davinci-002",
    val prompt: String = "Hello, world",
    val max_tokens: Int = 256,
    val temperature: Int = 0,
    val top_p: Double = 1.0,
    val frequency_penalty: Double = 0.0,
    val presence_penalty: Double = 0.0,
    val stop : List<String> = listOf("#", ";")
)
@Serializable
data class CodeCompletionResponse (
    val id: String ="",
    @JsonNames("object")
    val Object: String = "",
    val created: Int = 0,
    val model: String = "",
    val choices: List<CodeCompletionChoice>,
    val usage: CodeCompletionUsage
)

@Serializable
data class CodeCompletionChoice (
    val text: String = "",
    val index : Int = 0,
    val logprobs: Int? = null,
    val finish_reason: String = ""
        )

@Serializable
data class CodeCompletionUsage (
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0 ,
    val total_tokens: Int = 0
        )


enum class SupportedFileTypes(val value: Int) {
    EXCEL(25),
    CSV(0);

    companion object {
        fun fromInt(value: Int) = SupportedFileTypes.values().firstOrNull { it.value == value }
        fun fromString(s: String) = when (s.lowercase()) {
            "xls", "xlsx", "excel" -> EXCEL
            "csv", "txt", "text" -> CSV
            else -> null
        }
    }
}

fun getFileType(properties: Properties): SupportedFileTypes? {

    return if ((properties["kind"] as String).equals("Constant")) {
        if ((properties["type"] as DataType) == DataType.TEXT) {
            val thePath = (properties["value"] as String).trim('"')
            val suffix = thePath.substringAfterLast('.', "")
            SupportedFileTypes.fromString(suffix)
        } else if ((properties["type"] as DataType) == DataType.PATH) {
            SupportedFileTypes.fromString((properties["fileType"] ?: "") as String)
        } else {
            null
        }
    } else {
        //todo var ref for database
        null
    }
}
fun getFileType(thePath: String): SupportedFileTypes? {
    val suffix = thePath.substringAfterLast('.', "")
    return SupportedFileTypes.fromString(suffix)
}
enum class AggregationTypes {
    AGGREGATE, SCALAR, UNKNOWN, CONSTANT
}

fun getAggrType(pab: PropertyAnnotationBase) : AggregationTypes {
    if (pab.properties.containsKey("aggregation"))
        return pab.properties["aggregation"] as AggregationTypes

    return when (((pab.properties["kind"] as String?) ?: "").lowercase()) {
        "constant" ->  AggregationTypes.CONSTANT
        "identifier" -> AggregationTypes.SCALAR
        "complexId" -> AggregationTypes.SCALAR
        else -> AggregationTypes.UNKNOWN
    }

}
