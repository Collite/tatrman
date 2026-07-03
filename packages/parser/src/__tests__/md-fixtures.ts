// Shared MD (multidimensional) `.ttrm` fixtures for the Phase 1 grammar/walker
// tests. Stage 1B asserts these PARSE (no errors); 1C/1D assert their AST shape.
// One snippet per construct, drawn from design.md §5–§6, the map-catalog §3
// authoring shape, and contracts §2.

/** A full logical model (model md): domains, a dimension, maps, hierarchy, measures, cubelets. */
export const MD_LOGICAL = `model md

def domain Timestamp { type: timestamp }
def domain Day       { type: date }
def domain Month     { type: int, kind: calc, restrict: { range: 1..12 } }
def domain Quarter   { type: int, kind: calc, restrict: { range: 1..4 } }
def domain Year      { type: int, kind: calc }
def domain FiscalYear { type: int, kind: calc }
def domain CustomerCode { type: string }
def domain CustomerName { type: string }
def domain ProductCode  { type: string }
def domain CostCenterCode { type: string }
def domain Money { type: decimal }
def domain AccountKind {
  type: string,
  kind: bound,
  restrict: {
    members: {
      "A": { en: "Asset", cs: "Aktivum" },
      "L": { en: "Liability" }
    }
  }
}

def dimension Customer {
  key: code,
  attributes: [
    def attribute code { domain: md.CustomerCode, isKey: true },
    def attribute name { domain: md.CustomerName, aggregation: latestValid }
  ],
  hierarchies: [geo]
}

def dimension Time {
  key: day,
  attributes: [
    def attribute day     { domain: md.Day },
    def attribute month   { domain: md.Month },
    def attribute quarter { domain: md.Quarter },
    def attribute year    { domain: md.Year }
  ],
  hierarchies: [calendar]
}

// truncation / extraction / roll-up maps (calc)
def map ts_to_day    { from: md.Timestamp, to: md.Day,     calc: truncToDay }
def map day_to_month { from: md.Day,       to: md.Month,   calc: monthOfDate }
def map month_to_qtr { from: md.Month,     to: md.Quarter, calc: quarterOfMonth }
def map qtr_to_year  { from: md.Quarter,   to: md.Year,    calc: yearOfDate }

// parameterised calc (named args)
def map day_to_fy { from: md.Day, to: md.FiscalYear, calc: fiscalYearOfDate(fiscalYearStartMonth: 4) }

// table-backed map (no calc) with explicit cardinality and a multi-domain from
def map cc_to_building {
  from: md.CostCenterCode,
  to: md.CustomerCode,
  cardinality: { from: "N", to: "1" }
}

def hierarchy calendar {
  dimension: md.Time,
  levels: [day, month via md.day_to_month, quarter via md.month_to_qtr, year]
}

def measure net { domain: md.Money, class: additive, aggregation: sum }
def measure balance {
  domain: md.Money,
  class: semiAdditive,
  aggregation: { default: sum, time: latestValid },
  validBy: day
}

def cubelet sales {
  grain: [Customer.code, Time.day],
  measures: [net, balance]
}

def cubelet costs {
  grain: [Customer.code, Time.month],
  measures: [
    def measure amount { domain: md.Money, aggregation: sum }
  ]
}
`;

/** Binding model (model binding): the four md2* binding kinds, wide + long shapes. */
export const MD_BINDING = `model binding

def md2db_cubelet sales_fact {
  cubelet: md.sales,
  target: db.dbo.SALES_FACT,
  shape: wide,
  attributes: {
    Customer.code: CUST_CODE,
    Time.day: TXN_DATE,
    CostCenter.code: { via: md.cc_to_building, from: { table: db.dbo.CC_MAP, column: BUILDING } }
  },
  measures: { net: NET_AMT, balance: BAL_AMT },
  journaling: overwrite
}

def md2db_cubelet drivers_fact {
  cubelet: md.otherDrivers,
  target: db.dbo.DRIVERS,
  shape: { long: { codeColumn: DRIVER_CODE, valueColumn: AMOUNT } },
  attributes: { CostCenter.code: CC_CODE, Time.month: PERIOD },
  measures: { amount: { code: AMT } },
  journaling: { invalidate: { validColumn: VALID_TO } }
}

def md2db_domain account_kind_src {
  domain: md.AccountKind,
  source: { table: db.dbo.ACCOUNTS, column: KIND }
}

def md2db_map cc_building_map {
  map: md.cc_to_building,
  target: db.dbo.CC_BUILDING,
  columns: { CostCenterCode: CC_COL, CustomerCode: BLDG_COL }
}

def md2er_cubelet sales_er {
  cubelet: md.sales,
  target: er.entity.Sale,
  attributes: { Customer.code: customerCode, Time.day: saleDate }
}
`;

/**
 * Negative/idPart coverage: the new MD keywords (`domain`, `measure`, `cubelet`,
 * `map`, `kind`, …) must still work as cross-reference fragments and bare ids in
 * non-MD schemas, so the 3.1 bump stays additive.
 */
export const MD_IDPART = `model db schema dbo

def table EVENTS {
  columns: [
    def column measure { type: int },
    def column domain { type: varchar },
    def column cubelet { type: varchar },
    def column kind { type: int }
  ]
}

def view V {
  columns: [def column c { type: int }],
  definitionSql: "SELECT m FROM db.dbo.measure"
}
`;
