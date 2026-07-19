// SPDX-License-Identifier: Apache-2.0
//
// The copyable federation URL for the active view (FO contracts §3: "every Studio
// surface renders a stable, copyable URL for its current context"). Read-only —
// no edit code (FO-21 safe). The URL is the §3 federation projection of the active
// tab (see `federation-link.ts`), never the richer internal §6 address bar.

import { useState } from 'react';

export function CopyLinkButton({ url }: { url: string }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard?.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard denied (no permission / insecure context) — the URL stays visible
      // in the title for a manual copy; never throw from a share affordance.
    }
  };

  return (
    <button
      type="button"
      data-testid="copy-federation-link"
      data-federation-url={url}
      aria-label={`Copy link to this view: ${url}`}
      title={url}
      onClick={copy}
      style={{
        marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 6,
        border: '1px solid #CBD8E6', borderRadius: 6, background: '#fff', color: '#33506e',
        padding: '3px 10px', fontSize: 12, cursor: 'pointer',
      }}
    >
      🔗 {copied ? 'Copied' : 'Copy link'}
    </button>
  );
}
