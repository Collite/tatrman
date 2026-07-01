package com.alteryx.kyx

import com.alteryx.workflow.*

class Workflow {
    val _plugins = mutableListOf<Plugin>()

//    operator fun Plugin.unaryPlus() {
//        _plugins.add(this)
//    }

    private val _conections = mutableSetOf<Connection>()

    fun connect(a1: Anchor, a2: Anchor) {
        _conections.add(Connection(a1, a2))
    }

    fun connect(p1: Plugin, a2: Anchor) {
        connect(p1.Out, a2)
    }

    fun connect(a1: Anchor, p2: Plugin) {
        connect(a1, p2.In)
    }

    fun connect(p1: Plugin, p2: Plugin) {
        connect(p1.Out, p2.In)
    }


    fun Select(init: Select.() -> Unit): Plugin {
        val s = Select(this)
        s.init()
        _plugins.add(s)
        return s
    }

    fun Filter(init: Filter.() -> Unit): Filter {
        val s = Filter(this)
        s.init()
        _plugins.add(s)
        return s
    }

    fun Summarize(init: Summarize.() -> Unit): Summarize {
        val s = Summarize(this)
        s.init()
//        addPlugin(s, init)
        _plugins.add(s)
        return s
    }

    fun Input(init: Input.() -> Unit): Plugin {
        val s = Input(this)
        s.init()
        _plugins.add(s)
        return s
    }

    fun Output(init: Output.() -> Unit): Plugin {
        val s = Output(this)
        s.init()
        _plugins.add(s)
        return s
    }

    fun Join(init: Join.() -> Unit): Join {
        val join = Join(this)
        join.init()
        _plugins.add(join)
        return join
    }

    fun Join(a1: Anchor, a2: Anchor, init: Join.() -> Unit): Join {
        val join = Join(a1, a2, this)
        join.init()
        _plugins.add(join)
        return join
    }

    fun Join(a1: Plugin, a2: Anchor, init: Join.() -> Unit): Join {
        val join = Join(a1, a2, this)
        join.init()
        _plugins.add(join)
        return join
    }

    fun Join(a1: Anchor, a2: Plugin, init: Join.() -> Unit): Join {
        val join = Join(a1, a2, this)
        join.init()
        _plugins.add(join)
        return join
    }

    fun Join(a1: Plugin, a2: Plugin, init: Join.() -> Unit): Join {
        val join = Join(a1, a2, this)
        join.init()
        _plugins.add(join)
        return join
    }

    val Browse: Browse
        get() {
            val browse = Browse(this)
            _plugins.add(browse)
            return browse
        }


    protected fun convertToAlteryx() : AlteryxXmlWorkflow {
        val wf = AlteryxXmlWorkflow().apply {
            yxmdVersion = "2021.1"
        }

        wf.nodes .addAll(
            _plugins.map {
                it.convertToAlteryxNode()
            }
        )
        wf.connections .addAll(
            _conections.map {
                it.convertToAlteryxConnection()
            }
        )

        return wf
    }

    fun toXmlString() = AlteryxXmlWorkflowFactory().printToString(convertToAlteryx())

}


abstract class Plugin(val wf: Workflow, val nodeType: NodeTypes) {
    val id = nextToolId++
    protected var _config: AlteryxXmlNodeConfiguration = AlteryxXmlNodeConfiguration.getDefaults(nodeType)

    var inAnchors: MutableMap<String, Anchor> = mutableMapOf("Input" to Anchor("Input", "Input", this))
    var outAnchors: MutableMap<String, Anchor> = mutableMapOf("Output" to Anchor("Output", "Output", this))
    operator fun plus(next: Plugin): Plugin {
        wf.connect(this, next)
        return next
    }

    operator fun plus(next: Anchor): Plugin {
        wf.connect(this, next)
        return next.plugin
    }

    open val Out: Anchor
        get() = outAnchors["Output"]!!
    open val In: Anchor
        get() = inAnchors["Input"]!!

