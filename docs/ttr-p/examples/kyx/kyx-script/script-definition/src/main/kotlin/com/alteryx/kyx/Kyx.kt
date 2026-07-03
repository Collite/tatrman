package com.alteryx.kyx

import java.io.File


class Workflow() {

    val _plugins = mutableListOf<Plugin>()

    private val _conections = mutableSetOf<Connection>()

    fun connect(a1: Anchor, a2: Anchor) {
        assert(a1 is OutputAnchor)
        assert(a2 is InputAnchor)
        _conections.add(Connection(a1, a2))
        a1.connectedTo.add(a2)
        a2.connectedTo.add(a1)
        //todo disconect tp clear this

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


    fun Select(init: Select.() -> Unit): Select {
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

    fun Input(init: Input.() -> Unit): Input {
        val s = Input(this)
        s.init()
        _plugins.add(s)
        return s
    }

    fun Output(init: Output.() -> Unit): Output {
        val s = Output(this)
        s.init()
        _plugins.add(s)
        return s
    }

    fun Formula(init: Formula.() -> Unit): Formula {
        val s = Formula(this)
        s.init()
        _plugins.add(s)
        return s
    }

    fun Sort(init: Sort.() -> Unit): Sort {
        val s = Sort(this)
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

//todo: tohle nejde, pac tohle by byl ten 'embedded language' a delam ze strongly typed dynamickej, nebo preprocesovanej, coz neumim
    //takze az jindy

//    fun SQL(sql: String) : SqlText {
//        val txt = SqlText(this)
//        txt.text = sql
//        _plugins.add(txt)
//        return txt
//    }
//
//    fun English(eng: String) : EnglishText {
//        val txt = EnglishText(this)
//        txt.text = eng
//        _plugins.add(txt)
//        return txt
//    }


    protected fun convertToAlteryx(): AlteryxXmlWorkflow {
        val wf = AlteryxXmlWorkflow().apply {
            yxmdVersion = "2021.1"
        }

        wf.nodes.addAll(
            _plugins.map {
                it.convertToAlteryxNode()
            }
        )
        wf.connections.addAll(
            _conections.map {
                it.convertToAlteryxConnection()
            }
        )

        return wf
    }

    fun toXmlString() = AlteryxXmlWorkflowFactory(AlteryxConfigurationMapper()).printToString(convertToAlteryx())

    fun saveAs(fn: String) {
        File(fn).writeText(toXmlString())
    }
}


abstract class Plugin(val wf: Workflow, val nodeType: NodeTypes) {
    val id = nextToolId++
    internal var _config: AlteryxXmlNodeConfiguration = AlteryxXmlNodeConfiguration.getDefaults(nodeType)
    internal var position: Pair<Int, Int> = 150 * id to 50

    var inAnchors: MutableMap<String, Anchor> = mutableMapOf("Input" to InputAnchor("Input", this))
    var outAnchors: MutableMap<String, Anchor> = mutableMapOf("Output" to OutputAnchor("Output", this))

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

    fun convertToAlteryxNode(): AlteryxXmlNode {
        val engine = if (this is Summarize) "Spatial" else "Base"
        return AlteryxXmlNode().apply {
            toolId = id
            properties = AlteryxXmlNodeProperties().apply {
                config = _config
            }
            guiSettings = AlteryxXmlNodeGuiSettings().apply {
                plugin = nodeType.gui(engine)
                position = AlteryxXmlNodeGuiPosition().apply {
                    x = this@Plugin.position.first
                    y = this@Plugin.position.second
                }
            }
            engineSettings = AlteryxXmlNodeEngineSettings().apply {
                engineDll = "Alteryx${engine}PluginsEngine.dll"
                entryPoint = nodeType.engine()
            }
        }
    }

    abstract fun gimmeMyMetadata(anchor: Anchor): Metadata
}

abstract class Anchor(
    val name: String,
    val plugin: Plugin
) {
    val id = nextAnchorId++
    open val metadata: Metadata? = null
    var connectedTo = mutableListOf<Anchor>()

    operator fun plus(next: Anchor): Plugin {
        this.plugin.wf.connect(this, next)
        return next.plugin
    }

    operator fun plus(next: Plugin): Plugin {
        this.plugin.wf.connect(this, next)
        return next
    }
}

class InputAnchor(name: String, plugin: Plugin) : Anchor(name, plugin) {
    override val metadata: Metadata?
        get() = if (connectedTo.isEmpty()) null else connectedTo.first().metadata
}

class OutputAnchor(name: String, plugin: Plugin) : Anchor(name, plugin) {
    override val metadata: Metadata?
        get() = this.plugin.gimmeMyMetadata(this)
}

data class Connection(val fromAnchor: Anchor, val toAnchor: Anchor) {
    fun convertToAlteryxConnection(): AlteryxXmlConnection {
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

infix fun AlteryxXmlNodeRecordInfoField.As(alias: String) {
    this.rename = alias
}

class Record(val plugin: Plugin) {
    val _columns = mutableListOf<AlteryxXmlNodeRecordInfoField>()
    fun Column(init: AlteryxXmlNodeRecordInfoField.() -> Unit): AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            init()
            this@Record._columns.add(this)
        }
    }

    fun toMetadata(): Metadata {
        val metadata = Metadata()
        metadata.columns.addAll(
            _columns.map { recInfo ->
                val name = recInfo.rename ?: recInfo.name ?: ""
                val type = DataType.from(recInfo.fieldType ?: "UNKNOWN")
                Column(
                    name = name,
                    type = type  //todo sizes
                )
            }
        )
        return metadata
    }

    fun fromMetadata(m: Metadata) {
        _columns.clear()
        _columns.addAll(
            m.map {
                AlteryxXmlNodeRecordInfoField().apply {
                    name = it.name
                    fieldType = it.type.toString()
                }
            }
        )
    }
}

class Select(wf: Workflow) : Plugin(wf, NodeTypes.SELECT) {
    fun Column(init: AlteryxXmlNodeRecordInfoField.() -> Unit): AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            init()
            selected = true
            this@Select._config.selRecordInfo?.add(this)
        }
    }

    fun Columns(vararg names: String) {
        names.forEach {
            this._config.selRecordInfo?.add(
                AlteryxXmlNodeRecordInfoField().apply {
                    field = it
                    selected = true
                }
            )
        }
    }

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        val selectMetadata = Metadata()
        this.In.metadata?.columns?.forEach {
            val column = columnThroughConfig(it, _config.selRecordInfo!!)
            if (column != null)
                selectMetadata.add(column)
        }
        return selectMetadata
    }
}

