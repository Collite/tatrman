#!/usr/bin/env node
import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PKG_PATH = path.resolve(__dirname, '../../package.json');

describe('package.json language/grammar registration', () => {
  const pkg = JSON.parse(fs.readFileSync(PKG_PATH, 'utf-8'));
  const languages = pkg.contributes.languages;
  const grammars = pkg.contributes.grammars;

  it('exactly one language registers .ttrg', () => {
    const ttrgLanguages = languages.filter((l: any) =>
      l.extensions?.includes('.ttrg'),
    );
    expect(ttrgLanguages.length).toBe(1);
    expect(ttrgLanguages[0].id).toBe('ttrg');
  });

  it('.ttrg is NOT on the ttr language', () => {
    const ttrLang = languages.find((l: any) => l.id === 'ttr');
    expect(ttrLang?.extensions ?? []).not.toContain('.ttrg');
  });

  it('grammars has a ttrg entry mapping to source.ttrg', () => {
    const ttrgGrammar = grammars.find((g: any) => g.language === 'ttrg');
    expect(ttrgGrammar).toBeDefined();
    expect(ttrgGrammar.scopeName).toBe('source.ttrg');
    expect(ttrgGrammar.path).toBe('./syntaxes/ttrg.tmLanguage.json');
  });

  it('no .ttrl language is registered', () => {
    const ttrlLanguages = languages.filter((l: any) =>
      l.extensions?.includes('.ttrl'),
    );
    expect(ttrlLanguages.length).toBe(0);
  });

  it('no ttrl grammar is registered', () => {
    const ttrlGrammars = grammars.filter((g: any) => g.language === 'ttrl');
    expect(ttrlGrammars.length).toBe(0);
  });

  it('activationEvents includes both ttr and ttrg', () => {
    const events = pkg.activationEvents as string[];
    expect(events).toContain('onLanguage:ttr');
    expect(events).toContain('onLanguage:ttrg');
  });

  it('ttr language registers .ttrm (not .ttrg)', () => {
    const ttrLang = languages.find((l: any) => l.id === 'ttr');
    expect(ttrLang?.extensions).toEqual(['.ttrm']);
  });
});