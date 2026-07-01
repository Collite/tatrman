/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ... and Bora
 * ------------------------------------------------------------------------------------------ */
import {
	createConnection,
	TextDocuments,
	Diagnostic,
	DiagnosticSeverity,
	ProposedFeatures,
	InitializeParams,
	DidChangeConfigurationNotification,
	CompletionItem,
	CompletionItemKind,
	TextDocumentPositionParams,
	TextDocumentSyncKind,
	InitializeResult,
	CancellationToken,
	CompletionContext,
	integer
} from 'vscode-languageserver/node';

import {
	Position,
	TextDocument
} from 'vscode-languageserver-textdocument';

// import * as vscode from 'vscode'
import axios, { HttpStatusCode } from 'axios';
import { createWriteStream } from 'fs';
import { Uri } from 'vscode';
var logger = createWriteStream("C:/Temp/byx-server.log", { flags: 'a' });

// Create a connection for the server, using Node's IPC as a transport.
// Also include all preview / proposed LSP features.
const connection = createConnection(ProposedFeatures.all);

// Create a simple text document manager.
const documents: TextDocuments<TextDocument> = new TextDocuments(TextDocument);

let hasConfigurationCapability = false;
let hasWorkspaceFolderCapability = false;
let hasDiagnosticRelatedInformationCapability = false;

let serverAddress = "http://localhost:8099"
let serverPresent = true

let numberOfLines: Map<string, integer> = new Map()


// let suggestions: CompletionItem[] = []

connection.onInitialize(
	async (params: InitializeParams) => {
		logger.write("Initializing server\n");
		const capabilities = params.capabilities;

		// Does the client support the `workspace/configuration` request?
		// If not, we fall back using global settings.
		hasConfigurationCapability = !!(
			capabilities.workspace && !!capabilities.workspace.configuration
		);
		hasWorkspaceFolderCapability = !!(
			capabilities.workspace && !!capabilities.workspace.workspaceFolders
		);
		hasDiagnosticRelatedInformationCapability = !!(
			capabilities.textDocument &&
			capabilities.textDocument.publishDiagnostics &&
			capabilities.textDocument.publishDiagnostics.relatedInformation
		);

		const result: InitializeResult = {
			capabilities: {
				textDocumentSync: TextDocumentSyncKind.Incremental,
				// Tell the client that this server supports code completion.
				completionProvider: {
					resolveProvider: true,
					triggerCharacters: [" ", '\n']
				}
			}
		};
		if (hasWorkspaceFolderCapability) {
			result.capabilities.workspace = {
				workspaceFolders: {
					supported: true
				}
			};
		}

		// await checkMyServer()

		logger.write("Byx Language Server initialized\n");
		return result;
	});

connection.onInitialized(() => {
	logger.write("onInit called ");
	if (hasConfigurationCapability) {
		// Register for all configuration changes.
		connection.client.register(DidChangeConfigurationNotification.type, undefined);
	}
	if (hasWorkspaceFolderCapability) {
		connection.workspace.onDidChangeWorkspaceFolders(_event => {

		});
	}
});


async function checkMyServer() {
	logger.write("Checking if the server is alive ... ");
	// 👇️ const data: CreateUserResponse

	await axios.get<String>(
		serverAddress + '/doc/status',
		{
			headers: {
				Accept: 'text/plain',
			},
		}).then(response => {
			logger.write("Got a " + response + " response from the server: " + response.data + '\n');
			if (response.status == HttpStatusCode.Ok) {
				logger.write("Server's alive! Wo-hooo!")
				serverPresent = true
			}
		}
		)
		.catch(e => {
			logger.write("Server is dead or undead, in any case, not helpful. No validation and compilation, and almost no suggestions.")
		});

}

// The example settings
interface ExampleSettings {
	maxNumberOfProblems: number;
}

// The global settings, used when the `workspace/configuration` request is not supported by the client.
// Please note that this is not the case when using this server with the client provided in this example
// but could happen with other clients.
const defaultSettings: ExampleSettings = { maxNumberOfProblems: 1000 };
let globalSettings: ExampleSettings = defaultSettings;

// Cache the settings of all open documents
const documentSettings: Map<string, Thenable<ExampleSettings>> = new Map();

