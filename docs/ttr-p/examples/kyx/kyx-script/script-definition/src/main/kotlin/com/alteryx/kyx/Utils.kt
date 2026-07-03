package com.alteryx.kyx

fun unwrapSummarize(properties: Properties, issues: MutableList<String>) {
    val summaryColumns =
        ((properties["summary"] as PropertyAnnotationBase?)?.properties?.get("operands") as List<PropertyAnnotationBase>?)
            ?: listOf<PropertyAnnotationBase>()
    val summaryActions = summaryColumns.mapNotNull {
        if (it.properties["kind"] == "Function"
            && it.properties["aggregation"] == AggregationTypes.AGGREGATE
        ) {
            val funcName =
                ((it.properties["function"] as Function?)?.name) ?: (it.properties["name"] as String?) ?: run {
                    issues.add("Unknown aggregate function. Replaced with Sum.")  //Todo ale poradit ktera
                    "Sum"
                }

            val cName = (((it.properties["parameters"] as PropertyAnnotationBase?)
                ?.properties?.get("operands") as List<PropertyAnnotationBase>?)
                ?.getOrNull(0)
                ?.properties?.get("column") as Column?)
                ?.name

            if (cName != null) {
                Properties(
                    mutableMapOf(
                        "name" to cName,
                        "selected" to true,
                        "action" to funcName,
                        "alias" to it.properties["alias"]
                    )
                )
            } else {
                issues.add("Not an aggreagation of a column.")//Todo ale poradit ktera
                null
            }
        } else {
            issues.add("Not an aggregate function.")   //Todo ale poradit ktera ....
            null
        }
    }
    val groupByColumns =
        ((properties["groupBy"] as PropertyAnnotationBase?)?.properties?.get("operands") as List<PropertyAnnotationBase>?)
            ?: listOf<PropertyAnnotationBase>()
    val groupByActions = groupByColumns.mapNotNull {

        val cName = (it?.properties?.get("column") as Column?)?.name

        if (cName != null) {
            Properties(
                mutableMapOf(
                    "name" to cName,
                    "selected" to true,
                    "action" to "GroupBy",
                    "alias" to it.properties["alias"]
                )
            )
        } else {
            issues.add("Not a column for group by. Expressions not yet supported, sorry.")//Todo ale poradit ktera
            null
        }
    }


    properties["record"] = mutableListOf<Properties>()
    (properties["record"] as MutableList<Properties>).addAll(summaryActions)
    (properties["record"] as MutableList<Properties>).addAll(groupByActions)

}

fun columnThroughConfig(column: Column, record: List<AlteryxXmlNodeRecordInfoField>, name: String = "Input"): Column? {

    val passThrough = record.any { it.name?.lowercase() == "*unknown" }

    val recordInfo = record.find { it.name.equals(column.name, true) }
        ?: if (passThrough)
            return column.copy()
        else
            return null

    if (recordInfo.selected == false)
        return null

    return column.copy(
        name = recordInfo.rename ?: column.name,
        type = if (DataType.from(recordInfo.fieldType ?: "Unknown") != DataType.UNKNOWN)
            DataType.from(recordInfo.fieldType!!)
        else column.type  //todo sizes
    )


}


