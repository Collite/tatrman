package org.tatrman.ttrp.lsp

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import org.tatrman.ttrp.lsp.analysis.AnalysisEngine
import org.tatrman.ttrp.lsp.analysis.AnalysisScheduler
import org.tatrman.ttrp.lsp.docs.DocumentStore
import org.tatrman.ttrp.lsp.project.FilesystemProjectResolver
import org.tatrman.ttrp.lsp.project.ProjectResolver
import org.tatrman.ttrp.lsp.protocol.AuthoringContextParams
import org.tatrman.ttrp.lsp.protocol.AuthoringContextResult
import org.tatrman.ttrp.lsp.protocol.ExplainParams
import org.tatrman.ttrp.lsp.protocol.ExplainResult
import org.tatrman.ttrp.lsp.protocol.RunParams
import org.tatrman.ttrp.lsp.protocol.RunResult
import org.tatrman.ttrp.lsp.protocol.TranspileParams
import org.tatrman.ttrp.lsp.protocol.TranspileResult
import org.tatrman.ttrp.lsp.protocol.TtrpLanguageServerApi
import org.tatrman.ttrp.lsp.protocol.ValidateParams
import org.tatrman.ttrp.lsp.protocol.ValidateResult
import org.tatrman.ttrp.lsp.viewstate.ViewStateRenameParticipant
import java.util.concurrent.CompletableFuture

/**
 * The single TTR-P language server (G-b/G-f: one Kotlin LSP across hosts). Stage 4.1
 * serves diagnostics, hover, definition, and rename over the injected [projects]
 * resolver; the transport is external (stdio `main` here; a WS launcher in Stage 5.1
 * reuses the exact same server object). The five `ttrp/…` custom methods are declared
 * now (contracts §4) and answer `MethodNotFound` until Stage 4.2.
 */
class TtrpLanguageServer(
    projects: ProjectResolver = FilesystemProjectResolver(),
    renameParticipants: List<ViewStateRenameParticipant> = emptyList(),
) : TtrpLanguageServerApi,
    LanguageClientAware {
    private var client: LanguageClient? = null

    val docs = DocumentStore()
    val engine = AnalysisEngine(projects)
    private val scheduler = AnalysisScheduler(docs, engine, { client })

    private val hoverService = HoverService()
    private val definitionService = DefinitionService()
    private val renameService = RenameService(renameParticipants)

    private val textDocumentService =
        TtrpTextDocumentService(this, docs, engine, scheduler, hoverService, definitionService, renameService)
    private val workspaceService = TtrpWorkspaceService()

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    fun client(): LanguageClient? = client

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities()
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental)
        capabilities.setHoverProvider(true)
        capabilities.setDefinitionProvider(true)
        capabilities.renameProvider =
            org.eclipse.lsp4j.jsonrpc.messages.Either
                .forRight(RenameOptions(true))
        // documentFormattingProvider is advertised in Stage 4.2.
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun shutdown(): CompletableFuture<Any> {
        scheduler.close()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        // The launcher owns process teardown; nothing to do here beyond scheduler close (done in shutdown).
    }

    override fun getTextDocumentService() = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    // ---- custom ttrp/ methods — declared in Stage 4.1, implemented in Stage 4.2 ----

    override fun transpile(params: TranspileParams): CompletableFuture<TranspileResult> = notYet()

    override fun run(params: RunParams): CompletableFuture<RunResult> = notYet()

    override fun explain(params: ExplainParams): CompletableFuture<ExplainResult> = notYet()

    override fun validate(params: ValidateParams): CompletableFuture<ValidateResult> = notYet()

    override fun authoringContext(params: AuthoringContextParams): CompletableFuture<AuthoringContextResult> = notYet()

    private fun <T> notYet(): CompletableFuture<T> =
        CompletableFuture.failedFuture(
            ResponseErrorException(ResponseError(ResponseErrorCode.MethodNotFound, "lands in Stage 4.2", null)),
        )
}
