import { useEffect, useState } from 'react';

export interface ToastMessage {
  id: string;
  message: string;
  kind: 'error' | 'info';
}

interface ToastProps {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}

export function ToastContainer({ toasts, onDismiss }: ToastProps) {
  return (
    <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm">
      {toasts.map((toast) => (
        <Toast key={toast.id} toast={toast} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

function Toast({ toast, onDismiss }: { toast: ToastMessage; onDismiss: (id: string) => void }) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(false);
      setTimeout(() => onDismiss(toast.id), 300);
    }, 4000);
    return () => clearTimeout(timer);
  }, [toast.id, onDismiss]);

  return (
    <div
      className={`flex items-start gap-3 px-4 py-3 rounded-lg shadow-lg border transition-opacity duration-300 ${
        visible ? 'opacity-100' : 'opacity-0'
      } ${
        toast.kind === 'error'
          ? 'bg-red-50 border-red-300 text-red-700'
          : 'bg-sky-50 border-sky-300 text-sky-700'
      }`}
    >
      <span className="flex-1 text-sm">{toast.message}</span>
      <button
        onClick={() => {
          setVisible(false);
          setTimeout(() => onDismiss(toast.id), 300);
        }}
        className="text-sm opacity-70 hover:opacity-100"
        aria-label="Dismiss"
      >
        ✕
      </button>
    </div>
  );
}

let toastCounter = 0;
export function makeToast(message: string, kind: 'error' | 'info' = 'error'): ToastMessage {
  return { id: `toast-${++toastCounter}`, message, kind };
}