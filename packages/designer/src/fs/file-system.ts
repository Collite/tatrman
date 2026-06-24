export interface ProjectFiles {
  rootName: string;
  files: Map<string, string>;
}

export async function loadProjectViaUpload(input: HTMLInputElement): Promise<ProjectFiles> {
  const files = new Map<string, string>();
  const entries = input.files;
  if (!entries) return { rootName: 'project', files };

  let rootName = 'project';
  for (const file of Array.from(entries)) {
    if (!file.name.endsWith('.ttrm') && !file.name.endsWith('.ttrl') && !file.name.endsWith('.toml')) {
      continue;
    }
    const content = await file.text();
    const name = file.webkitRelativePath?.split('/')[0] ?? file.name;
    if (!rootName || rootName === 'project') {
      rootName = name;
    }
    const relativePath = file.webkitRelativePath
      ? file.webkitRelativePath.split('/').slice(1).join('/')
      : file.name;
    files.set(relativePath, content);
  }

  return { rootName, files };
}

export async function loadProjectViaFileSystemAccessAPI(): Promise<ProjectFiles | null> {
  if (!('showDirectoryPicker' in window)) return null;
  try {
    const dirHandle = await (window as unknown as { showDirectoryPicker: () => Promise<FileSystemDirectoryHandle> }).showDirectoryPicker();
    const files = new Map<string, string>();
    for await (const [name, handle] of dirHandle.entries()) {
      if (handle.kind === 'file') {
        if (!name.endsWith('.ttrm') && !name.endsWith('.ttrl') && !name.endsWith('.toml')) continue;
        const fileHandle = handle as FileSystemFileHandle;
        const file = await fileHandle.getFile();
        const content = await file.text();
        files.set(name, content);
      }
    }
    return { rootName: dirHandle.name, files };
  } catch {
    return null;
  }
}

export async function downloadFile(filename: string, content: string): Promise<void> {
  const blob = new Blob([content], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}