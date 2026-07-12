// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import org.tatrman.translator.framework.SurfaceType

/** Maps a db-schema type spelling (port/column `type` strings) to the translator's [SurfaceType]. */
object TypeMapping {
    fun surfaceType(spelling: String): SurfaceType =
        when (spelling.substringBefore('(').trim().lowercase()) {
            "int", "integer", "bigint", "smallint", "tinyint", "long" -> SurfaceType.INT
            "float", "double", "real", "decimal", "numeric", "number", "money" -> SurfaceType.FLOAT
            "bool", "boolean" -> SurfaceType.BOOL
            "date", "time", "timestamp", "datetime" -> SurfaceType.DATETIME
            else -> SurfaceType.TEXT
        }
}