    fun convertToAlteryxNode() : AlteryxXmlNode {
        val engine = if (this is Summarize) "Spatial" else "Base"
        return AlteryxXmlNode().apply {
            toolId = id
            properties = AlteryxXmlNodeProperties().apply {
                config = _config
            }
            guiSettings = AlteryxXmlNodeGuiSettings().apply {
                plugin = nodeType.gui(engine)
                position = AlteryxXmlNodeGuiPosition().apply {
                    x = id * 50
                    y = 50
                }
            }
            engineSettings = AlteryxXmlNodeEngineSettings().apply {
                engineDll = "Alteryx${engine}PluginsEngine.dll"
                entryPoint = nodeType.engine()
            }
        }
    }
}

class Anchor(
    val kind: String,
    val name: String,
    val plugin: Plugin
) {
    val id = nextAnchorId++

    operator fun plus(next: Anchor): Plugin {
        this.plugin.wf.connect(this, next)
        return next.plugin
    }

    operator fun plus(next: Plugin): Plugin {
        this.plugin.wf.connect(this, next)
        return next
    }

}

data class Connection(val fromAnchor: Anchor, val toAnchor: Anchor) {
    fun convertToAlteryxConnection () : AlteryxXmlConnection {
        return AlteryxXmlConnection().apply {
            origin = AlteryxXmlConnectionOrigin().apply {
                toolId = fromAnchor.plugin.id
                anchor = fromAnchor.name
            }
            destination = AlteryxXmlConnectionDestination().apply {
                toolId = toAnchor.plugin.id
                anchor = toAnchor.name
            }
        }

    }
}
infix fun AlteryxXmlNodeRecordInfoField.As ( alias: String) {
this.rename = alias
}

class Select(wf: Workflow) : Plugin(wf, NodeTypes.SELECT) {
    fun Column(init: AlteryxXmlNodeRecordInfoField.() -> Unit) : AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            init()
            this@Select._config.selRecordInfo?.add(this)
        }
    }
    fun Columns(vararg names: String) {
        names.forEach {
            this._config.selRecordInfo?.add(
                AlteryxXmlNodeRecordInfoField().apply {
                    name = it
                    selected = true
                }
            )
        }
    }


}

class Filter(wf: Workflow) : Plugin(wf, NodeTypes.FILTER) {

    init {
        outAnchors.clear()
        outAnchors.putAll(
            mapOf(
                "True" to Anchor("Output", "True", this),
                "False" to Anchor("Output", "False", this)
            )
        )
    }

    val True: Anchor
        get() = outAnchors["True"]!!
    val False: Anchor
        get() = outAnchors["False"]!!

    override val Out: Anchor
        get() = True

    var Expression: String = ""
        set(value) {
            this._config.filterExpression = escape(value)
            field = value
        }
}

class Summarize(wf: Workflow) : Plugin(wf, NodeTypes.SUMMARIZE) {

    fun Sum(field: String) : AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Sum"
            this@Summarize._config.sumRecordInfo?.add(this)
        }
    }

    fun Avg(field: String) : AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Sum"
            this@Summarize._config.sumRecordInfo?.add(this)
        }
    }

    fun Min(field: String) : AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Sum"
            this@Summarize._config.sumRecordInfo?.add(this)
        }
    }

    fun Max(field: String) : AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Sum"
            this@Summarize._config.sumRecordInfo?.add(this)
        }
    }

    fun GroupBy(vararg f: String) {
        f.forEach {
            this._config.sumRecordInfo?.add(
                AlteryxXmlNodeRecordInfoField().apply {
                    name = it
                    action = "GroupBy"
                }
            )
        }

    }
}


class Input(wf: Workflow) : Plugin(wf, NodeTypes.INPUT) {
    var Source: String = ""
        set(value) {
            val fileType = getFileType(value)
            _config.file = AlteryxXmlNodeDataSource().apply {
                dataSource = value
                fileFormat = fileType?.value ?: 0
            }
            _config.formatSpecificOptions = AlteryxXmlNodeFormatSpecific.getFileDefaults(fileType)
            field = value
        }
}

