// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * Belt to the `dependencyRules` Gradle task's braces (MD3 / architecture §2.1):
 * the Gradle task guards the published POM's runtime classpath; this spec guards
 * the *test* runtime's classpath. Neither ktor, grpc, opentelemetry, jgit, nor
 * protobuf may ride the core module — they belong to the service wrappers
 * (kantheon) or the `-git` artifact only.
 */
class DependencyRulesSpec :
    StringSpec({

        "no banned dependency jars on the ttr-metadata test classpath" {
            val bannedFragments =
                listOf("/ktor", "/grpc", "/opentelemetry", "org.eclipse.jgit", "/protobuf-java")
            val classpath = System.getProperty("java.class.path").split(java.io.File.pathSeparator)
            val offenders =
                classpath.filter { entry -> bannedFragments.any { entry.contains(it) } }
            offenders.shouldBeEmpty()
        }
    })
