// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.airflow3

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Test helper: assert generated Python is syntactically valid via `python3 -m py_compile` (skips if no python3). */
internal object AirflowPy {
    fun assertCompiles(script: String) {
        val python = which("python3") ?: which("python")
        if (python == null) {
            System.err.println("SKIP: python3 not on PATH — cannot py_compile the generated DAG")
            return
        }
        val tmp = Files.createTempFile("dag", ".py")
        Files.writeString(tmp, script)
        val proc =
            ProcessBuilder(python.toString(), "-m", "py_compile", tmp.toString())
                .redirectErrorStream(true)
                .start()
        val out = proc.inputStream.readBytes().decodeToString()
        val code = proc.waitFor()
        if (code != 0) throw AssertionError("py_compile failed:\n$out\n---\n$script")
    }

    private fun which(cmd: String): Path? =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparatorChar)
            .map { Paths.get(it, cmd) }
            .firstOrNull { Files.isExecutable(it) }
}
