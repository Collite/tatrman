package cz.ruleng.rae.generation.generator

RuleBuilder ruleBuilder = ruleBuilderWrapper.ruleBuilder
ruleBuilder.createRule{

    Closure expr = {TargetAttr.eq(2013)}

    ModelTest2 = aggr(Allocation___Organizational, [CP_Code])

    Filter1 = filter(Allocorg, {(Fteeq+10).eq(DrvInv - 5)})
    Filter2 = filter(Allocorg, {(Fteeq+10).eq("test")})
	SourceData = filter(Allocorg, {(Fteeq.eq(10)).or(Fteeq.lt(DrvInv - 5))})
	//SourceDataC = cut(MULTI_DRIVER, [CP_Code])
	SourceDataC = cut(SourceData, [DrvInv])
    SourceDataC = createValue(SourceDataC, "ComputedValue1", {1520})
    SourceDataC = createValue(SourceDataC, "ComputedValue2", {ComputedValue1 + 5})
    SourceDataC = createValue(SourceDataC, "CurrentDate", {now()})
    SourceDataC = createValue(SourceDataC, "Lowercase", {lcase(DrvInv)})
    SourceDataC = createValue(SourceDataC, "Lowercase", {lcase(DrvInv)})    // zdvojenie column - vytvori sa nepomenovany stlpec


	MapTest2 = aggr(MapTestValueSet, [TargetAttr])

    MapTest3 = filter(MapTestValueSet, {TargetAttr.eq(2013)})    // filter with mapped attribute
    MapTest4 = filter(MapTestValueSet, expr)                     // filter  with outside expression



    materialize(MapTest2)
    append(Split___Periods, MapTest2)
	
	

	println SourceDataC.toString()
	println MapTest2.toString()
    println Split___Periods.toString()

	println()
	
}