fun createConfigFile(dataSource: DataSource): Pair<AlteryxXmlNodeDataSource, AlteryxXmlNodeFormatSpecific> {
    var fileSpecific: AlteryxXmlNodeFormatSpecific? = null
    val file = AlteryxXmlNodeDataSource().apply {
        if (dataSource.Type?.lowercase() == "file") {
            this.dataSource = dataSource.File ?: ""
            fileFormat = dataSource.fileType?.value ?: 0
            fileSpecific = AlteryxXmlNodeFormatSpecific.getFileDefaults(dataSource.fileType)
                ?: AlteryxXmlNodeFormatSpecific.csvDefaults()
        } else if (dataSource.Type?.lowercase() == "db") {
            val stringBuilder = StringBuilder("odbc:DSN=${dataSource.DSN};")
            if (!dataSource.Username.isNullOrBlank()) stringBuilder.append("UID=${dataSource.Username}")
            if (!dataSource.Password.isNullOrBlank()) stringBuilder.append("PWD=${dataSource.Password}")
            if (!dataSource.Query.isNullOrBlank())
                stringBuilder.append("|||${dataSource.Query}")
            else {
                stringBuilder.append("|||")
                if (!dataSource.Db.isNullOrBlank()) stringBuilder.append(dataSource.Db + ".")
                if (!dataSource.Schema.isNullOrBlank()) stringBuilder.append(dataSource.Schema + ".")
                stringBuilder.append(dataSource.Table)
            }
            fileSpecific = AlteryxXmlNodeFormatSpecific.sqlDefaults()
        }
    }
    return Pair(file, fileSpecific!!)
}

