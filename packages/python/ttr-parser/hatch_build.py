# SPDX-License-Identifier: Apache-2.0
"""Hatchling build hook: regenerate the ANTLR Python parser before the wheel is assembled.

This runs `scripts/generate-python-parser.sh` from the build environment so CI
(where the source tree may not contain `_generated/` because it is gitignored)
always produces a fresh parser. Per the always-regen decision for task 1.1.4:
the hook is intentionally unconditional — antlr4 jar is ~5s, well below the
budget for a wheel build, and always-fresh avoids stale `_generated/` drift.

The script downloads the reference antlr4 jar to `$HOME/.cache/antlr/` on first
run. Java 21+ must be on PATH at build time (CI provides it; consumers do not
need it because they install from the prebuilt wheel).
"""

from __future__ import annotations

import os
import subprocess
from pathlib import Path
from typing import Any

from hatchling.builders.hooks.plugin.interface import BuildHookInterface


class CustomBuildHook(BuildHookInterface):
    PLUGIN_NAME = "custom"

    def initialize(self, version: str, build_data: dict[str, Any]) -> None:  # noqa: ARG002
        repo_root = Path(self.root)
        script = repo_root / "scripts" / "generate-python-parser.sh"

        if not script.exists():
            raise RuntimeError(f"generate script missing: {script}")

        # The generate script always invokes the ANTLR jar (it clears any stale
        # _generated/ first), so Java is required on every build — not just when
        # _generated/ is empty. Check unconditionally for a clear error.
        env = os.environ.copy()
        if not env.get("JAVA_HOME") and not _which("java"):
            raise RuntimeError(
                "Java (JDK 21+) is required: the build hook runs "
                "scripts/generate-python-parser.sh, which always invokes the "
                "ANTLR jar. Install Java or build from a prebuilt wheel."
            )

        subprocess.run([str(script)], cwd=str(repo_root), check=True, env=env)


def _which(name: str) -> str | None:
    for directory in os.environ.get("PATH", "").split(os.pathsep):
        candidate = Path(directory) / name
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    return None
