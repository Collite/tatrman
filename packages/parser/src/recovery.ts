import { DefaultErrorStrategy, Parser, RecognitionException, Token } from 'antlr4ng';

export interface RecoveryEvent {
  line: number;
  column: number;
  offsetStart: number;
  offsetEnd: number;
  description: string;
}

export class RecoveryReportingStrategy extends DefaultErrorStrategy {
  public readonly recoveryEvents: RecoveryEvent[] = [];

  override recover(recognizer: Parser, e: RecognitionException): void {
    const tok = recognizer.getCurrentToken();
    this.recoveryEvents.push(recoveryEvent(tok, 'parser resumed after syntax error'));
    super.recover(recognizer, e);
  }

  override recoverInline(recognizer: Parser): Token {
    const tok = recognizer.getCurrentToken();
    this.recoveryEvents.push(recoveryEvent(tok, 'parser skipped token to continue'));
    return super.recoverInline(recognizer);
  }
}

function recoveryEvent(tok: Token, action: string): RecoveryEvent {
  return {
    line: tok.line,
    column: tok.column,
    offsetStart: tok.start,
    offsetEnd: tok.stop + 1,
    description: `${action} at '${tok.text ?? ''}'`,
  };
}