fun parseSource(s: String): DataSource {
    //todo much better, but you get the idea
    if (s.contains("::")) {
        val split = s.split("::")
        if (split.size != 2)
            return DataSource().apply {
                Type = "Db"
                Query = s
            }
        val dsn = split[0].split(";")
        val ds = DataSource().apply {
            Type = "Db"
            DSN = dsn[0]
            if (dsn.size >1)
                Username = dsn[1]
            if (dsn.size >2)
                Password = dsn[2]
        }
        val tbl = split[1].split(".")
        when (tbl.size) {
            1 -> {
                ds.Table = tbl[0]
            }
            2 -> {
                ds.Schema = tbl[0]
                ds.Table = tbl[1]
            }
            3 -> {
                ds.Db = tbl[0]
                ds.Schema = tbl[1]
                ds.Table = tbl[2]
            }
        }
        return ds
    } else {
        return DataSource().apply {
            val ft = getFileType(s)
            Type = ft?.toString() ?: "csv"
            File = s
        }
    }
}
fun printPropertyAnnotationBase(prop: PropertyAnnotationBase?): String {

    if (prop == null)
        return ""

    val name = prop.name
    when (name.lowercase()) {
        "column" -> {
            return "Column: name:${prop.properties["columnName"]} , type:${prop.properties["type"]} ," +
                    if (prop.properties.containsKey("alias")) prop.properties["alias"] as String else ""
        }

        "expression" -> {
            return "Expression: " + printExpression(prop) +
                    if (prop.properties.containsKey("alias")) prop.properties["alias"] as String else ""
        }

        "number" -> {
            return "Number: value: ${prop.properties["normValue"]}"
        }

        "operationkeyword" -> {
            return "Keyword: ${prop.properties["operations"]}"
        }

        "condition" -> {
            if (prop.properties.containsKey("expression")) {
                return "Condition: " + printPropertyAnnotationBase(prop.properties["expression"] as PropertyAnnotationBase)
            } else {
                return "${prop.name}, ${prop.properties.size} " +
                        "properties: [ ${prop.properties.map { "${it.key}:${it.value}" }.joinToString(", ")} ]"
            }
        }

        "token" -> {
            var toPrint = "Token: ${prop.properties["text"]}"
            if (prop.properties["isNer"] as Boolean) {
                if (prop.properties["isDateTime"] as Boolean) {
                    toPrint += ": DateTime (${prop.properties["NerValue"]})"
                } else if (prop.properties["isNumber"] as Boolean) {
                    toPrint += ": Number (${prop.properties["NerValue"]})"
                } else toPrint += ": NER ${prop.properties["NerType"]} (${prop.properties["NerValue"]})"
            }
            if (prop.properties["isKeyword"] as Boolean) {
                toPrint += ": Keyword (${prop.properties["Keyword"]})"
            }
            if (prop.properties["isHelperWord"] as Boolean) {
                toPrint += ": HelperWord (${prop.properties["HelperWord"]})"
            }
            if (prop.properties["isOperator"] as Boolean) {
                toPrint += ": Operator (${prop.properties["Operator"]})"
            }
            return toPrint
        }
//        "operation" -> {
//            sb.append("${prop.name}  ($span), '$text', ${prop.properties.size} properties: [${prop.toString()}")
//
//        }
//        "select" -> {
//            sb.append("${prop.name}  ($span), '$text', ${prop.properties.size} properties: [${prop.toString()}")
//
//        }
//        "filter" -> {
//            sb.append("${prop.name}  ($span), '$text', ${prop.properties.size} properties: [${prop.toString()}")
//
//        }
//        "summarize" -> {
//            sb.append("${prop.name}  ($span), '$text', ${prop.properties.size} properties: [${prop.toString()}")
//
//        }
//        "timex" -> {
//            sb.append("${prop.name}  ($span), '$text', ${prop.properties.size} properties: [${prop.toString()}")
//
//        }
        else -> {
            return "${prop.name}, ${prop.properties.size} " +
                    "properties: [ ${prop.properties.map { "${it.key}:${it.value}" }.joinToString(", ")} ]"
        }
    }

}
fun printExpression(prop: PropertyAnnotationBase): String {
    val kind = prop.properties["kind"] as String
    when (kind.lowercase()) {
        "function" -> {
            if (((prop.properties["operator"] as Boolean? == true) || ((prop.properties["function"] as? Function)?.operator == true))
                && prop.properties.containsKey("parameters")
                && ((prop.properties["parameters"] as PropertyAnnotationBase).properties["operands"] as List<*>).isNotEmpty()
                && ((prop.properties["parameters"] as PropertyAnnotationBase).properties["operands"] as List<*>).size <= 2
            ) {
                val operands =
                    (prop.properties["parameters"] as PropertyAnnotationBase).properties["operands"] as List<PropertyAnnotationBase>
                if (operands.size == 2) {
                    return printExpression(operands[0]) +
                            printOperator(prop.properties["name"] as String) +
                            printExpression(operands[1])
                } else {
                    return printOperator(prop.properties["name"] as String) +
                            printExpression(operands[0])
                }
            } else {
                val sb = StringBuilder(prop.properties["name"] as String)
                sb.append("(")
                if (prop.properties.containsKey("parameters"))
                    sb.append(printExpression(prop.properties["parameters"] as PropertyAnnotationBase))
                sb.append(")")
                return sb.toString()
            }
        }

        "identifier" -> {
            return "[${prop.properties["name"]}]"
        }

        "constant" -> {
            if (prop.properties["type"] == DataType.TEXT) {
                return "\"${prop.properties["value"]}\""
            } else {
                return "${prop.properties["value"]}"
            }
        }

        "expressionlist" -> {

            return ((prop.properties["operands"] as List<PropertyAnnotationBase>?)?.joinToString(", ") {
                printExpression(
                    it
                )
            } ?: "")
        }

        "identifierlist" -> {
            return ((prop.properties["operands"] as List<PropertyAnnotationBase>?)?.joinToString(", ") {
                printExpression(
                    it
                )
            } ?: "")

        }

        "complexidentifier" -> {
            return "[*** COmplex ***].[${prop.properties["name"]}]"
        }

        else -> {
            return "<UNKNOWN $kind>"
        }
    }
}


fun printOperator(opName: String): String {
    return when (opName.uppercase()) {
        "PLUS" -> "+"
        "MINUS" -> "-"
        "TIMES" -> "*"
        "DIV" -> "/"
        "EQ", "DOUBLE_EQ", "VERBOSE_EQ" -> "=="
        "OR", "BIG_OR", "W_OR" -> "|"
        "AND", "BIG_AND", "W_AND" -> "&"
        "NOT", "TILDE", "W_NOT" -> "!"
        "LT", "VERBOSE_LT" -> "<"
        "LE", "VERBOSE_LE" -> "<="
        "GT", "VERBOSE_GT" -> ">"
        "GE", "VERBOSE_GE" -> ">="
        "NEQ", "NOT_EQ", "VERBOSE_NEQ" -> "!="
        else -> opName
    }
}
