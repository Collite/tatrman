import { describe, it, expect } from 'vitest';
import { loadProjectViaUpload } from '../file-system';

describe('loadProjectViaUpload', () => {
  function makeFile(name: string, webkitRelativePath: string, content: string): File {
    const file = new File([content], name, { type: 'text/plain' });
    (file as File & { webkitRelativePath: string }).webkitRelativePath = webkitRelativePath;
    return file;
  }

  it('filters out non-.ttr/.ttrl/.toml files', async () => {
    const input = {
      files: [
        makeFile('artikl.ttr', 'project/artikl.ttr', 'def entity artikl {}'),
        makeFile('modeler.toml', 'project/modeler.toml', '[project]'),
        makeFile('diagram.png', 'project/diagram.png', 'fake png content'),
      ],
    } as unknown as HTMLInputElement & { files: FileList };

    const result = await loadProjectViaUpload(input);
    const keys = Array.from(result.files.keys());
    expect(keys).toHaveLength(2);
    expect(keys).not.toContain('diagram.png');
    expect(keys).toContain('artikl.ttr');
    expect(keys).toContain('modeler.toml');
  });

  it('returns ProjectFiles with both .ttr and .toml entries', async () => {
    const input = {
      files: [
        makeFile('er.ttr', 'project/er.ttr', 'def entity foo {}'),
        makeFile('modeler.toml', 'project/modeler.toml', '[project]'),
      ],
    } as unknown as HTMLInputElement & { files: FileList };

    const result = await loadProjectViaUpload(input);
    expect(result.files.has('er.ttr')).toBe(true);
    expect(result.files.has('modeler.toml')).toBe(true);
    expect(result.files.get('er.ttr')).toBe('def entity foo {}');
  });

  it('keys do not start with /', async () => {
    const input = {
      files: [
        makeFile('artikl.ttr', 'project/artikl.ttr', 'def entity artikl {}'),
      ],
    } as unknown as HTMLInputElement & { files: FileList };

    const result = await loadProjectViaUpload(input);
    const keys = Array.from(result.files.keys());
    expect(keys.every(k => !k.startsWith('/'))).toBe(true);
  });
});

describe('loadProjectViaFileSystemAccessAPI', () => {
  it('returns null when showDirectoryPicker is undefined', async () => {
    const { loadProjectViaFileSystemAccessAPI } = await import('../file-system');
    const result = await loadProjectViaFileSystemAccessAPI();
    expect(result).toBeNull();
  });
});