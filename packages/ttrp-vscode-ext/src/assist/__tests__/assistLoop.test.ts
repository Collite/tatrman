import { describe, it, expect } from 'vitest';
import { runAssistLoop, ModelProvider } from '../assistLoop';
import { AssistDiagnostic } from '../prompt';

const eq: AssistDiagnostic = { id: 'TTRP-EQ-001', message: '`==` is not equality (S9)', suggestion: 'use =' };
const validate = async (c: string): Promise<AssistDiagnostic[]> => (c.includes('==') ? [eq] : []);

describe('runAssistLoop', () => {
  it('repairs an invalid first candidate and returns ok (generate → validate → repair)', async () => {
    const responses = ["Keep the rows where status == 'open'.", "Keep the rows where status = 'open'."];
    let n = 0;
    const model: ModelProvider = { complete: async () => responses[n++] };
    const out = await runAssistLoop({ context: 'ctx', request: 'keep open rows', model, validate });
    expect(out.ok).toBe(true);
    expect(out.attempts).toBe(2);
    expect(out.candidate).not.toContain('==');
  });

  it('never presents an edit when it cannot repair — ok=false, diagnostics surfaced (C4-d-iii)', async () => {
    const model: ModelProvider = { complete: async () => 'x == y' };
    const out = await runAssistLoop({ context: 'c', request: 'r', model, validate, maxRepairs: 2 });
    expect(out.ok).toBe(false);
    expect(out.attempts).toBe(3); // initial + 2 repairs
    expect(out.diagnostics[0].id).toBe('TTRP-EQ-001');
  });

  it('feeds the prior diagnostics + suggestion into the repair prompt (the repair vocabulary)', async () => {
    const prompts: string[] = [];
    const model: ModelProvider = {
      complete: async (p) => {
        prompts.push(p);
        return prompts.length === 1 ? 'a == b' : 'a = b';
      },
    };
    await runAssistLoop({ context: 'c', request: 'r', model, validate });
    expect(prompts[1]).toContain('TTRP-EQ-001');
    expect(prompts[1]).toContain('use =');
  });
});
