// SPDX-License-Identifier: Apache-2.0
export interface DemoFiles {
  rootName: string;
  files: Map<string, string>;
}

export async function loadDemoFiles(demoName: string): Promise<DemoFiles> {
  const base = `/samples/${demoName}/`;
  const indexRes = await fetch(`${base}index.json`);
  if (!indexRes.ok) throw new Error(`Demo manifest not found: ${base}index.json`);
  const filePaths: string[] = await indexRes.json();

  const files = new Map<string, string>();
  await Promise.all(
    filePaths.map(async (relPath) => {
      const url = `${base}${relPath}`;
      const res = await fetch(url);
      if (!res.ok) throw new Error(`Failed to fetch ${url}`);
      const text = await res.text();
      files.set(relPath, text);
    })
  );

  return { rootName: demoName, files };
}