connection.onDidChangeConfiguration(change => {
	logger.write("on did change config called");
	if (hasConfigurationCapability) {
		// Reset all cached document settings
		documentSettings.clear();
	} else {
		globalSettings = <ExampleSettings>(
			(change.settings.byxLanguageServer || defaultSettings)
		);
	}

	// Revalidate all open text documents
	documents.all().forEach(validateTextDocument);
});

function getDocumentSettings(resource: string): Thenable<ExampleSettings> {
	logger.write("get doc settings called\n");
	if (!hasConfigurationCapability) {
		return Promise.resolve(globalSettings);
	}
	let result = documentSettings.get(resource);
	if (!result) {
		result = connection.workspace.getConfiguration({
			scopeUri: resource,
			section: 'byxLanguageServer'
		});
		documentSettings.set(resource, result);
	}
	return result;
}

// Only keep settings for open documents
documents.onDidClose(e => {
	logger.write("did close called\n");
	documentSettings.delete(e.document.uri);
});

// The content of a text document has changed. This event is emitted
// when the text document first opened or when its content has changed.
documents.onDidChangeContent(change => {
	logger.write("did change called\n");
	if (numberOfLinesChanged(change.document)) {
		validateTextDocument(change.document);
	}
});

documents.onDidOpen(open => {
	logger.write("did open happened")
	validateTextDocument(open.document)
})


function numberOfLinesChanged(textDocument: TextDocument): boolean {
	if (!numberOfLines.has(textDocument.uri)) {
		numberOfLines.set(textDocument.uri, textDocument.lineCount)
		return true
	} else {
		if (numberOfLines.get(textDocument.uri) != textDocument.lineCount) {
			numberOfLines.set(textDocument.uri, textDocument.lineCount)
			return true
		} else
			return false
	}
}

async function validateTextDocument(textDocument: TextDocument): Promise<void> {

	logger.write("validate doc called ...\n");
	if (!serverPresent) {
		return
	}

	const diagnostics = await provideServerBasedValidation(textDocument)

	// Send the computed diagnostics to VSCode.
	connection.sendDiagnostics({ uri: textDocument.uri, diagnostics });
}

connection.onDidChangeWatchedFiles(_change => {
	// Monitored files have change in VSCode

});

// This handler provides the initial list of the completion items.
connection.onCompletion(
	async (_textDocumentPosition: TextDocumentPositionParams): Promise<CompletionItem[]> => {
		logger.write("on Completion called");
		const document = documents.get(_textDocumentPosition.textDocument.uri);
		if (document == undefined) {
			return []
		}
		const position = _textDocumentPosition.position;
		return await provideCompletionItems(document, position);
	}
);

// This handler resolves additional information for the item selected in
// the completion list.
// connection.onCompletionResolve(
// 	(item: CompletionItem): CompletionItem => {
// 		if (item.data === 1) {
// 			item.detail = 'TypeScript details';
// 			item.documentation = 'TypeScript documentation';
// 		} else if (item.data === 2) {
// 			item.detail = 'JavaScript details';
// 			item.documentation = 'JavaScript documentation';
// 		}
// 		return item;
// 	}
// );

async function provideCompletionItems(document: TextDocument, position: Position): Promise<CompletionItem[]> {

	const docLines = lines(document.getText())
	const currentLine = docLines[position.line]

	const caretPosition = document.offsetAt(position)

	if (!currentLine.includes(" ")) {
		// const currentLineWords = currentLine.split(" ").filter( word => word.length > 0)
		logger.write("First word. Returning basic operation keywords...\n")
		return provideBasicOperationKeywords()

	} else {

		// if (serverPresent) {
			logger.write("Calling server for completion.")
			return await provideServerBasedSuggestion(document.getText(), caretPosition)
		// } else {
			// return []
		// }
	}
}

function lines(text: string) {
	return text.split('\n')
}

// Make the text document manager listen on the connection
// for open, change and close text document events
documents.listen(connection);

// Listen on the connection
connection.listen();


function provideBasicOperationKeywords(): CompletionItem[] {
	return [
		{
			label: 'select',
			kind: CompletionItemKind.Keyword,
			data: 1
		},
		{
			label: 'create',
			kind: CompletionItemKind.Keyword,
			data: 2
		},
		{
			label: 'filter',
			kind: CompletionItemKind.Keyword,
			data: 3
		},
		{
			label: 'summarize',
			kind: CompletionItemKind.Keyword,
			data: 4
		},
		{
			label: 'input',
			kind: CompletionItemKind.Keyword,
			data: 5
		},
		{
			label: 'output',
			kind: CompletionItemKind.Keyword,
			data: 6
		},
		{
			label: 'rename',
			kind: CompletionItemKind.Keyword,
			data: 7
		},
		{
			label: 'change',
			kind: CompletionItemKind.Keyword,
			data: 8
		}
	]
}

