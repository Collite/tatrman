import { describe, it, expect } from 'vitest';
import { loadProjectViaUpload } from '../file-system';

describe('loadProjectViaUpload', () => {
  function makeFile(name: string, webkitRelativePath: string, content: string): File {
    const file = new File([content], name, { type: 'text/plain' });
    (file as File & { webkitRelativePath: string }).webkitRelativePath = webkitRelativePath;
    return file;
  }

  it('filters out non-.ttrm/.ttrl/.toml files', async () => {
    const input = {
      files: [
        makeFile('artikl.ttrm', 'project/artikl.ttrm', 'def entity artikl {}'),
        makeFile('modeler.toml', 'project/modeler.toml', '[project]'),
        makeFile('diagram.png', 'project/diagram.png', 'fake png content'),
      ],
    } as unknown as HTMLInputElement & { files: FileList };

    const result = await loadProjectViaUpload(input);
    const keys = Array.from(result.files.keys());
    expect(keys).toHaveLength(2);
    expect(keys).not.toContain('diagram.png');
    expect(keys).toContain('artikl.ttrm');
    expect(keys).toContain('modeler.toml');
  });

  it('returns ProjectFiles with both .ttrm and .toml entries', async () => {
    const input = {
      files: [
        makeFile('er.ttrm', 'project/er.ttrm', 'def entity foo {}'),
        makeFile('modeler.toml', 'project/modeler.toml', '[project]'),
      ],
    } as unknown as HTMLInputElement & { files: FileList };

    const result = await loadProjectViaUpload(input);
    expect(result.files.has('er.ttrm')).toBe(true);
    expect(result.files.has('modeler.toml')).toBe(true);
    expect(result.files.get('er.ttrm')).toBe('def entity foo {}');
  });

  it('keys do not start with /', async () => {
    const input = {
      files: [
        makeFile('artikl.ttrm', 'project/artikl.ttrm', 'def entity artikl {}'),
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