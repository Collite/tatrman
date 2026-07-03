import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  /** Optional label shown in the fallback ("error loading <label>"). */
  label?: string;
  /** Optional reset-key — when it changes, the boundary clears its error. */
  resetKey?: unknown;
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('ErrorBoundary caught:', error, info);
  }

  componentDidUpdate(prevProps: Props): void {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  render(): ReactNode {
    if (this.state.error) {
      const label = this.props.label ?? 'view';
      return (
        <div className="flex items-center justify-center w-full h-full bg-red-50 p-6">
          <div className="max-w-lg text-red-800">
            <div className="font-semibold mb-2">Error loading {label}</div>
            <pre className="text-xs whitespace-pre-wrap bg-red-100 p-2 rounded border border-red-200">
              {this.state.error.message}
            </pre>
            <button
              type="button"
              onClick={() => this.setState({ error: null })}
              className="mt-3 px-3 py-1 text-sm bg-red-200 hover:bg-red-300 rounded"
            >
              Dismiss
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
