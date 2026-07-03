package com.alteryx.kyx


import jakarta.xml.bind.Marshaller
import jakarta.xml.bind.annotation.*
import org.eclipse.persistence.jaxb.JAXBContextFactory
import org.eclipse.persistence.jaxb.xmlmodel.ObjectFactory
import org.eclipse.persistence.oxm.annotations.XmlCDATA
import org.w3c.dom.Element
import java.io.StringReader
import java.io.StringWriter


@XmlRootElement(name = "AlteryxDocument")
@XmlAccessorType(XmlAccessType.FIELD)
class AlteryxXmlWorkflow {

    @field:XmlAttribute(name = "yxmdVer")
    var yxmdVersion: String = ""

    @XmlElementWrapper(name = "Nodes")
    @XmlElement(name = "Node")
    var nodes: MutableList<AlteryxXmlNode> = mutableListOf()

    @XmlElementWrapper(name = "Connections")
    @XmlElement(name = "Connection")
    var connections: MutableList<AlteryxXmlConnection> = mutableListOf()

}

//@XmlRootElement(name = "Node")
@XmlAccessorType(XmlAccessType.FIELD)
class AlteryxXmlNode {
    @field:XmlAttribute(name = "ToolID")
    var toolId: Int = 0

    @XmlElement(name = "Properties", required = false)
    var properties: AlteryxXmlNodeProperties? = null

    @XmlElement(name = "EngineSettings", required = false)
    var engineSettings: AlteryxXmlNodeEngineSettings? = null

    @XmlElement(name = "GuiSettings", required = false)
    var guiSettings: AlteryxXmlNodeGuiSettings? = null

    @XmlElementWrapper(name = "ChildNodes", required = false)
    @XmlElement(name = "Node")
    var childNodes: List<AlteryxXmlNode> = mutableListOf()

    @XmlTransient
    var toolType = ""

}

//@XmlRootElement(name = "Properties")
@XmlAccessorType(XmlAccessType.FIELD)
class AlteryxXmlNodeProperties {

    @XmlElement(name = "Annotation")
    var annotation: AlteryxXmlNodeAnnotation? = null

    //  @XmlElementWrapper()
    @XmlElement(name = "MetaInfo")
    var metaInfo: List<AlteryxXmlNodeMetaInfo> = mutableListOf()

    @XmlElement(name = "Configuration")
    var config: AlteryxXmlNodeConfiguration? = null

    @XmlAnyElement(lax = true)
//    var config: AlteryxXmlNodeConfiguration? = null
    var elements: MutableList<Element> = mutableListOf()

}


////@XmlRootElement(name = "EngineSettings")
class AlteryxXmlNodeEngineSettings {
    @field:XmlAttribute(name = "EngineDll")
    var engineDll: String = ""

    @field:XmlAttribute(name = "EngineDllEntryPoint")
    var entryPoint: String = ""
}

//@XmlRootElement(name = "GuiSettings")
class AlteryxXmlNodeGuiSettings {
    @field:XmlAttribute(name = "Plugin")
    var plugin: String = ""

    @XmlElement(name = "Position")
    var position: AlteryxXmlNodeGuiPosition? = null
}

//@XmlRootElement(name = "Position")
class AlteryxXmlNodeGuiPosition {
    @field:XmlAttribute(name = "x")
    var x: Int = 100

    @field:XmlAttribute(name = "y")
    var y: Int = 100
}


class BooleanValue() {
    @XmlAttribute
    var value: Boolean? = null

    constructor(v: Boolean) : this() {
        this.value = v
    }
}

enum class NodeTypes {
    SELECT, SUMMARIZE, SORT, FORMULA, INPUT, OUTPUT, CONTAINER, JOIN, FILTER, BROWSE, UNION;


