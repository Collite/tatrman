package org.tatrman.ttrp.lsp

import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.services.TextDocumentService
import org.tatrman.ttrp.lsp.analysis.AnalysisEngine
import org.tatrman.ttrp.lsp.analysis.AnalysisScheduler
import org.tatrman.ttrp.lsp.docs.DocumentStore
import java.util.concurrent.CompletableFuture

/**
 * The `textDocument/…` surface (T4.1.3–4.1.7). Sync + diagnostics run through the
 * [scheduler]; hover / definition / rename analyze the current document on demand
 * (deterministic re-check; the [engine] caches by version so a hover reuses the
 * diagnostics pass's report).
 */
class TtrpTextDocumentService(
    private val server: TtrpLanguageServer,
    private val docs: DocumentStore,
    private val engine: AnalysisEngine,
    private val scheduler: AnalysisScheduler,
    private val hoverService: HoverService,
    private val definitionService: DefinitionService,
    private val renameService: RenameService,
) : TextDocumentService {
    override fun didOpen(params: DidOpenTextDocumentParams) {
        val d = params.textDocument
        docs.open(d.uri, d.text, d.version, d.languageId)
        scheduler.schedule(d.uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val id = params.textDocument
        val updated = docs.change(id.uri, id.version, params.contentChanges)
        if (updated != null) scheduler.schedule(id.uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        docs.close(params.textDocument.uri)
        scheduler.clear(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // No-op: text is canonical and already synced via didChange.
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> =
        CompletableFuture.supplyAsync {
            val doc = docs.get(params.textDocument.uri) ?: return@supplyAsync null
            val analysis = engine.analyze(doc)
            hoverService.hover(analysis.report, params.position)
        }

    override fun definition(
        params: DefinitionParams,
    ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> =
        CompletableFuture.supplyAsync {
            val doc = docs.get(params.textDocument.uri) ?: return@supplyAsync emptyLocations()
            val analysis = engine.analyze(doc)
            val loc = definitionService.define(params.textDocument.uri, analysis.report.document, params.position)
            if (loc == null) emptyLocations() else Either.forLeft(mutableListOf(loc))
        }

    override fun prepareRename(
        params: PrepareRenameParams,
    ): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> =
        CompletableFuture.supplyAsync {
            val doc = docs.get(params.textDocument.uri) ?: return@supplyAsync null
            val analysis = engine.analyze(doc)
            when (val prep = renameService.prepareRename(doc.text, analysis.report.document, params.position)) {
                is RenameService.Prepare.Valid -> Either3.forFirst(prep.range)
                is RenameService.Prepare.Invalid ->
                    throw ResponseErrorException(ResponseError(ResponseErrorCode.InvalidRequest, prep.reason, null))
            }
        }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> =
        CompletableFuture.supplyAsync {
            val doc = docs.get(params.textDocument.uri) ?: return@supplyAsync WorkspaceEdit(emptyList())
            val analysis = engine.analyze(doc)
            renameService.rename(
                params.textDocument.uri,
                doc.text,
                analysis.report.document,
                doc.version,
                params.position,
                params.newName,
            ) ?: WorkspaceEdit(emptyList())
        }

    private fun emptyLocations(): Either<MutableList<out Location>, MutableList<out LocationLink>> =
        Either.forLeft(mutableListOf())
}
