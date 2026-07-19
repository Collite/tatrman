import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import type { Command } from '../types.js';
import { CommandRegistry, commandParityGaps } from '../commands.js';
import { CommandPalette } from '../CommandPalette.js';

afterEach(() => cleanup());

function cmd(partial: Partial<Command> & { id: string; title: string }): Command {
  return { run: () => {}, ...partial };
}

describe('CommandRegistry', () => {
  it('register/all preserves registration order', () => {
    const r = new CommandRegistry();
    r.register(cmd({ id: 'a', title: 'Alpha' }));
    r.register(cmd({ id: 'b', title: 'Beta' }));
    r.register(cmd({ id: 'c', title: 'Gamma' }));
    expect(r.all().map((c) => c.id)).toEqual(['a', 'b', 'c']);
  });

  it('re-registering by id replaces in place (no duplicate)', () => {
    const r = new CommandRegistry();
    r.register(cmd({ id: 'a', title: 'Alpha' }));
    r.register(cmd({ id: 'b', title: 'Beta' }));
    r.register(cmd({ id: 'a', title: 'Alpha v2' }));
    expect(r.all().map((c) => c.id)).toEqual(['a', 'b']);
    expect(r.all().find((c) => c.id === 'a')?.title).toBe('Alpha v2');
  });

  it('unregister removes by id', () => {
    const r = new CommandRegistry();
    r.register(cmd({ id: 'a', title: 'Alpha' }));
    r.register(cmd({ id: 'b', title: 'Beta' }));
    r.unregister('a');
    expect(r.all().map((c) => c.id)).toEqual(['b']);
  });

  it('find does case-insensitive substring match on title', () => {
    const r = new CommandRegistry();
    r.register(cmd({ id: 'a', title: 'Switch Skin' }));
    r.register(cmd({ id: 'b', title: 'Open Subject' }));
    expect(r.find('skin').map((c) => c.id)).toEqual(['a']);
    expect(r.find('SUBJECT').map((c) => c.id)).toEqual(['b']);
  });

  it('find matches on group too', () => {
    const r = new CommandRegistry();
    r.register(cmd({ id: 'a', title: 'Alpha', group: 'View' }));
    r.register(cmd({ id: 'b', title: 'Beta', group: 'Edit' }));
    expect(r.find('view').map((c) => c.id)).toEqual(['a']);
  });

  it('find with empty query returns all()', () => {
    const r = new CommandRegistry();
    r.register(cmd({ id: 'a', title: 'Alpha' }));
    r.register(cmd({ id: 'b', title: 'Beta' }));
    expect(r.find('')).toEqual(r.all());
  });

  it('execute runs the command run() and is a no-op for unknown id', () => {
    const r = new CommandRegistry();
    const spy = vi.fn();
    r.register(cmd({ id: 'a', title: 'Alpha', run: spy }));
    r.execute('a');
    expect(spy).toHaveBeenCalledTimes(1);
    expect(() => r.execute('nope')).not.toThrow();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});

describe('commandParityGaps (E-4/D-6)', () => {
  const toolbarIds = ['skin.switch', 'perspective.switch', 'open.subject', 'pin.tab', 'drawer.open'];

  function fullRegistry(): CommandRegistry {
    const r = new CommandRegistry();
    for (const ta of toolbarIds) {
      r.register(cmd({ id: `cmd.${ta}`, title: ta, toolbarAction: ta }));
    }
    return r;
  }

  it('returns [] when every toolbar action has a mirroring command', () => {
    expect(commandParityGaps(fullRegistry(), toolbarIds)).toEqual([]);
  });

  it('returns the missing toolbarAction id when a command is dropped', () => {
    const r = fullRegistry();
    r.unregister('cmd.pin.tab');
    expect(commandParityGaps(r, toolbarIds)).toEqual(['pin.tab']);
  });
});

describe('CommandPalette', () => {
  function makeRegistry(): { registry: CommandRegistry; spies: Record<string, ReturnType<typeof vi.fn>> } {
    const registry = new CommandRegistry();
    const spies: Record<string, ReturnType<typeof vi.fn>> = {};
    for (const [id, title] of [
      ['skin', 'Switch Skin'],
      ['open', 'Open Subject'],
      ['pin', 'Pin Tab'],
    ] as const) {
      spies[id] = vi.fn();
      registry.register(cmd({ id, title, run: spies[id] as () => void }));
    }
    return { registry, spies };
  }

  it('renders nothing when closed', () => {
    const { registry } = makeRegistry();
    const { container } = render(<CommandPalette registry={registry} open={false} onClose={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('when open shows input + all items', () => {
    const { registry } = makeRegistry();
    render(<CommandPalette registry={registry} open onClose={() => {}} />);
    expect(screen.getByTestId('cmdk-input')).toBeInTheDocument();
    expect(screen.getAllByTestId('cmdk-item')).toHaveLength(3);
    expect(screen.getAllByTestId('cmdk-item')[0]).toHaveTextContent('Switch Skin');
  });

  it('typing filters the list', () => {
    const { registry } = makeRegistry();
    render(<CommandPalette registry={registry} open onClose={() => {}} />);
    fireEvent.change(screen.getByTestId('cmdk-input'), { target: { value: 'pin' } });
    const items = screen.getAllByTestId('cmdk-item');
    expect(items).toHaveLength(1);
    expect(items[0]).toHaveTextContent('Pin Tab');
  });

  it('Enter executes the top command and calls onClose', () => {
    const { registry, spies } = makeRegistry();
    const onClose = vi.fn();
    render(<CommandPalette registry={registry} open onClose={onClose} />);
    const input = screen.getByTestId('cmdk-input');
    fireEvent.change(input, { target: { value: 'pin' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(spies.pin).toHaveBeenCalledTimes(1);
    expect(spies.skin).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('clicking an item executes it and calls onClose', () => {
    const { registry, spies } = makeRegistry();
    const onClose = vi.fn();
    render(<CommandPalette registry={registry} open onClose={onClose} />);
    fireEvent.click(screen.getAllByTestId('cmdk-item')[1]);
    expect(spies.open).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('Escape calls onClose', () => {
    const { registry } = makeRegistry();
    const onClose = vi.fn();
    render(<CommandPalette registry={registry} open onClose={onClose} />);
    fireEvent.keyDown(screen.getByTestId('cmdk-input'), { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