    fun gui(engine: String): String {
        return "Alteryx${engine}PluginsGui." + when (this) {
            SELECT -> "AlteryxSelect.AlteryxSelect"
            SUMMARIZE -> "Summarize.Summarize"
            SORT -> "Sort.Sort"
            FORMULA -> "Formula.Formula"
            INPUT -> "DbFileInput.DbFileInput"
            OUTPUT -> "DbFileOutput.DbFileOutput"
            CONTAINER -> ""
            JOIN -> "Join.Join"
            FILTER -> "Filter.Filter"
            BROWSE -> "BrowseV2.BrowseV2"
            UNION -> "Union.Union"
        }
    }

    fun engine(): String {
        return "Alteryx" + when (this) {
            SELECT -> "Select"
            SUMMARIZE -> "Summarize"
            SORT -> "Sort"
            FORMULA -> "Formula"
            INPUT -> "DbFileInput"
            OUTPUT -> "DbFileOutput"
            CONTAINER -> ""
            JOIN -> "Join"
            FILTER -> "Filter"
            BROWSE -> "BrowseV2"
            UNION -> "Union"
        }
    }
}

@XmlRootElement(name = "Configuration")
class AlteryxXmlNodeConfiguration {

    @XmlElement(name = "Passwords")
    var password: String? = null

    @XmlElement(name = "File")
    var file: AlteryxXmlNodeDataSource? = null

    @XmlElement(name = "FormatSpecificOptions")
    var formatSpecificOptions: AlteryxXmlNodeFormatSpecific? = null

    @XmlElement(name = "DialectInfo")
    var dialectInfo: AlteryxXmlNodeDialect? = null


    @XmlAttribute(name = "outputConnection")
    var connection: String? = null

    @field:XmlElement(name = "OrderChanged")
    var orderChanged: BooleanValue? = null


    @XmlElement(name = "CommaDecimal")
    var commaDecimal: BooleanValue? = null

    @XmlElementWrapper(name = "SelectFields")
    @XmlElement(name = "SelectField")
    var selRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    @XmlElementWrapper(name = "FormulaFields")
    @XmlElement(name = "FormulaField")
    var forRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    @XmlElementWrapper(name = "SummarizeFields")
    @XmlElement(name = "SummarizeField")
    var sumRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    @XmlElementWrapper(name = "SortInfo")
    @XmlElement(name = "Field")
    var sortRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    //filter

    @XmlElement(name = "Mode")
    var mode: String? = null

    @XmlElement(name = "Simple")
    var simpleFilter: AlteryxXmlNodeSimpleFilter? = null

    @XmlElement(name = "Expression")
    var filterExpression: String? = null

    //Union
    @XmlElement(name = "ByName_ErrorMode")
    var unionByNameErrorMode: String? = null

    @XmlElement(name = "ByName_OutputMode")
    var unionByNameOutputMode: String? = null

    @XmlElement(name = "SetOutputOrder")
    var unionSetOutputOrder: BooleanValue? = null


    //Join
    @XmlElement(name = "JoinInfo")
    var joinInfo: MutableList<AlteryxXmlNodeJoinInfo>? = null

    @XmlElement(name = "SelectConfiguration")
    var joinSelectConfiguration: AlteryxXmlNodeSelectConfigurations? = null


    //Container
    @XmlElement(name = "Disabled")
    var containerDisabled: BooleanValue? = null

    @XmlElement(name = "Folded")
    var containerFolded: BooleanValue? = null

    @XmlElement(name = "Style")
    var containerStyle: AlteryxXmlNodeStyle? = null

    @XmlElement(name = "Caption")
    @XmlCDATA
    var containerCaption: String? = null

    //Browse
    @XmlElement(name = "TempFile")
    var browseTempFile: String? = null

    @XmlElement(name = "TempFileDataProfiling")
    var browseTempFileProfiling: String? = null

    @XmlElement(name = "Layout")
    var browseLayout: AlteryxXmlNodeBrowseLayout? = null


