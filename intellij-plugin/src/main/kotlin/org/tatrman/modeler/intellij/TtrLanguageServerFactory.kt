// SPDX-License-Identifier: Apache-2.0
package org.tatrman.modeler.intellij

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * LSP4IJ entry point (contracts §3). Returns a fresh [TtrStreamConnectionProvider]
 * per project. No `createLanguageClient` / `getServerInterface` overrides in v1:
 * the standard client covers every surfaced feature and no custom `modeler/…`
 * requests are issued.
 */
class TtrLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        TtrStreamConnectionProvider(project)
}
