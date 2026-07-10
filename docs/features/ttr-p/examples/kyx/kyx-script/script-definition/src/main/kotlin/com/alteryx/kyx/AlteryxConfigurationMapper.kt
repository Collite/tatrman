package com.alteryx.kyx

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.namespace.QName
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class AlteryxConfigurationMapper {
    val readers = mapOf<String, (AlteryxXmlNodeConfiguration?) -> Properties>(
        "AlteryxDbFileInput" to this::dbFileInputConfigReader,
        "AlteryxDbFileOutput" to this::dbFileOutputConfigReader,
        "AlteryxJoin" to this::joinConfigReader,
        "AlteryxSummarize" to this::summarizeConfigReader,
        "AlteryxFilter" to this::filterConfigReader,
        "AlteryxSort" to this::sortConfigReader,
        "AlteryxSelect" to this::selectConfigReader,
        "AlteryxFormula" to this::formulaConfigReader,
        "AlteryxUnion" to this::unionConfigReader,
        "AlteryxBrowseV2" to this::browseConfigReader
    )
    val writers = mapOf<String, (Properties) -> AlteryxXmlNodeConfiguration?>(
        "AlteryxDbFileInput" to this::dbFileInputConfigWriter,
        "AlteryxDbFileOutput" to this::dbFileOutputConfigWriter,
        "AlteryxJoin" to this::joinConfigWriter,
        "AlteryxSummarize" to this::summarizeConfigWriter,
        "AlteryxFilter" to this::filterConfigWriter,
        "AlteryxSort" to this::sortConfigWriter,
        "AlteryxSelect" to this::selectConfigWriter,
        "AlteryxFormula" to this::formulaConfigWriter,
        "AlteryxUnion" to this::unionConfigWriter,
        "AlteryxBrowseV2" to this::browseConfigWriter,
        "Input" to this::dbFileInputConfigWriter,
        "Output" to this::dbFileOutputConfigWriter,
        "Join" to this::joinConfigWriter,
        "Summarize" to this::summarizeConfigWriter,
        "Filter" to this::filterConfigWriter,
        "Sort" to this::sortConfigWriter,
        "Select" to this::selectConfigWriter,
        "Formula" to this::formulaConfigWriter,
        "Union" to this::unionConfigWriter,
        "Browse" to this::browseConfigWriter
    )

    val xp = XPathFactory.newInstance().newXPath()


    //--------------------------------------------------------------------------------------------------
    //                                               DbFileInput
    //--------------------------------------------------------------------------------------------------

    fun dbFileInputConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Input"

        if (element == null)
            return properties

        properties["password"] = element.password
        properties["fileFormat"] = element.file!!.fileFormat
        properties["file"] = element.file!!.dataSource

        properties["dataSource"] = getDatasource(properties["file"] as String)
        properties["table"] = getTable(properties["file"] as String)
        properties["query"] = getQuery(properties["file"] as String)

        //ToDo after seeding, check the columns?
        //There is a lot to do with the catalog vs the workflow

        return properties
    }

    fun dbFileInputConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {

        assert(properties["kind"] == "Input")
        val config = AlteryxXmlNodeConfiguration()

        when (((properties["type"] ?: "") as String).lowercase()) {
            "file" -> {
                val fileProperties = (properties["reference"] as PropertyAnnotationBase?)?.properties ?: Properties()
                val fileType = getFileType(fileProperties)
                val fsOptions = when (fileType) {
                    SupportedFileTypes.EXCEL -> AlteryxXmlNodeFormatSpecific.xlsDefaults()
                    SupportedFileTypes.CSV -> AlteryxXmlNodeFormatSpecific.csvDefaults()
                    else -> null
                }
                config.file = AlteryxXmlNodeDataSource()
                config.file!!.fileFormat = fileType?.value ?: 0
                config.file!!.dataSource = (fileProperties["value"] ?: "") as String
                config.formatSpecificOptions = fsOptions
            }

            "db" -> {
                val fsOptions = AlteryxXmlNodeFormatSpecific.sqlDefaults()
                config.file = AlteryxXmlNodeDataSource()
                config.file!!.fileFormat = 23 //ODBC
                config.file!!.dataSource = constructDb(properties)
                config.formatSpecificOptions = fsOptions
            }
        }

        config.password = properties["password"] as String?

        return config
    }

    private fun constructDb(properties: Properties) : String {
//todo this needs to be much better; + ||| etc
        return (properties["datasource"] as? String ?: "") + ":" + (properties["database"] as? String ?: "") + "." + (properties["table"] as? String ?: "unknown_table")
    }
    //--------------------------------------------------------------------------------------------------
    //                                               DbFileOutput
    //--------------------------------------------------------------------------------------------------

    fun dbFileOutputConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Output"

        if (element == null)
            return properties

        properties["password"] = element.password
        properties["fileFormat"] = element.file!!.fileFormat
        properties["file"] = element.file!!.dataSource

        properties["dataSource"] = getDatasource(properties["file"] as String)
        properties["table"] = getTable(properties["file"] as String)
        properties["query"] = getQuery(properties["file"] as String)

        //ToDo format specific options
        //ToDo after seeding, check the columns?
        //There is a lot to do with the catalog vs the workflow

        return properties
    }

    fun dbFileOutputConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {
        val element = AlteryxXmlNodeConfiguration()

        assert(properties["kind"] == "Output")

        element.password = properties["password"] as String?
        element.file = AlteryxXmlNodeDataSource()
        element.file!!.fileFormat = (properties["fileFormat"] ?: 0) as Int //ToDo tohle as neumim jeste plnit, rekl bych
        element.file!!.dataSource =
            ((properties["dataSource"] ?: "") as String) + "|||" +
                    ((properties["table"] ?: "") as String) + ((properties["query"] ?: "") as String)

        return element

    }


    //--------------------------------------------------------------------------------------------------
    //                                               Join
    //--------------------------------------------------------------------------------------------------

    fun joinConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {
        val properties = Properties()

        properties["kind"] = "Join"

        if (element == null)
            return properties

        val leftFields = element.joinInfo!!.find { it.connectionName.equals("Left", true) }?.fieldList
        val rightFields = element.joinInfo!!.find { it.connectionName.equals("Right", true) }?.fieldList

        val joinedFields = mutableListOf<MutableMap<String, String?>>()

//ToDo what if not equal?
        for (i in 0 until leftFields!!.size) {
            joinedFields.add(
                mutableMapOf<String, String?>(
                    "Left" to leftFields[i].field,
                    "Right" to rightFields!![i].field
                )
            )
        }
        properties["joins"] = joinedFields

        element.joinSelectConfiguration?.configurations?.forEach {
            properties["record${it.connection}"] = getRecordMap(it.selRecordInfo)
        }

        properties["record"] = properties.getOrDefault("recordJoin", mutableListOf<Any?>())

        return properties
    }

    fun joinConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {
        val element = AlteryxXmlNodeConfiguration()

        assert(properties["kind"] == ("Join"))

        val leftFields = mutableListOf<AlteryxXmlNodeRecordInfoField>()
        val rightFields = mutableListOf<AlteryxXmlNodeRecordInfoField>()

        (properties["joins"] as List<Map<String, String>>).forEach {
            val left = AlteryxXmlNodeRecordInfoField()
            left.field = it.getOrDefault("Left", "Error")
            val right = AlteryxXmlNodeRecordInfoField()
            right.field = it.getOrDefault("Right", "Error")

            leftFields.add(left)
            rightFields.add(right)
        }

        val selectConfigs = AlteryxXmlNodeSelectConfigurations()

        if (properties.containsKey("recordLeft")) {
            val config = AlteryxXmlNodeSelectConfiguration.getDefaultsWithUnknown(NodeTypes.SELECT)
            config.connection = "Left"
            config.selRecordInfo!!.addAll(
                selectFields(
                    properties["recordLeft"] as List<Properties>,
                    setOf(SelectFieldsFlags.SOURCE, SelectFieldsFlags.ALIAS)
                )
            )
            selectConfigs.configurations.add(config)
        }
        if (properties.containsKey("recordRight")) {
            val config = AlteryxXmlNodeSelectConfiguration()
            config.connection = "Right"
            config.selRecordInfo!!.addAll(
                selectFields(
                    properties["recordRight"] as List<Properties>,
                    setOf(SelectFieldsFlags.SOURCE, SelectFieldsFlags.ALIAS)
                )
            )
            selectConfigs.configurations.add(config)
        }
        if (properties.containsKey("recordJoin")) {
            val config = AlteryxXmlNodeSelectConfiguration()
            config.connection = "Join"
            config.selRecordInfo!!.addAll(
                selectFields(
                    properties["recordJoin"] as List<Properties>,
                    setOf(SelectFieldsFlags.SOURCE, SelectFieldsFlags.ALIAS)
                )
            )
            selectConfigs.configurations.add(config)
        }

        element.joinSelectConfiguration = selectConfigs
        element.joinInfo = mutableListOf()

        val leftJoinInfo = AlteryxXmlNodeJoinInfo()
        leftJoinInfo.connectionName = "Left"
        leftJoinInfo.fieldList = leftFields
        val rightJoinInfo = AlteryxXmlNodeJoinInfo()
        rightJoinInfo.connectionName = "Right"
        rightJoinInfo.fieldList = rightFields

        element!!.joinInfo!!.add(leftJoinInfo)
        element!!.joinInfo!!.add(rightJoinInfo)

        return element
    }


    //--------------------------------------------------------------------------------------------------
    //                                               Select
    //--------------------------------------------------------------------------------------------------

    fun selectConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {
        val properties = Properties()
        properties["kind"] = "Select"

        if (element == null)
            return properties

        properties["record"] = getRecordMap(element.selRecordInfo!!)

        return properties
    }

    fun selectConfigWriter(
        properties: Properties,
        flags: Set<SelectFieldsFlags>
    ): AlteryxXmlNodeConfiguration {

        assert(properties["kind"] == ("Select"))

        val element = if (flags.contains(SelectFieldsFlags.UNKNOWN))
            AlteryxXmlNodeConfiguration.getDefaultsWithUnknown(NodeTypes.SELECT)
        else
            AlteryxXmlNodeConfiguration.getDefaults(NodeTypes.SELECT)

        element.selRecordInfo!!.addAll(selectFields(properties["record"] as List<Properties>, flags))

        return element

    }

    fun selectConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {
        return selectConfigWriter(
            properties,
            setOf(SelectFieldsFlags.SELECTED, SelectFieldsFlags.ALIAS)
        )
    }