    @XmlAnyElement(lax = true)
    var objects: List<Element> = mutableListOf()

    companion object {

        fun getDefaults(forTool: NodeTypes): AlteryxXmlNodeConfiguration {
            val config = AlteryxXmlNodeConfiguration()
            when (forTool) {
                NodeTypes.SELECT -> {
                    config.orderChanged = BooleanValue(false)
                    config.commaDecimal = BooleanValue(false)
                    config.selRecordInfo = mutableListOf()
                }

                NodeTypes.SUMMARIZE -> config.sumRecordInfo = mutableListOf()
                NodeTypes.SORT -> config.sortRecordInfo = mutableListOf()
                NodeTypes.FORMULA -> config.forRecordInfo = mutableListOf()
                NodeTypes.FILTER -> {
                    config.mode = "Custom"
                }

                else -> {}
            }
            return config
        }

        fun getDefaultsWithUnknown(forTool: NodeTypes): AlteryxXmlNodeConfiguration {
            val config = getDefaults(forTool)
            val unknownField = AlteryxXmlNodeRecordInfoField()
            unknownField.selected = true
            unknownField.name = "*Unknown"
            when (forTool) {
                NodeTypes.SELECT -> config.selRecordInfo!!.add(unknownField)
                NodeTypes.SUMMARIZE -> config.sumRecordInfo!!.add(unknownField)
                NodeTypes.SORT -> config.sortRecordInfo!!.add(unknownField)
                NodeTypes.FORMULA -> config.forRecordInfo!!.add(unknownField)
                else -> {}
            }

            return config
        }
    }
}

//@XmlRootElement(name = "File")
class AlteryxXmlNodeDataSource {
    @field:XmlAttribute(name = "RecordLimit")
    var recordLimit: String = ""

    @field:XmlAttribute(name = "FileFormat")
    var fileFormat: Int = 0

    @XmlValue
    var dataSource: String = ""
}

//@XmlRootElement(name = "FormatSpecificOptions")
class AlteryxXmlNodeFormatSpecific {
    @field:XmlElement(name = "PreSQL")
    var preSql: String? = null

    @field:XmlElement(name = "PostSQL")
    var postSql: String? = null

    @field:XmlElement(name = "ReadCentroids")
    var readCentroids: Boolean? = null

    @field:XmlElement(name = "PreSQLOnConfig")
    var preSqlOnConfig: Boolean? = null

    @field:XmlElement(name = "TableStyle")
    var tableStyle: String? = null

    @field:XmlElement(name = "ReadUncommitted")
    var readUncommitted: Boolean? = null

    @field:XmlElement(name = "Projection")
    var projection: String? = null

    @XmlElement(name = "SpatialObjSize")
    var spatialObjSize: Int? = null

    @XmlElement(name = "TransactionSize")
    var transactionSize: Int? = null

    @XmlElement(name = "IgnoreDropTableSQLErrors")
    var ignoreDropTableSqlErrors: Boolean? = null

    @XmlElement(name = "TransactionMessages")
    var transactionMessages: Boolean? = null

    @XmlElement(name = "OutputOption")
    var outputOption: String? = null

    @XmlElement(name = "HeaderRow")
    var headerRow: Boolean? = null

    @XmlElement(name = "IgnoreErrors")
    var ignoreErrors: Boolean? = null

    @XmlElement(name = "AllowShareWrite")
    var allowShareWrite: Boolean? = null

    @XmlElement(name = "ImportLine")
    var importLine: Int? = null

    @XmlElement(name = "FieldLen")
    var fieldLen: Int? = null

    @XmlElement(name = "SingleThreadRead")
    var singleThreadRead: Boolean? = null

    @XmlElement(name = "IgnoreQuotes")
    var ignoreQuotes: String? = null

    @XmlElement(name = "Delimiter")
    var delimiter: String? = null

    @XmlElement(name = "QuoteRecordBreak")
    var quoteRecordBreak: Boolean? = null

