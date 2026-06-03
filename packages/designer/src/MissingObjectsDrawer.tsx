import { useState, useEffect, useRef } from 'react';

interface MissingObjectsDrawerProps {
  missingObjects: string[];
  onRemove: (qname: string) => void;
  onClose: () => void;
}

export function MissingObjectsDrawer({ missingObjects, onRemove, onClose }: MissingObjectsDrawerProps) {
  const [visible, setVisible] = useState(false);
  const overlayRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setVisible(true);
  }, []);

  useEffect(() => {
    if (missingObjects.length === 0) {
      setVisible(false);
      const t = setTimeout(onClose, 300);
      return () => clearTimeout(t);
    }
  }, [missingObjects.length, onClose]);

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === overlayRef.current) {
      setVisible(false);
      setTimeout(onClose, 300);
    }
  };

  return (
    <div
      ref={overlayRef}
      onClick={handleOverlayClick}
      className={`fixed inset-0 bg-black/20 z-40 transition-opacity duration-300 ${visible ? 'opacity-100' : 'opacity-0'}`}
    >
      <div
        className={`absolute right-0 top-0 h-full w-80 bg-white border-l border-slate-300 shadow-xl flex flex-col transition-transform duration-300 ${visible ? 'translate-x-0' : 'translate-x-full'}`}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
          <h2 className="text-base font-semibold text-gray-800">
            Stale Objects ({missingObjects.length})
          </h2>
          <button
            onClick={() => {
              setVisible(false);
              setTimeout(onClose, 300);
            }}
            className="px-3 py-1 text-sm text-gray-500 border border-slate-300 rounded hover:bg-slate-50"
          >
            Close
          </button>
        </div>
        <p className="text-xs text-gray-500 px-4 py-2 border-b border-slate-100">
          These objects are listed in the graph but no longer exist in the project.
        </p>
        <ul className="flex-1 overflow-y-auto">
          {missingObjects.map((qname) => (
            <li key={qname} className="flex items-center justify-between px-4 py-2.5 border-b border-slate-50">
              <span className="text-sm text-gray-700 truncate flex-1 min-w-0" title={qname}>
                {qname}
              </span>
              <button
                onClick={() => onRemove(qname)}
                className="ml-2 px-2 py-1 text-xs text-red-600 border border-red-300 rounded hover:bg-red-50 shrink-0"
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}