//    fun retypeConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {
//        return selectConfigWriter(
//            retypeToSelect(properties),
//            setOf(SelectFieldsFlags.UNKNOWN, SelectFieldsFlags.RETYPE, SelectFieldsFlags.SELECTED)
//        )
//    }
//
//    fun renameConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {
//        return selectConfigWriter(
//            renameToSelect(properties),
//            setOf(SelectFieldsFlags.UNKNOWN, SelectFieldsFlags.ALIAS, SelectFieldsFlags.SELECTED)
//        )
//    }

    //--------------------------------------------------------------------------------------------------
    //                                               Formula
    //--------------------------------------------------------------------------------------------------


    fun formulaConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Formula"

        if (element == null)
            return properties

        properties["formulas"] = getRecordMap(element.forRecordInfo!!)

        return properties
    }

    fun formulaConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {

        val element = AlteryxXmlNodeConfiguration.getDefaults(NodeTypes.FORMULA)
        assert(properties["kind"] == ("Formula"))

        val formulas =
            properties["formulas"] as? List<Properties>
                ?: ((properties["formulas"] as PropertyAnnotationBase)   //ExprList
                    .properties["operands"] as List<PropertyAnnotationBase>)
                    .map {
                        Properties(
                            mutableMapOf(
                                "alias" to it.properties["alias"],
                                "expression" to printExpression(it),
                                "type" to it.properties["type"]  //todo sizes
                            )
                        )
                    }


        element.forRecordInfo!!.addAll(
            selectFields(formulas, setOf(SelectFieldsFlags.EXPRESSION))
        )

        return element

    }
    //--------------------------------------------------------------------------------------------------
    //                                               Summarize
    //--------------------------------------------------------------------------------------------------

    fun summarizeConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Summarize"

        if (element == null)
            return properties

        val actions = getRecordMap(element.sumRecordInfo!!)

        val groupByList = mutableListOf<Properties>()
        val aggrList = mutableListOf<Properties>()

        actions.forEach {
            if ((it["action"] as String) == "GroupBy")
                groupByList.add(it)
            else {
                it["expression"] = "${it["action"]} ( ${it["name"]} )"
                aggrList.add(it)
            }
        }

        properties["groupBy"] = groupByList
        properties["aggr"] = aggrList

        return properties
    }

    fun summarizeConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {

        assert(properties["kind"] == ("Summarize"))

        val element = AlteryxXmlNodeConfiguration.getDefaults(NodeTypes.SUMMARIZE)

        unwrapSummarize(properties, mutableListOf()) //todo pass the issues ...

        element.sumRecordInfo!!.addAll(
            selectFields(
                properties["record"] as List<Properties>?,
                setOf(SelectFieldsFlags.ACTION, SelectFieldsFlags.ALIAS)
            )
        )

        return element
    }
    //--------------------------------------------------------------------------------------------------
    //                                               Filter
    //--------------------------------------------------------------------------------------------------

    fun filterConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Filter"

        if (element == null)
            return properties

        when (element.mode?.lowercase()) {
//            "simple" -> properties["alteryxExpression"] = getExpressionFromSimpleFilter(element.simpleFilter!!)
            "custom" -> properties["alteryxExpression"] = unescape(element.filterExpression!!)
            //Todo the simple needs the type of the field, so for the time being, i leave it to the annotation

        }

        return properties
    }

    fun getExpressionFromSimpleFilter(sf: AlteryxXmlNodeSimpleFilter): String {
        //Todo a lot of things
        //I need the field data type to properly interpret (and event quote) operand
        //more operators and all the f-ing date options
        return unescape(
            "[${sf.field}] ${sf.operator} ${sf.operands?.operand}"
        )
    }


    fun filterConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {

        val element = AlteryxXmlNodeConfiguration()
        assert(properties["kind"] == ("Filter"))

        //Todo this creates always a custom filter. Maybe (maybe!!) we might want to create also simple ones, one day

//        element.filterExpression = escape(properties["alteryxExpression"] as String)
        element.filterExpression = escape(properties["conditionString"] as String)
        element.mode = "custom"

        return element
    }

    fun unescape(s: String) = s
    fun escape(s: String) = s


    //--------------------------------------------------------------------------------------------------
    //                                               Sort
    //--------------------------------------------------------------------------------------------------


    fun sortConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Sort"

        if (element == null)
            return properties

        properties["sort"] = getRecordMap(element.sortRecordInfo!!)

        return properties
    }

    fun sortConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {

        val element = AlteryxXmlNodeConfiguration()
        assert(properties["kind"] == ("Sort"))

        element.sortRecordInfo = selectFields(properties["sort"] as List<Properties>, setOf(SelectFieldsFlags.ORDER))

        return element

    }
    //--------------------------------------------------------------------------------------------------
    //                                               Union
    //--------------------------------------------------------------------------------------------------

    fun unionConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Union"

        if (element == null)
            return properties

        properties["mode"] = element.mode
        properties["byName_errorMode"] = element.unionByNameErrorMode
        properties["byName_outputMode"] = element.unionByNameOutputMode
        properties["setOutputOrder"] = element.unionSetOutputOrder

        return properties

    }

    fun unionConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {

        val element = AlteryxXmlNodeConfiguration()
        assert(properties["kind"] == ("Union"))


        element.mode = properties["mode"] as String
        element.unionByNameErrorMode = properties["byName_errorMode"] as String?
        element.unionByNameOutputMode = properties["byName_outputMode"] as String?
        element.unionSetOutputOrder = BooleanValue((properties["setOutputOrder"] as Boolean?) ?: false)

        return element

    }

    //--------------------------------------------------------------------------------------------------
    //                                               Browse
    //--------------------------------------------------------------------------------------------------

    fun browseConfigReader(element: AlteryxXmlNodeConfiguration?): Properties {

        val properties = Properties()
        properties["kind"] = "Browse"

        if (element == null)
            return properties

        properties["viewMode"] = element.browseLayout?.viewMode

        return properties
    }

    fun browseConfigWriter(properties: Properties): AlteryxXmlNodeConfiguration {

        val element = AlteryxXmlNodeConfiguration()
        assert(properties["kind"] == ("Browse"))

        element.browseTempFile = "C:\\TEMP\\b.yxdb"
        element.browseLayout = AlteryxXmlNodeBrowseLayout()
        element.browseLayout!!.viewMode = ((properties["viewMode"] as String?) ?: "Single")

        return element

    }

    //--------------------------------------------------------------------------------------------------
    //                                               Helpers
    //--------------------------------------------------------------------------------------------------
    fun setProperty(
        properties: Properties,
        element: Element?,
        name: String,
        xPath: String,
        valueType: QName
    ) {
        val propValue = xp.evaluate(xPath, element, valueType)
        if (propValue != null && propValue.toString().length > 0)
            properties[name] = propValue
    }

    fun setElement(properties: Properties, element: Element?, name: String, xPath: String, valueType: QName) {
        val node = xp.evaluate(xPath, element, XPathConstants.NODE) as Node
        node.nodeValue = properties[name].toString()

    }

    fun getDatasource(s: String): String {
        //ToDo o dost lepe :)
        return s.substringBefore("|||")
    }

    fun getTable(s: String): String {
        val after = s.substringAfter("|||").trim()
        return if (after.startsWith("select", true)) "" else after
    }

    fun getQuery(s: String): String {
        val after = s.substringAfter("|||").trim()
        return if (after.startsWith("select", true)) after else ""
    }


    fun getRecordMap(recInfo: List<AlteryxXmlNodeRecordInfoField>?): List<Properties> {

        return recInfo?.filter { field -> field.field != "*Unknown" }?.map { field ->
            val p = Properties()
            p.putAll(
                mapOf(
                    "name" to if ((field.name?.length ?: 0) > 0) field.name else if ((field.input?.length
                            ?: 0) > 0
                    ) field.field!!.substringAfter(
                        field!!.input!!
                    ) else field.field,
                    "source" to if ((field.input?.length ?: 0) > 0) field.input else field.source,
                    "input" to field.input,
                    "selected" to field.selected,
                    "type" to (field.fieldType ?: ""),  //see below
                    "size" to (field.size ?: 0),
                    "scale" to (field.scale ?: 0),
                    "alias" to (field.rename ?: ""),  //ToDo rather avoid the nulls, probably
                    "action" to field.action,
                    "order" to field.order,
                    "expression" to field.expression
                )
            )
            p
        }?.toList() ?: listOf()
    }


    enum class SelectFieldsFlags {
        UNKNOWN, ALIAS, TYPE, RETYPE, SELECTED, ACTION, ORDER, SOURCE, EXPRESSION, INPUT
    }

    fun selectFields(p: List<Properties>?, flags: Set<SelectFieldsFlags>): MutableList<AlteryxXmlNodeRecordInfoField> {
        return p?.map {
            val node = AlteryxXmlNodeRecordInfoField()

            if (it.containsKey("name") && null != it["name"])
                node.field = it["name"] as String

            if (flags.contains(SelectFieldsFlags.SOURCE) &&
                it.containsKey("source") && null != it["source"]
            )
                node.source = it["source"] as String

            if (it.containsKey("input") && null != it["input"])
                node.input = it["input"] as String

            if (flags.contains(SelectFieldsFlags.SELECTED) &&
                (!it.containsKey("selected") || it.containsKey("selected") && null != it["selected"])
            )
                node.selected = (it["selected"] as Boolean?) ?: true

            if (flags.contains(SelectFieldsFlags.TYPE)) {
                if (it.containsKey("type") && null != it["type"]) {
                    node.fieldType = (it["type"] as DataType).toString()
                }
                if (it.containsKey("size") && null != it["size"])
                    node.size = it["size"] as Int
                if (it.containsKey("scale") && null != it["scale"])
                    node.scale = it["scale"] as Int
            }

            if (flags.contains(SelectFieldsFlags.RETYPE)) {
                if (it.containsKey("newType") && null != it["newType"])
                    node.fieldType = (it["newType"] as DataType).toString()
                if (it.containsKey("newSize") && null != it["newSize"])
                    node.size = it["newSize"] as Int
                if (it.containsKey("newScale") && null != it["newScale"])
                    node.scale = it["newScale"] as Int
            }


            if (flags.contains(SelectFieldsFlags.ALIAS) &&
                it.containsKey("alias") && null != it["alias"]
            )
                node.rename = it["alias"] as String

            if (flags.contains(SelectFieldsFlags.ACTION) &&
                it.containsKey("action") && null != it["action"]
            )
                node.action = it["action"] as String

            if (flags.contains(SelectFieldsFlags.ORDER) &&
                it.containsKey("order") && null != it["order"]
            )
                node.order = it["order"] as String

            if (flags.contains(SelectFieldsFlags.EXPRESSION) &&
                it.containsKey("expression") && null != it["expression"]
            ) {
                node.expression = it["expression"] as String
                node.field = it["alias"] as String
                node.fieldType = DataType.toAlteryxString(it["type"] as DataType)
                //todo data type!!!
            }

            node
        }?.toMutableList() ?: mutableListOf()
    }
}