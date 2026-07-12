// SPDX-License-Identifier: Apache-2.0
declare module 'cytoscape-cose-bilkent' {
  const coseBilkent: cytoscape.Ext;
  export = coseBilkent;
}

declare module 'cytoscape-node-html-label' {
  function nodeHtmlLabel(cytoscape: typeof import('cytoscape')): void;
  export = nodeHtmlLabel;
}

declare module 'cytoscape' {
  interface Core {
    nodeHtmlLabel(options: Array<{
      query: string;
      halign?: 'left' | 'center' | 'right';
      valign?: 'top' | 'center' | 'bottom';
      tpl: (data: Record<string, unknown>) => string;
    }>): void;
  }
}