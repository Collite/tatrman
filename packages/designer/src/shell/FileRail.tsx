// The file rail (A-1 γ secondary spine). Browser Designer: the repo tree the backend already
// serves (the list of open .ttrm/.ttrg docs). IDE hosts: this is the host's native file tree —
// there the rail is host-provided, not this component (documented, not coded here).

export function FileRail({ files, onOpenFile }: { files: string[]; onOpenFile?: (uri: string) => void }) {
  return (
    <div data-testid="file-rail" style={{ width: 180, flex: '0 0 auto', background: '#f5f8fc', borderRight: '1px solid #CBD8E6', overflow: 'auto' }}>
      <h3 style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.06em', color: '#96989B', padding: '10px 12px 4px' }}>Files</h3>
      {files.length === 0 ? (
        <div style={{ padding: '4px 12px', fontSize: 12, color: '#96989B' }}>—</div>
      ) : (
        files.map((f) => (
          <button
            key={f}
            data-testid="file-rail-item"
            onClick={() => onOpenFile?.(f)}
            title={f}
            style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', background: 'transparent', cursor: onOpenFile ? 'pointer' : 'default', font: 'inherit', fontSize: 12, color: '#4A4B4D', padding: '2px 12px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
          >
            {f.split('/').pop()}
          </button>
        ))
      )}
    </div>
  );
}
