package cz.ruleng.rae.generation.generator

RuleBuilder ruleBuilder = ruleBuilderWrapper.ruleBuilder

ruleBuilder.createRule{

    SourceData2 = cut(allocation___Organizational, [Allocation___Organizational])
    SourceData = cut(allocation___Organizational, [Allocation___Organizational])  // gdsl funguje len ako camelCase
    DriverFC = cut(multi_Driver, [FTEEq])
    DriverAggr = aggr(DriverFC,  [ CP_Code , RC_Code ] )
    SourceData = createValue(SourceData, "Rule_Id", {1428})
    SourceData = createValue(SourceData, "Rule_Dir", {1})
    SourceData = createValue(SourceData, "Batch_Id", {10002})
    SourceData = createValue(SourceData, "Driver_Value", {0})
    DriverF = filter(multi_Driver, {CP_Type.eq("test")})
    append(Allocation___Organizational, SourceData)


    println(DriverF.toString())
}