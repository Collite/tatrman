// The two-level (recursive) canvas navigation stack (C1-a β). `["program"]` is the
// orchestration view; double-clicking a container pushes its path; the breadcrumb pops.
// The current canvas key drives skin/mode lookup in the layout — keys match `.ttrl`
// canvas keys exactly.

export interface ViewStack {
  readonly stack: string[];
}

export const ROOT: ViewStack = { stack: ['program'] };

export function current(v: ViewStack): string {
  return v.stack[v.stack.length - 1];
}

export function enter(v: ViewStack, canvasKey: string): ViewStack {
  if (current(v) === canvasKey) return v;
  return { stack: [...v.stack, canvasKey] };
}

/** Pop to the given depth (breadcrumb click); index 0 = root. */
export function popTo(v: ViewStack, index: number): ViewStack {
  if (index < 0 || index >= v.stack.length - 1) return v;
  return { stack: v.stack.slice(0, index + 1) };
}

export function exit(v: ViewStack): ViewStack {
  return v.stack.length > 1 ? { stack: v.stack.slice(0, -1) } : v;
}
