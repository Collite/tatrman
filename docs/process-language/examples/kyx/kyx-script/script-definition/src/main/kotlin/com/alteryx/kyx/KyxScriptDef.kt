package com.alteryx.kyx

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
    fileExtension = "kyx.kts",
    compilationConfiguration = KyxScriptConfig::class
)
abstract class KyxScript {

}

object KyxScriptConfig : ScriptCompilationConfiguration(
    {
        defaultImports(
            "com.alteryx.kyx.workflow",
            "com.alteryx.kyx.Input",
            "com.alteryx.kyx.DataSource",
            "com.alteryx.kyx.Filter",
            "com.alteryx.kyx.Select",
            "com.alteryx.kyx.Summarize",
            "com.alteryx.kyx.Output",
            "com.alteryx.kyx.Formula",
            "com.alteryx.kyx.Sort",
            "com.alteryx.kyx.Join",
            "com.alteryx.kyx.Browse",
            "com.alteryx.kyx.Record",
            "com.alteryx.kyx.As"
        )
        jvm {
            // Extract the whole classpath from context classloader and use it as dependencies
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
    }
)