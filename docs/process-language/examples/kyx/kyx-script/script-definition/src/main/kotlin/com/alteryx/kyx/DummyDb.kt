package com.alteryx.kyx

object DummyDb {

    private val _dummyTables: MutableMap<String, Metadata> = mutableMapOf()

    init {
        val jsoncustomer = Workflow::class.java.getResource("/objects/dummy_table_customer.json")!!.readText()
        _dummyTables["CUSTOMER"] = Metadata(jsoncustomer)
        val jsonproduct = Workflow::class.java.getResource("/objects/dummy_table_product.json")!!.readText()
        _dummyTables["PRODUCT"] = Metadata(jsonproduct)
        val jsonbranch = Workflow::class.java.getResource("/objects/dummy_table_branch.json")!!.readText()
        _dummyTables["BRANCH"] = Metadata(jsonbranch)
        val jsonaccounts = Workflow::class.java.getResource("/objects/dummy_table_accounts.json")!!.readText()
        _dummyTables["ACCOUNTS"] = Metadata(jsonaccounts)
        val jsonsales = Workflow::class.java.getResource("/objects/dummy_table_sales.json")!!.readText()
        _dummyTables["SALES"] = Metadata(jsonsales)
        val jsontransactions = Workflow::class.java.getResource("/objects/dummy_table_transactions.json")!!.readText()
        _dummyTables["TRANSACTIONS"] = Metadata(jsontransactions)
        val jsoncolumns = Workflow::class.java.getResource("/objects/dummy_columns.json")!!.readText()
        _dummyTables["COLUMNS"] = Metadata(jsoncolumns)

    }


    fun getMetadata(source: String): Metadata {
        return ((when {
            source.lowercase().contains("cust") -> _dummyTables["CUSTOMER"]
            source.lowercase().contains("prod") -> _dummyTables["PRODUCT"]
            source.lowercase().contains("bra") -> _dummyTables["BRANCH"]
            source.lowercase().contains("acc") -> _dummyTables["ACCOUNTS"]
            source.lowercase().contains("sal") -> _dummyTables["SALES"]
            source.lowercase().contains("tran") -> _dummyTables["TRANSACTIONS"]
            source.lowercase().contains("trx") -> _dummyTables["TRANSACTIONS"]

            else -> _dummyTables["COLUMNS"]
        } ?: _dummyTables["COLUMNS"]!!))
    }


}


