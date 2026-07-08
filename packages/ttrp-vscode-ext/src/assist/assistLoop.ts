import { buildPrompt, AssistDiagnostic } from './prompt';

/** The host's model (the LLM call lives here, never in the LSP/compiler — P2, C4-d-ii = γ). */
export interface ModelProvider {
  complete(prompt: string): Promise<string>;
}

/** Validate a candidate through `ttrp/validate` — returns the named diagnostics (repair vocabulary). */
export type CandidateValidator = (candidate: string) => Promise<AssistDiagnostic[]>;

export interface AssistOutcome {
  candidate: string;
  diagnostics: AssistDiagnostic[];
  ok: boolean;
  attempts: number;
}

/**
 * The generate → validate → repair loop (C4-d). Re-prompts with the prior diagnostics + their
 * suggestions up to [maxRepairs] times. This function NEVER applies an edit — it only returns the
 * candidate + whether it passed the exit gate (C4-d-iii): the caller presents a diff and applies
 * ONLY on explicit user confirmation, and presents nothing when `ok` is false.
 */
export async function runAssistLoop(args: {
  context: string;
  request: string;
  model: ModelProvider;
  validate: CandidateValidator;
  maxRepairs?: number;
}): Promise<AssistOutcome> {
  const maxRepairs = args.maxRepairs ?? 3;
  let prior: AssistDiagnostic[] = [];
  let candidate = '';
  let attempts = 0;
  for (let i = 0; i <= maxRepairs; i++) {
    attempts++;
    candidate = (await args.model.complete(buildPrompt(args.context, args.request, prior))).trim();
    const diagnostics = await args.validate(candidate);
    if (diagnostics.length === 0) return { candidate, diagnostics: [], ok: true, attempts };
    prior = diagnostics;
  }
  return { candidate, diagnostics: prior, ok: false, attempts };
}