async function provideServerBasedSuggestion(text: string, caretPosition: integer): Promise<CompletionItem[]> {

	logger.write("Trying to call the server to suggest\n")

	const value = await callTheServerForSuggestions(text, caretPosition);
	logger.write("Got a suggestion response from the server: " + JSON.stringify(value, null, 4) + '\n');

	return processSuggestions(value);

}
async function provideServerBasedValidation(doc: TextDocument): Promise<Diagnostic[]> {

	logger.write("Trying to call the server to validate'\n")

	const value = await callTheServerForValidation(doc.getText());
	logger.write("Got a validation response from the server\n");

	return convertIssues(value, doc);

}

async function callTheServerForSuggestions(text: string, caretPosition: integer): Promise<SuggestionResponse> {
	logger.write("Calling the server for suggestion... ");
	// 👇️ const data: CreateUserResponse
	const response = await axios.post<SuggestionResponse>(
		serverAddress + '/doc/suggest',
		{ text: text, caretPosition: caretPosition },
		{
			headers: {
				'Content-Type': 'application/json',
				Accept: 'application/json',
			},
		})

	logger.write("Got a " + response.status + " suggestion response from the server: " + JSON.stringify(response.data, null, 4) + '\n');

	return response.data
}

async function callTheServerForValidation(text: string): Promise<ValidationIssues> {
	logger.write("Calling the server for validation... ");
	// 👇️ const data: CreateUserResponse
	const response = await axios.post<ValidationIssues>(
		serverAddress + '/doc/validate',
		text,
		{
			headers: {
				'Content-Type': 'text/plain',
				Accept: 'application/json',
			},
		});

	logger.write("Got a " + response.status + " validation response from the server: " + JSON.stringify(response.data, null, 4) + '\n');

	return response.data;
}


type SuggestionAsk = {
	text: string;
	caretPosition: integer;
}

type SuggestionResponse = {
	keywords: string[];
	columns: string[];
	functions: string[];
}

type ValidationIssue = {
	code: integer;
	message: string;
	text: string;
	charFrom: integer;
	charTo: integer;
	severity: integer;
}

type ValidationIssues = {
	issues: ValidationIssue[]
}

function convertIssues(issues: ValidationIssues, doc: TextDocument): Diagnostic[] {
	let diagnostics: Diagnostic[] = []

	issues.issues.forEach(function (it) {
		let severity = (it.severity == 0) ? DiagnosticSeverity.Information : (it.severity == 1) ? DiagnosticSeverity.Warning : DiagnosticSeverity.Error
		const diagnostic: Diagnostic = {
			severity: severity,
			range: {
				start: doc.positionAt(it.charFrom),
				end: doc.positionAt(it.charTo)
			},
			message: it.message,
			source: 'byx'
		};
		if (it.severity == 3 && hasDiagnosticRelatedInformationCapability) {
			diagnostic.relatedInformation = [
				{
					location: {
						uri: doc.uri,
						range: Object.assign({}, diagnostic.range)
					},
					message: 'These messages are brought to you by Byx.'
				},
				{
					location: {
						uri: doc.uri,
						range: Object.assign({}, diagnostic.range)
					},
					message: 'Enjoy!'
				}
			];
		}
		diagnostics.push(diagnostic);
	})



	return diagnostics
}

function processSuggestions(response: SuggestionResponse): CompletionItem[] {
	logger.write("Converting the response " + JSON.stringify(response) + '\n');

	let suggestions: CompletionItem[] = []


	response.columns.forEach(
		(value) => {
			suggestions.push(
				{
					label: value,
					kind: CompletionItemKind.Field
				}
			)
		}
	)
	response.keywords.forEach(
		(value) => {
			suggestions.push(
				{
					label: value,
					kind: CompletionItemKind.Keyword
				}
			)
		}
	)
	response.functions.forEach(
		(value) => {
			suggestions.push(
				{
					label: value,
					kind: CompletionItemKind.Function
				}
			)
		}
	)

	logger.write("Response converted into " + suggestions.length + " items.\n")

	return suggestions

}