    @XmlElement(name = "CodePage")
    var codePage: String? = null

    @XmlElement(name = "FirstRowData")
    var firstRowData: Boolean? = null


    companion object {
        public fun xlsDefaults(): AlteryxXmlNodeFormatSpecific {
            val fs = AlteryxXmlNodeFormatSpecific()
            fs.importLine = 1
            fs.firstRowData = false
            return fs
        }

        public fun csvDefaults(): AlteryxXmlNodeFormatSpecific {
            val fs = AlteryxXmlNodeFormatSpecific()
            fs.headerRow = true
            fs.ignoreErrors = false
            fs.allowShareWrite = false
            fs.importLine = 1
            fs.fieldLen = 254
            fs.singleThreadRead = false
            fs.ignoreQuotes = "DoubleQuotes"
            fs.delimiter = ","
            fs.quoteRecordBreak = false
            fs.codePage = "28591"
            return fs
        }

        public fun sqlDefaults(): AlteryxXmlNodeFormatSpecific {
            val fs = AlteryxXmlNodeFormatSpecific()
            fs.preSql = ""
            fs.postSql = ""
            fs.readCentroids = false
            fs.preSqlOnConfig = false
            fs.tableStyle = "Quoted"
            fs.readUncommitted = false
            fs.projection = ""
            fs.spatialObjSize = 8000
            fs.transactionSize = 10000
            fs.transactionMessages = false
            return fs
        }

        public fun getFileDefaults(fileTypes: SupportedFileTypes?): AlteryxXmlNodeFormatSpecific? {
            return when (fileTypes) {
                null -> null
                SupportedFileTypes.EXCEL -> xlsDefaults()
                SupportedFileTypes.CSV -> csvDefaults()
            }
        }

    }
}

//@XmlRootElement(name = "DialectInfo")
class AlteryxXmlNodeDialect {
    @field:XmlAttribute(name = "name")
    var dialect: String = ""
}

//@XmlRootElement(name = "Annotation")
class AlteryxXmlNodeAnnotation {
    @field:XmlAttribute(name = "DisplayMode")
    var displayMode: Int = 0

    @field:XmlElement(name = "Name")
    var annotName: String = ""

    @field:XmlElement(name = "DefaultAnnotationText")
    var annotText: String = ""

    //ToDo what's the Left node ?
}

//@XmlRootElement(name = "MetaInfo")
@XmlAccessorType(XmlAccessType.FIELD)
class AlteryxXmlNodeMetaInfo {
    @XmlAttribute(name = "connection")
    var connection: String = ""

    @XmlElementWrapper(name = "RecordInfo")
    @XmlElement(name = "Field")
    var recordInfo: List<AlteryxXmlNodeRecordInfoField> = mutableListOf()
}


//@XmlRootElement(name = "SelectConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
class AlteryxXmlNodeSelectConfigurations {
    @XmlElement(name = "Configuration")
    var configurations = mutableListOf<AlteryxXmlNodeSelectConfiguration>()
}


//@XmlRootElement(name = "Configuration")
@XmlAccessorType(XmlAccessType.FIELD)
class AlteryxXmlNodeSelectConfiguration {
    @XmlAttribute(name = "outputConnection")
    var connection: String = ""

    @XmlElement(name = "OrderChanged@value")
    var orderChanged = false

    @XmlElement(name = "CommaDecimal@value")
    var commaDecimal = false

    @XmlElementWrapper(name = "SelectFields")
    @XmlElement(name = "SelectField")
    var selRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    @XmlElementWrapper(name = "FormulaFields")
    @XmlElement(name = "FormulaField")
    var forRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    @XmlElementWrapper(name = "SummarizeFields")
    @XmlElement(name = "SummarizeField")
    var sumRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    @XmlElementWrapper(name = "SortInfo")
    @XmlElement(name = "Field")
    var sortRecordInfo: MutableList<AlteryxXmlNodeRecordInfoField>? = null

