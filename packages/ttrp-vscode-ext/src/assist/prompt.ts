// Deterministic, prompt-ready rendering of the authoring-context bundle + request (contracts §7:
// "deterministic serialization"). No model dependency — the LLM call lives at the HOST (C4-d-ii = γ);
// this only shapes the text. Stable key order so a re-run produces byte-identical prompts.

export interface AssistDiagnostic {
  id: string;
  message: string;
  suggestion?: string;
}

/** Build the model prompt: the rendered bundle, the NL request, and (on repair) the prior diagnostics. */
export function buildPrompt(context: string, request: string, priorDiagnostics: AssistDiagnostic[]): string {
  const parts: string[] = ['# TTR-P authoring context', context.trim(), '', '# Request', request.trim()];
  if (priorDiagnostics.length > 0) {
    parts.push('', '# The previous attempt failed validation — fix exactly these:');
    for (const d of priorDiagnostics) {
      parts.push(`- ${d.id}: ${d.message}${d.suggestion ? ` (suggestion: ${d.suggestion})` : ''}`);
    }
  }
  parts.push('', '# Emit only the TTR-P text for the target dialect — no prose, no code fences.');
  return parts.join('\n');
}
