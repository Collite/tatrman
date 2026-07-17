// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema

import org.tatrman.ttr.importschema.dbmodel.GeneratedFile
import org.tatrman.ttr.importschema.dbmodel.IdentifierRename

/** The complete output of one import: the `db` mirror + `er` first cut + the review checklist. */
data class ImportResult(
    val dbFiles: List<GeneratedFile>,
    val erFile: GeneratedFile,
    val reviewMarkdown: String,
    val reviewJson: String,
    val renames: List<IdentifierRename>,
)