    companion object {


        fun getDefaults(whichList: NodeTypes): AlteryxXmlNodeSelectConfiguration {
            val config = AlteryxXmlNodeSelectConfiguration()
            when (whichList) {
                NodeTypes.SELECT -> {
                    config.orderChanged = false
                    config.commaDecimal = false
                    config.selRecordInfo = mutableListOf()
                }

                NodeTypes.SUMMARIZE -> config.sumRecordInfo = mutableListOf()
                NodeTypes.SORT -> config.sortRecordInfo = mutableListOf()
                NodeTypes.FORMULA -> config.forRecordInfo = mutableListOf()
                else -> {}
            }
            return config
        }

        fun getDefaultsWithUnknown(whichList: NodeTypes): AlteryxXmlNodeSelectConfiguration {
            val config = getDefaults(whichList)
            val unknownField = AlteryxXmlNodeRecordInfoField()
            unknownField.selected = true
            unknownField.name = "*Unknown"
            when (whichList) {
                NodeTypes.SELECT -> config.selRecordInfo!!.add(unknownField)
                NodeTypes.SUMMARIZE -> config.sumRecordInfo!!.add(unknownField)
                NodeTypes.SORT -> config.sortRecordInfo!!.add(unknownField)
                NodeTypes.FORMULA -> config.forRecordInfo!!.add(unknownField)
                else -> {}
            }

            return config
        }
    }
}

//@XmlRootElement(name = "Field")
class AlteryxXmlNodeRecordInfoField {
    @field:XmlAttribute(name = "description")
    var description: String? = null

    @field:XmlAttribute(name = "name")
    var name: String? = null

    @field:XmlAttribute(name = "field")
    var field: String? = null

    @field:XmlAttribute(name = "source")
    var source: String? = null

    @field:XmlAttribute(name = "type")
    var fieldType: String? = null

    @field:XmlAttribute(name = "size")
    var size: Int? = null

    @field:XmlAttribute(name = "scale")
    var scale: Int? = null

    @field:XmlAttribute(name = "rename")
    var rename: String? = null

    @field:XmlAttribute(name = "input")
    var input: String? = null

    @field:XmlAttribute(name = "action")
    var action: String? = null

    @field:XmlAttribute(name = "expression")
    var expression: String? = null

    @field:XmlAttribute(name = "selected")
    var selected: Boolean? = null

    @field:XmlAttribute(name = "order")
    var order: String? = null
}

//@XmlRootElement(name = "Connection")
class AlteryxXmlConnection {
    @field:XmlAttribute(name = "name")
    var name: String? = null

    @XmlElement(name = "Origin")
    var origin: AlteryxXmlConnectionOrigin? = null

    @XmlElement(name = "Destination")
    var destination: AlteryxXmlConnectionDestination? = null
}

//@XmlRootElement(name = "Origin")
class AlteryxXmlConnectionOrigin {
    @field:XmlAttribute(name = "ToolID")
    var toolId: Int = 0

    @field:XmlAttribute(name = "Connection")
    var anchor: String = ""
}

//@XmlRootElement(name = "Destination")
class AlteryxXmlConnectionDestination {
    @field:XmlAttribute(name = "ToolID")
    var toolId: Int = 0

    @field:XmlAttribute(name = "Connection")
    var anchor: String = ""
}

class AlteryxXmlNodeUnionSetOutputOrder {
    @field:XmlAttribute(name = "value")
    var toolId: Boolean = false
}

class AlteryxXmlNodeSimpleFilter {
    @XmlElement(name = "Operator")
    var operator: String? = null

    @XmlElement(name = "Field")
    var field: String? = null

    @XmlElement(name = "Operands")
    var operands: AlteryxXmlNodeSimpleFilterOperands? = null
}