class Join(wf: Workflow) : Plugin(wf, NodeTypes.JOIN) {

    constructor(a1: Anchor, a2: Anchor, wf: Workflow) : this(wf) {
        wf.connect(a1, InLeft)
        wf.connect(a2, InRight)
    }

    constructor(a1: Anchor, p2: Plugin, wf: Workflow) : this(wf) {
        wf.connect(a1, InLeft)
        wf.connect(p2, InRight)

    }

    constructor(p1: Plugin, a2: Anchor, wf: Workflow) : this(wf) {
        wf.connect(p1, InLeft)
        wf.connect(a2, InRight)

    }

    constructor(p1: Plugin, p2: Plugin, wf: Workflow) : this(wf) {
        wf.connect(p1, InLeft)
        wf.connect(p2, InRight)

    }


    init {
        outAnchors.clear()
        outAnchors.putAll(
            mapOf(
                "Left" to Anchor("Output", "Left", this),
                "Right" to Anchor("Output", "Right", this),
                "Join" to Anchor("Output", "Join", this)
            )
        )
        inAnchors.clear()
        inAnchors.putAll(
            mapOf(
                "Left" to Anchor("Input", "Left", this),
                "Right" to Anchor("Input", "Right", this)
            )
        )
        _config.joinInfo = mutableListOf()
        val leftJoinInfo = AlteryxXmlNodeJoinInfo()
        leftJoinInfo.connectionName = "Left"
        val rightJoinInfo = AlteryxXmlNodeJoinInfo()
        rightJoinInfo.connectionName = "Right"
        _config.joinInfo?.add(leftJoinInfo)
        _config.joinInfo?.add(rightJoinInfo)
    }

    val OutLeft: Anchor
        get() = outAnchors["Left"]!!
    val OutRight: Anchor
        get() = outAnchors["Right"]!!
    val OutInner: Anchor
        get() = outAnchors["Join"]!!

    val OutJoin: Anchor
        get() = OutInner

    val Join: Anchor
        get() = OutInner

    override val Out: Anchor
        get() = OutInner

    val InLeft: Anchor
        get() = inAnchors["Left"]!!
    val InRight: Anchor
        get() = inAnchors["Right"]!!

    fun On(L: String, R: String) {
        _config.joinInfo!![0].fieldList.add(
            AlteryxXmlNodeRecordInfoField().apply {
                field = L
            }
        )
        _config.joinInfo!![1].fieldList.add(
            AlteryxXmlNodeRecordInfoField().apply {
                field = R
            }
        )
    }

    fun And(L: String, R: String) {
        _config.joinInfo!![0].fieldList.add(
            AlteryxXmlNodeRecordInfoField().apply {
                field = L
            }
        )
        _config.joinInfo!![1].fieldList.add(
            AlteryxXmlNodeRecordInfoField().apply {
                field = R
            }
        )

    }

}


class Browse(wf: Workflow) : Plugin(wf, NodeTypes.BROWSE) {
}

class Output(wf: Workflow) : Plugin(wf, NodeTypes.OUTPUT) {
    var Destination: String = ""
        set(value) {
            val fileType = getFileType(value)
            _config.file = AlteryxXmlNodeDataSource().apply {
                dataSource = value
                fileFormat = fileType?.value ?: 0
            }
            _config.formatSpecificOptions = AlteryxXmlNodeFormatSpecific.getFileDefaults(fileType)
            field = value
        }
}

fun workflow(init: Workflow.() -> Unit): Workflow {
    val wf = Workflow()
    wf.init()
    return wf
}



var nextToolId = 1
var nextAnchorId = 1

fun escape(s: String) :String {
    return s.replace(">", "&gt;").replace("<", "&lt;")  //todo continue :)
}