class Filter(wf: Workflow) : Plugin(wf, NodeTypes.FILTER) {

    init {
        outAnchors.clear()
        outAnchors.putAll(
            mapOf(
                "True" to OutputAnchor("True", this),
                "False" to OutputAnchor("False", this)
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
            this._config.filterExpression = value
            field = value
        }

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        return In.metadata ?: Metadata()
    }
}

class Summarize(wf: Workflow) : Plugin(wf, NodeTypes.SUMMARIZE) {

    fun Sum(field: String): AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Sum"
            this@Summarize._config.sumRecordInfo?.add(this)
        }
    }

    fun Avg(field: String): AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Avg"
            this@Summarize._config.sumRecordInfo?.add(this)
        }
    }

    fun Min(field: String): AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Min"
            this@Summarize._config.sumRecordInfo?.add(this)
        }
    }

    fun Max(field: String): AlteryxXmlNodeRecordInfoField {
        return AlteryxXmlNodeRecordInfoField().apply {
            name = field
            action = "Max"
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

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        val metadata = Metadata()
        val inMeta = In.metadata ?: return metadata
        metadata.columns.addAll(
            _config.sumRecordInfo!!.map { recInfo ->
                val name = recInfo.rename ?: recInfo.name ?: ""
                val inCol = inMeta?.columns?.firstOrNull { it.name.equals(recInfo.name, true) }
                val type = inCol?.type ?: DataType.UNKNOWN
                Column(
                    name = name,
                    type = type  //todo sizes
                )
            }
        )
        return metadata
    }

}


