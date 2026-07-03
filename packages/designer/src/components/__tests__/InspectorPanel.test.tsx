import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { InspectorPanel } from '../InspectorPanel';
import type { PerKindData } from '@modeler/lsp';

const fixtureDetail = {
  qname: 'er.entity.artikl',
  kind: 'entity',
  label: 'artikl',
  description: 'An entity representing a thing.',
  tags: ['important', 'demo'],
  sourceUri: 'file:///path/to/artikl.ttrm',
  sourceLine: 42,
  perKindData: {
    kind: 'entity',
    attributes: [
      { name: 'id', qname: 'er.entity.artikl.id', kind: 'attribute', type: 'int', isKey: true, optional: false, isNameAttribute: false, isCodeAttribute: false },
      { name: 'name', qname: 'er.entity.artikl.name', kind: 'attribute', type: 'text', isKey: false, optional: false, isNameAttribute: true, isCodeAttribute: false },
    ],
    nameAttributeQname: 'er.entity.artikl.name',
    codeAttributeQname: null,
    roleQnames: [],
  } as PerKindData,
  referencedBy: [
    { qname: 'er.entity.related_thing', sourceUri: 'file:///path/to/related.ttrm', sourceLine: 10 },
    { qname: 'er.entity.other', sourceUri: 'file:///path/to/other.ttrm', sourceLine: 20 },
  ],
};

describe('InspectorPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('selectedSymbol === null → shows empty-state text', () => {
    render(
      <InspectorPanel
        selectedSymbol={null}
        symbolDetails={{}}
        onSelect={vi.fn()}
      />
    );
    expect(screen.getByText('Select a node to see its details.')).toBeInTheDocument();
  });

  it('with fixture detail → renders kind chip, label, qname, description, tags, source file:line, and attribute table', () => {
    render(
      <InspectorPanel
        selectedSymbol={{ qname: 'er.entity.artikl' }}
        symbolDetails={{ 'er.entity.artikl': fixtureDetail }}
        onSelect={vi.fn()}
      />
    );

    expect(screen.getByText('entity')).toBeInTheDocument();
    expect(screen.getByText('artikl')).toBeInTheDocument();
    expect(screen.getByText('er.entity.artikl')).toBeInTheDocument();
    expect(screen.getByText('An entity representing a thing.')).toBeInTheDocument();
    expect(screen.getByText('important')).toBeInTheDocument();
    expect(screen.getByText('demo')).toBeInTheDocument();
    expect(screen.getByText('artikl.ttrm')).toBeInTheDocument();
    expect(screen.getByText(':42')).toBeInTheDocument();

    expect(screen.getByText('id')).toBeInTheDocument();
    expect(screen.getByText('name')).toBeInTheDocument();
    expect(screen.getAllByText('int')).toHaveLength(1);
    expect(screen.getAllByText('text')).toHaveLength(1);
  });

  it('clicking a Referenced By row calls onSelect with that qname', () => {
    const onSelect = vi.fn();
    render(
      <InspectorPanel
        selectedSymbol={{ qname: 'er.entity.artikl' }}
        symbolDetails={{ 'er.entity.artikl': fixtureDetail }}
        onSelect={onSelect}
      />
    );

    fireEvent.click(screen.getByText('er.entity.related_thing'));
    expect(onSelect).toHaveBeenCalledWith('er.entity.related_thing');

    fireEvent.click(screen.getByText('er.entity.other'));
    expect(onSelect).toHaveBeenCalledWith('er.entity.other');
  });

  it('clicking the source button writes file:line to clipboard', async () => {
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });

    render(
      <InspectorPanel
        selectedSymbol={{ qname: 'er.entity.artikl' }}
        symbolDetails={{ 'er.entity.artikl': fixtureDetail }}
        onSelect={vi.fn()}
      />
    );

    const sourceButton = screen.getByRole('button', { name: /artikl\.ttrm/ });
    fireEvent.click(sourceButton);

    expect(writeText).toHaveBeenCalledWith('/path/to/artikl.ttrm:42');
  });
});