class AlteryxXmlNodeSimpleFilterOperands {
    @XmlElement(name = "IgnoreTimeInDateTime")
    var ignoreTimeInDateTime: Boolean? = true

    @XmlElement(name = "DateType")
    var dateType: String? = "fixed"

    @XmlElement(name = "PeriodDate")
    var periodDate: String? = null

    @XmlElement(name = "PeriodType")
    var periodType: String? = null

    @XmlElement(name = "PeriodCount")
    var periodCount: Int? = 0

    @XmlElement(name = "Operand")
    var operand: String? = null

    @XmlElement(name = "StartDate")
    var startDate: String? = null

    @XmlElement(name = "EndDate")
    var endDate: String? = null
}

class AlteryxXmlNodeJoinInfo {
    @XmlAttribute(name = "connection")
    var connectionName: String? = null

    @XmlElement(name = "Field")
    var fieldList: MutableList<AlteryxXmlNodeRecordInfoField> = mutableListOf()
}

class AlteryxXmlNodeBrowseLayout {
    @XmlElement(name = "ViewMode")
    var viewMode: String? = null

    @XmlElement(name = "ViewSize@value")
    var viewSize: Int? = null

    @XmlAnyElement(lax = true)
    var objects: List<Element> = mutableListOf()
}

class AlteryxXmlNodeStyle {
    @field:XmlElement(name = "TextColor")
    var textColor: String? = null

    @field:XmlElement(name = "FillColor")
    var fillColor: String? = null

    @field:XmlElement(name = "BorderColor")
    var borderColor: String? = null

    @field:XmlElement(name = "Transparency")
    var transparency: Int = 25

    @field:XmlElement(name = "Margin")
    var margin: Int = 25
}

class AlteryxXmlWorkflowFactory(val cf: AlteryxConfigurationMapper) {


    val jaxbContext = JAXBContextFactory.createContext(
        arrayOf(AlteryxXmlWorkflow::class.java, ObjectFactory::class.java),
        mutableMapOf<String, Any>()
    )
    val jaxbSelectContext = JAXBContextFactory.createContext(
        arrayOf(AlteryxXmlNodeSelectConfiguration::class.java, ObjectFactory::class.java),
        mutableMapOf<String, Any>()
    )
    val jaxbToolConfigContext = JAXBContextFactory.createContext(
        arrayOf(AlteryxXmlNodeConfiguration::class.java, ObjectFactory::class.java),
        mutableMapOf<String, Any>()
    )
    val jaxbToolContext = JAXBContextFactory.createContext(
        arrayOf(AlteryxXmlNode::class.java, ObjectFactory::class.java),
        mutableMapOf<String, Any>()
    )

    val unmarshaller = jaxbContext.createUnmarshaller()
    val marshaller = jaxbContext.createMarshaller()
    val selectUnmarshaller = jaxbSelectContext.createUnmarshaller()
    val toolConfigMarshaller = jaxbToolConfigContext.createMarshaller()
    val toolConfigUnmarshaller = jaxbToolConfigContext.createUnmarshaller()
    val toolMarshaller = jaxbToolContext.createMarshaller()

    fun printToString(wf: AlteryxXmlWorkflow): String {
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        val sw = StringWriter()
        marshaller.marshal(wf, sw)
        return sw.toString()
    }

    fun printToString(toolConfig: AlteryxXmlNodeConfiguration): String {
        toolConfigMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        val sw = StringWriter()
        toolConfigMarshaller.marshal(toolConfig, sw)
        return sw.toString()
    }

    fun printToString(tool: AlteryxXmlNode): String {
        toolMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        val sw = StringWriter()
        toolMarshaller.marshal(tool, sw)
        return sw.toString()
    }

    fun readConfigFromString(s: String) : AlteryxXmlNodeConfiguration {
        return toolConfigUnmarshaller.unmarshal(StringReader(s)) as AlteryxXmlNodeConfiguration
    }

}