class Input(wf: Workflow) : Plugin(wf, NodeTypes.INPUT) {
    var Source: String
        set(value) {
            val fileType = getFileType(value)
            _config.file = AlteryxXmlNodeDataSource().apply {
                dataSource = value
                fileFormat = fileType?.value ?: 0
            }
            _config.formatSpecificOptions = AlteryxXmlNodeFormatSpecific.getFileDefaults(fileType)
//            field = value
        }
        get() = _config.file?.dataSource ?: ""

    private var _record: Record? = null
    private var _source: DataSource? = null
    fun Record(init: Record.() -> Unit): Record {
        val r = Record(this)
        r.init()
        _record = r
        return r
    }

    fun Source(init: DataSource.() -> Unit): DataSource {
        val r = DataSource()
        r.init()
        _source = r
        return r
    }

    fun Source(cf: String): DataSource {
        _source = parseSource(cf)
        val (file, fso) = createConfigFile(_source!!)
        _config.file = file
        _config.formatSpecificOptions = fso
        return _source!!
    }

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        return _record?.toMetadata() ?: DummyDb.getMetadata(Source)
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
                "Left" to OutputAnchor("Left", this),
                "Right" to OutputAnchor("Right", this),
                "Join" to OutputAnchor("Join", this)
            )
        )
        inAnchors.clear()
        inAnchors.putAll(
            mapOf(
                "Left" to InputAnchor("Left", this),
                "Right" to InputAnchor("Right", this)
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

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        if (anchor == OutLeft)
            return InLeft.metadata ?: Metadata()
        if (anchor == OutRight)
            return InRight.metadata ?: Metadata()

        val selectMetadata = Metadata()
        val joinConfig = _config.joinSelectConfiguration?.configurations?.first { it.connection == "Join" }
            ?.selRecordInfo ?: listOf()
        this.InLeft.metadata?.columns?.forEach {
            val column = columnThroughConfig(it, joinConfig, "Left")
            if (column != null)
                selectMetadata.add(column)
        }
        this.InRight.metadata?.columns?.forEach {
            val column = columnThroughConfig(it, joinConfig, "Right")
            if (column != null)
                selectMetadata.add(column)
        }
        return selectMetadata

    }

}


class Browse(wf: Workflow) : Plugin(wf, NodeTypes.BROWSE) {

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        return In.metadata ?: Metadata()
    }
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

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        return In.metadata ?: Metadata()
    }
}

class Formula(wf: Workflow) : Plugin(wf, NodeTypes.FORMULA) {
    infix fun String.As(alias: String) {
        this@Formula.Column(alias, this)
    }

    fun Column(name: String, expression: String) {
        _config.forRecordInfo!!.add(
            AlteryxXmlNodeRecordInfoField().apply {
                field = name
                this.expression = expression
            }
        )
    }

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        return Metadata().apply {
            columns.addAll((In.metadata?.columns ?: listOf()))
            columns.addAll(
                (_config.forRecordInfo?.map {
                    Column(
                        name = it.rename ?: it.field ?: it.name ?: "",
                        type = DataType.from(it.fieldType ?: "Unknown")  //todo sizes
                    )
                } ?: listOf())
            )
        }
    }
}

enum class SortDirection(val value: String) { ASC("Ascending"), DESC("Descending") }

class Sort(wf: Workflow) : Plugin(wf, NodeTypes.SORT) {
    fun By(field: String, direction: SortDirection = SortDirection.ASC) {
        _config.sortRecordInfo!!.add(
            AlteryxXmlNodeRecordInfoField().apply {
                name = field
                order = direction.value
            }
        )
    }

    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
        return In.metadata ?: Metadata()
    }
}

//class EnglishText(wf: Workflow) : Plugin (wf, NodeTypes.ENGLISH) {
//    var text: String = ""
//    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
//        TODO("Not yet implemented")
//    }
//}
//class SqlText(wf: Workflow) :Plugin(wf, NodeTypes.SQL) {
//    var text: String = ""
//    override fun gimmeMyMetadata(anchor: Anchor): Metadata {
//        TODO("Not yet implemented")
//    }
//
//}

fun workflow(init: Workflow.() -> Unit): Workflow {
    val s = Workflow()
    s.init()
    return s
}


var nextToolId = 1
var nextAnchorId = 1



