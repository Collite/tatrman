package com.alteryx.kyx

import kotlin.test.Test

class TestKyx {

    @Test
    fun testKyx1() {
//start building a workflow
        val wf = workflow {

//one can assign the tools to variables
            val s = Input {
                Source = "SNOWFLAKE::[My Connection]::[my demo database].mySchema.[Table of Bora]"
            }

            val f = Filter {
                Expression = "amount > 0"
            }

// '+' connects the tools using default Output -> Input anchors
            val branch1 = s + f

// you can '+' the tools directly, no need for variables
            val branch2 =

                Input {
                    Source = "C:\\Temp\\bora.csv"
                } +

                Select {
                    Columns ("customer", "branch")
                    Column {
                        name = "amount"
                        fieldType = "double"
                    } As "new column name"
                } +

                Filter {
                    Expression = "[customer] == 2323"

                }


// join the two branches and summarize

            Join(branch1, branch2) {
                On("customer", "customer")        //column names to join on
                And("branch", "branch_code")

            } +                                        // for Join the default outgoing anchor is the inner join
            Summarize {
                        Sum("amount") As "Sum_Amt"
                        Avg("amount") As "Avg_Amt"
                        GroupBy("customer", "year")
            } +
            Browse

//add some error handling
            f.False +                                 // you can connect to specific anchor for Filter and Join
                    Output {
                        Destination = "C:\\Temp\\errors.csv"
                    }
        }

        println(wf.toXmlString())                    // builds the xml


    }

}