package cz.ruleng.rae.generation.generator

RuleBuilder ruleBuilder = ruleBuilderWrapper.ruleBuilder
ruleBuilder.createRule{



	// agregujem podla attributu, ktory nie je vo tableObject - primapovanie
	AggrTest2 = aggr(MapTestValueSet, [TargetAttr])  // priame priradenie	


	
	// logic expression ako strom
	SourceData =  filter(Allocorg, {(Fteeq.eq(10)).or(Fteeq.lt(5))})
	// logic expression ako string 
	SourceData2 = filter(Allocorg, "Fteeq = 10 OR Fteeq < 5")
	
	
	SourceDataC = cut(SourceData, [Fteeq])
    SourceDataC = createValue(SourceDataC, "RuleId" , {"1518"}) // constant value expression


    Driver2 = createValue(Driver, "Sum", {CP_Code + 5}) // inline value expression

    ModelTest2 = aggr(Allocorg, [CP_Code])
	
//	Driver2.addValueSetAppend(SourceDataC) // addValueSetAppend cez funkciu
//	Driver3 +=	 SourceDataC		// addValueSetAppend priamo
	
	
	
	println()
	println AggrTest2.toString()
	println SourceData.toString()
	println SourceData2.toString()
	println SourceDataC.toString()
	println Driver.toString()
    println ModelTest2.toString()

}
