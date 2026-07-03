package com.alteryx.workflow


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

fun getFileType(thePath: String): SupportedFileTypes? {
            val suffix = thePath.substringAfterLast('.', "")
            return SupportedFileTypes.fromString(suffix)
}
