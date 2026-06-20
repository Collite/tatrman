"""Package inference from a file path (← `package-inference.ts`, contracts §3.4).

`<root>/foo/bar/baz.ttr` → `foo.bar`; a file directly under the root → empty
package. Advisory only: qnames are declaration-driven (the `package` decl), so
this is never used to *build* qnames — it just mirrors modeler's directory
convention for tooling that wants the expected package of a file.
"""

from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlparse

from .qname import Qname

_EXT = re.compile(r"\.(ttr|ttrg)$")


def infer_package(file_path: str | Path, project_root: str | Path) -> Qname:
    path = str(file_path)
    root = str(project_root)
    if path.startswith("file://"):
        path = urlparse(path).path

    relative = path[len(root):] if path.startswith(root) else path
    segments = [s for s in relative.split("/") if s]

    if len(segments) >= 2:
        dirs = [_EXT.sub("", s) for s in segments[:-1]]
        return Qname(".".join(dirs))
    return Qname("")
