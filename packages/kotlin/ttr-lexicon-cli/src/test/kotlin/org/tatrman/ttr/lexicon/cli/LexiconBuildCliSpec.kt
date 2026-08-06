// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.cli

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain as shouldContainElement
import io.kotest.matchers.collections.shouldNotContain as shouldNotContainElement
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttr.lexicon.LexiconArchive
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.snapshot.SnapshotId
import org.tatrman.ttr.snapshot.SnapshotReadResult
import org.tatrman.ttr.snapshot.SnapshotReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * RV-P3.1 T2 — the CLI that actually emits an estate's lexicon archive.
 *
 * Every case builds its own estate in a temp dir, in the shape
 * `ttr-lexicon-compile/src/test/resources/estate/` established at P1.2 T7: a root-level
 * `lexicon/` data area (RV-36) plus the `model/lexicon/<locale>` TTR-M sugar, over a `model/` tree
 * the CLI loads for itself. That last part is the whole point of this list — before it,
 * `LexiconBuild.run` took a `Model` nobody could produce from a repo root.
 */
class LexiconBuildCliSpec :
    FunSpec({

        /**
         * The smallest estate that exercises both declared surfaces and the metadata layer.
         *
         * @param area extra files under `lexicon/`, relative path → content.
         * @param modelFiles extra files under `model/`, relative path → content.
         */
        fun estate(
            root: Path,
            area: Map<String, String> = emptyMap(),
            modelFiles: Map<String, String> = emptyMap(),
        ): Path {
            val files =
                mapOf(
                    "model/er/customer.ttrm" to
                        """
                        model er

                        def entity customer {
                            displayLabel: { cs: "Odběratel", en: "Customer" }
                        }
                        """.trimIndent(),
                ) + modelFiles.mapKeys { (k, _) -> "model/$k" } + area.mapKeys { (k, _) -> "lexicon/$k" }

            for ((rel, content) in files) {
                val path = root.resolve(rel)
                path.parent.createDirectories()
                path.writeText(content + "\n")
            }
            return root
        }

        val aliases =
            """
            schema: ttr-lexicon/v1
            defaults: { lang: cs }
            entries:
              - terms:
                  - { text: "zákazník" }
                  - { text: "customer", lang: en, method: TOKENS }
                target: er.entity.customer
            """.trimIndent()

        val sugar =
            """
            model lexicon locale cs

            def term customer_cs {
                description: "zákazník synonyma"
                for: er.entity.customer
                forms: ["odběratel"]
            }
            """.trimIndent()

        fun run(
            repoRoot: Path,
            out: Path,
            check: Boolean = false,
            includeStdlib: Boolean = false,
            verbose: Boolean = false,
        ): CliOutcome =
            LexiconBuildCli.run(
                repoRoot = repoRoot,
                out = out,
                check = check,
                includeStdlib = includeStdlib,
                verbose = verbose,
            )

        // ---- (a) an estate with both declared surfaces produces an archive --------------------

        test("(a) a repo with a lexicon area and TTR-M sugar produces an archive at --out") {
            val root =
                estate(
                    Files.createTempDirectory("estate-a"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                    modelFiles = mapOf("lexicon/cs/terms.ttrm" to sugar),
                )
            val out = root.resolve("generated/lexicon.tar.zst")

            val outcome = run(root, out)

            withClue(outcome.stderr + outcome.stdout) { outcome.exitCode shouldBe LexiconBuildCli.EXIT_OK }
            out.exists() shouldBe true
            // The id the command prints IS the archive's content id — not a second hash of it.
            SnapshotId.of(out.readBytes()) shouldBe outcome.id
            outcome.stdout shouldContain outcome.id!!

            val entries = LexiconBuildCli.readBack(out).lexicon.entries
            entries.map { it.termNormalized }.toSet() shouldContainElement "zákazník"
            entries.map { it.termNormalized }.toSet() shouldContainElement "odběratel"
            // The METADATA layer rides along without anyone authoring it (RV-39).
            entries.map { it.termNormalized }.toSet() shouldContainElement "odběratel"
        }

        // ---- (b) determinism ------------------------------------------------------------------

        test("(b) two runs over the same estate emit byte-identical archives") {
            val root =
                estate(
                    Files.createTempDirectory("estate-b"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                    modelFiles = mapOf("lexicon/cs/terms.ttrm" to sugar),
                )
            val first = root.resolve("out/one.tar.zst")
            val second = root.resolve("out/two.tar.zst")

            run(root, first).exitCode shouldBe LexiconBuildCli.EXIT_OK
            run(root, second).exitCode shouldBe LexiconBuildCli.EXIT_OK

            first.readBytes() shouldBe second.readBytes()
        }

        test("(b) the default builtAt is a fixed stamp, not the clock — otherwise --check is noise") {
            // The header field is caller-supplied precisely so the compiler cannot reach for
            // Instant.now(); a CLI that reached for it instead would move the archive id on every
            // invocation and make the T4 drift gate report drift forever.
            LexiconBuildCli.defaultBuiltAt(env = emptyMap()) shouldBe "1970-01-01T00:00:00Z"
            LexiconBuildCli.defaultBuiltAt(env = mapOf("SOURCE_DATE_EPOCH" to "1754438400")) shouldBe
                "2025-08-06T00:00:00Z"
        }

        // ---- (c) an estate with no declared layer at all ---------------------------------------

        test("(c) a repo with no lexicon dir and no lexicon unit still emits, with zero warnings") {
            val root = estate(Files.createTempDirectory("estate-c"))
            val out = root.resolve("out/lexicon.tar.zst")

            val outcome = run(root, out)

            outcome.exitCode shouldBe LexiconBuildCli.EXIT_OK
            out.exists() shouldBe true
            outcome.warnings.shouldBeEmpty()
            // "Flag = the files" (P1.2): the metadata layer still compiles, so the archive is
            // real content, not an empty shell.
            LexiconBuildCli.readBack(out).lexicon.entries shouldNotBe emptyList<Any>()
        }

        // ---- (d) a validation failure is fatal and writes nothing -------------------------------

        test("(d) an RG-LEX violation exits non-zero, names code/file/line, and writes no archive") {
            val root =
                estate(
                    Files.createTempDirectory("estate-d"),
                    area =
                        mapOf(
                            "aliases/broken.lex.yaml" to
                                """
                                schema: ttr-lexicon/v1
                                entries:
                                  - terms:
                                      - { text: "zákazník", match: [{ norm: klingon, exact: 1.0 }] }
                                    target: er.entity.customer
                                """.trimIndent(),
                        ),
                )
            val out = root.resolve("out/lexicon.tar.zst")

            val outcome = run(root, out)

            outcome.exitCode shouldBe LexiconBuildCli.EXIT_FAILED
            out.exists() shouldBe false
            outcome.stderr shouldContain "RG-LEX-013"
            outcome.stderr shouldContain "aliases/broken.lex.yaml"
            // Every violation carries a line — an author fixing a file needs the position, and a
            // count of problems without positions is not actionable.
            outcome.violations.forEach { it.provenance.line shouldNotBe 0 }
        }

        // ---- (e) a dangling ref is a warning, never fatal (RV-20) -------------------------------

        test("(e) a dangling model ref warns, exits zero, and the archive IS written") {
            val root =
                estate(
                    Files.createTempDirectory("estate-e"),
                    area =
                        mapOf(
                            "aliases/ghost.lex.yaml" to
                                """
                                schema: ttr-lexicon/v1
                                defaults: { lang: cs }
                                entries:
                                  - terms: [{ text: "duch" }]
                                    target: er.entity.ghost
                                """.trimIndent(),
                        ),
                )
            val out = root.resolve("out/lexicon.tar.zst")

            val outcome = run(root, out)

            outcome.exitCode shouldBe LexiconBuildCli.EXIT_OK
            out.exists() shouldBe true
            outcome.warnings.map { it.code } shouldContainElement "RG-LEXC-001"
            outcome.stderr shouldContain "er.entity.ghost"
            LexiconBuildCli
                .readBack(out)
                .lexicon.entries
                .map { it.termNormalized } shouldNotContainElement "duch"
        }

        // ---- (f) --check, the drift gate --------------------------------------------------------

        test("(f) --check on an up-to-date archive exits zero and writes nothing") {
            val root =
                estate(
                    Files.createTempDirectory("estate-f1"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                )
            val out = root.resolve("out/lexicon.tar.zst")
            run(root, out).exitCode shouldBe LexiconBuildCli.EXIT_OK
            val committed = out.readBytes()

            val outcome = run(root, out, check = true)

            outcome.exitCode shouldBe LexiconBuildCli.EXIT_OK
            out.readBytes() shouldBe committed
        }

        test("(f) --check on a stale archive exits 3 and names both ids") {
            val root =
                estate(
                    Files.createTempDirectory("estate-f2"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                )
            val out = root.resolve("out/lexicon.tar.zst")
            run(root, out).exitCode shouldBe LexiconBuildCli.EXIT_OK
            val committed = out.readBytes()
            val committedId = SnapshotId.of(committed)

            // Author a new alias — the vocabulary moved, the committed artifact did not.
            root.resolve("lexicon/aliases/more.lex.yaml").writeText(
                """
                schema: ttr-lexicon/v1
                defaults: { lang: cs }
                entries:
                  - terms: [{ text: "klient" }]
                    target: er.entity.customer
                """.trimIndent() + "\n",
            )

            val outcome = run(root, out, check = true)

            outcome.exitCode shouldBe LexiconBuildCli.EXIT_DRIFT
            outcome.stderr shouldContain "stale"
            outcome.stderr shouldContain committedId
            outcome.stderr shouldContain outcome.id!!
            // A check never writes. Re-running the build is the author's call, not the gate's.
            out.readBytes() shouldBe committed
        }

        test("(f) --check with no artifact at --out exits 3 and says to generate one") {
            val root = estate(Files.createTempDirectory("estate-f3"))
            val out = root.resolve("out/absent.tar.zst")

            val outcome = run(root, out, check = true)

            outcome.exitCode shouldBe LexiconBuildCli.EXIT_DRIFT
            outcome.stderr shouldContain "no archive at"
            out.exists() shouldBe false
        }

        // ---- T1 ruling: a model that will not load is fatal --------------------------------------

        test("a model that fails to load is FATAL — never an archive full of dangling refs") {
            // The failure mode this guards: with no usable model every targetRef is unknown, so
            // RV-20 would drop the entire declared layer, warn, exit zero, and emit a valid,
            // plausible, nearly-empty archive. Silent and well-formed is the worst kind of wrong.
            val root =
                estate(
                    Files.createTempDirectory("estate-badmodel"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                    modelFiles = mapOf("er/broken.ttrm" to "def entity {{{ this is not TTR"),
                )
            val out = root.resolve("out/lexicon.tar.zst")

            val outcome = run(root, out)

            outcome.exitCode shouldBe LexiconBuildCli.EXIT_FAILED
            out.exists() shouldBe false
            outcome.stderr shouldContain "model"
        }

        // ---- the hartland shape: a declared package that fights the directory ---------------------

        test("a `package` declaration that disagrees with the directory does NOT stop the build") {
            // Not hypothetical, and the reason this tolerance exists: hartland declares
            // `package hartland` in all 26 of its model files ON PURPOSE (BM-9 — one flat package
            // over a kind-directory tree, so cross-kind refs need no import) and says so in
            // modeler.toml with `[packages] layout = "off"`. That switch is read by the TypeScript
            // linter, not by FileBasedSource, which raises the mismatch regardless. Treating it as
            // fatal would leave this command unable to build the one estate p3-2 targets.
            val root =
                estate(
                    Files.createTempDirectory("estate-pkg"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                    modelFiles = mapOf("lexicon/cs/terms.ttrm" to "package estate\n\n" + sugar),
                )
            val out = root.resolve("out/lexicon.tar.zst")

            val outcome = run(root, out)

            withClue(outcome.stderr) { outcome.exitCode shouldBe LexiconBuildCli.EXIT_OK }
            // Reported, never swallowed — an author still gets to see it.
            outcome.stderr shouldContain "package-declaration-mismatch"
            // And the vocabulary it declares is really in the artifact: the tolerance is safe
            // because ModelRefIndex keys on QualifiedName.dotted(), which drops the package.
            LexiconBuildCli
                .readBack(out)
                .lexicon.entries
                .map { it.termNormalized } shouldContainElement "odběratel"
        }

        // ---- the stock conceptual roles (RV-P3.2 finding) ---------------------------------------

        test("a model using stock `roles: [...]` loads — the cnc vocabulary is registered") {
            // Found by running this command over hartland: every `roles: [fact]` /
            // `roles: [dimension]` in a real estate's model came back as
            // `ttr/unimported-reference`, which is fatal, so the CLI could not load ANY real
            // model. The stock roles come from BuiltinStockSource, which the metadata service
            // registers ahead of user sources at boot and which a single-source load never sees.
            val root =
                estate(
                    Files.createTempDirectory("estate-roles"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                    modelFiles =
                        mapOf(
                            "er/roles.ttrm" to
                                """
                                model er

                                def entity orders { roles: [fact] }
                                """.trimIndent(),
                        ),
                )
            val out = root.resolve("out/lexicon.tar.zst")

            val outcome = run(root, out)

            withClue(outcome.stderr) { outcome.exitCode shouldBe LexiconBuildCli.EXIT_OK }
            outcome.stderr shouldNotContain "unimported-reference"
        }

        // ---- --no-stdlib ------------------------------------------------------------------------

        test("--no-stdlib maps to includeStdlib=false and nothing else") {
            val root =
                estate(
                    Files.createTempDirectory("estate-stdlib"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                )
            val with = root.resolve("out/with.tar.zst")
            val without = root.resolve("out/without.tar.zst")

            run(root, with, includeStdlib = true).exitCode shouldBe LexiconBuildCli.EXIT_OK
            run(root, without, includeStdlib = false).exitCode shouldBe LexiconBuildCli.EXIT_OK

            val operatorsWith = LexiconBuildCli.readBack(with).operators.operators
            val operatorsWithout = LexiconBuildCli.readBack(without).operators.operators
            operatorsWithout.keys.shouldBeEmpty()
            operatorsWith shouldNotBe emptyMap<String, Any>()
        }

        // ---- the archive is what a CONSUMER expects ----------------------------------------------

        test("the emitted archive is a kind:lexicon snapshot, readable through the consumer path") {
            val root =
                estate(
                    Files.createTempDirectory("estate-kind"),
                    area = mapOf("aliases/er.lex.yaml" to aliases),
                )
            val out = root.resolve("out/lexicon.tar.zst")
            run(root, out).exitCode shouldBe LexiconBuildCli.EXIT_OK

            val read = SnapshotReader.read(out.readBytes())
            read.shouldBeInstanceOf<SnapshotReadResult.Ok>()
            read.contents.manifest.kind shouldBe LexiconArchive.KIND
            read.contents.manifest.resolvedFrom[LexiconArchive.RESOLVED_FROM_MODEL]!! shouldContain "sha256:"
        }
    })
