import com.alteryx.kyx.As
import com.alteryx.kyx.workflow


workflow {

//one can assign the tools to variables
    val s = Input {
        Source = "SNOWFLAKE::[My Connection]::[my demo database].mySchema.[accounts]"
    }

    val f = Filter {
        Expression = "balance > 0"
    }

// '+' connects the tools using default Output -> Input anchors
    val branch1 = s + f

// you can '+' the tools directly, no need for variables
    val branch2 =

        Input {
            Source = "C:\\Temp\\sales.csv"
        } +

        Select {
            Columns ("customer", "branch", "account")
            Column {
                 field = "sales_volume"
                 fieldType = "double"
            } As "sales"
        } +

        Filter {
            Expression = "[customer] == 2323"

        } +
        Formula {
            "sales * 1.21" As "sales_with_VAT"
        }


// join the two branches and summarize

    Join(branch1, branch2) {
        On("customer", "customer")        //column names to join on
        And("branch", "branch")

    } +                                        // for Join the default outgoing anchor is the inner join
    Summarize {
        Sum("sales") As "Sum_Net"
        Avg("sales_with_VAT") As "Avg_Gross"
        GroupBy("branch")
    } +
    Sort {
        By("branch")
    } +

    Browse

//add some error handling
    f.False +                                 // you can connect to specific anchor for Filter and Join
            Output {
                Destination = "C:\\Temp\\errors.csv"
            }
} .

saveAs("C:\\temp\\myfirstkyxwf.yxmd")




