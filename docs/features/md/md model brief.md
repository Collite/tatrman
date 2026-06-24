# Multidimensional Model - A Brief

This document is a brief for the new feature of the modeler: Multidimensional model

## Current Situation

We have the ability, in the modeler, to specify the physical relational model (tables, columns, keys) and the E-R model (entities, relationships, attributes). We also provide the mechanism to specify the mapping from the E-R model to the physical model.
We want now to extend the modeler with the ability to specify also the multidimensional model, and its mapping to the physical model.

## Two "multidimensional model" meanings
The phrase "multidimensional model" will have two meanings - and we will need to distinguish them:
1. The "physical" MD model as provided by MD BI apps like PowerBI / MS SSAS etc (MOLAP model)
2. The "logical" MD model backed by the relational database schema, i.e. a ROLAP model

For the rest of this document we will mostly speak about the logical (ROLAP) model, but it needs to be clear that the MOLAP model(s) will come.

## Basic elements

For the logical multidimensional model we will use the "multidimensional" terminology with a few specifics:
- Domain - a set of values for a given attribute or measure. Will have a type, for sure, but can have other properties and constraints as well
- Dimension - a theoretical concept in the multidimensional world. Things like "Time", "Customer", "Product"
- Attribute (dimensional attribute) - a concept with a name and a domain - set of values, belonging to the dimension. Product Code, Week, etc
  - Hierarchy - ordered set of attributes in a given dimension with a (parent - child) relationship. Dimension Time will have attributes Year, Quarter, Month, Week, Day, Hour, Minute, Second; and they make two hierarchies (one with Month + Quarter, one with Weeks). The attributes in a hierarchy can be called Levels for that hierarchy. A hierarchy should have a name.
- Attribute Mapping - the way how to get from one attribute to the other. Can be 1:N (parent / child), 1:1; M:N should be avoided. Can be backed by a table (list of cases) or calculation (like in the Time case). See below "What are maps"
- Measure - a value, depending on the attributes.
- Cubelet - a small cube of values, defined by a set of attributes and set of measures. Several cubelets can have the same dimensions, but different attributes will specify the different levels of aggregations.

## Mapping (to other models)
Important feature of the whole Modeler is the ability to map the higher (logical) models to the physical (currently relational) model. The same will be true for the MD model:
MD model can be mapped to both physical (DB) model (to tables and columns) and E-R model (entities and attributes). In case of mapping the MD model to the E-R model, the mapping from E-R to DB will complete the whole picture.

One important feature will distinguish our model from a traditional OLAP: we will allow not only **reading** from the physical model, but also **writing** back to it. In order to support this, we will need to enhance the mapping with the "shape" of the underlying table, if necessary.
For the v1, we will support a few basics table "shapes" for the fact tables:
- "wide" - each attribute and measure is a separate column
- "long" - each attribute is a column; then there is a column with a measure code (id, name) and a column with the measure value

We can define cubelets that have multiple sources, and even different shapes of underlying tables. as long as the resulting measures have all the same attributes.

For v1.1 we will also allow the cubelets to be supported by **queries**; in this case, they will either be read-only, or they will have specific SQL scripts handling the "store" operation (while the query is a "load" operation).

## Journaling

We will also support a few "journaling" mechanisms for the table-backed cubelets:
- overwrite - when writing back, overwrite the original value
- invalidate - when writing back, mark the original value as invalid and append a new row to the table with the new value marked as valid (requires specifying the ```valid``` column)
- diff - when writing back, calculate the difference between the original value and the new value and append a new row to the table with the difference

Others will follow

## Usage

While this is a repo to define the **grammar** of the MD model, it is important to understand how the model will be used:
- we will use the "." (dot notation) to specify attributes and measures, and also cubelets:
   - customer.name
   - sales.net
- we will use the dot notation freely to specify both drill-down and filtering
   - sales.2025.january.net
   - kaufland.sales.2025.january.net ("Kaufland" is a name of a customer)
- we will omit the elements that can be derived
   - if we have the "customer_address" table with "zip" column, we can write both "Kaufland.address.zip" and "Kaufland.zip"
- we will use "aggregation defaults" to specify the default aggregation for a measure (or attribute in a hierarchy)
   - sales.2025 - aggregation specified as "sum"
   - kaufland.zip - if the "address" is time-based, with "valid from", we will have the aggregation "max", meaning "give me the latest valid value"
- we will have a DSL to specify operations on cubelets, including the calculations and updates and storing the values (we will use this for example in our future **planning** module / agent)

## RAE
There was an old precedent to this, called RAE - examples for two different DSLs are in the `RAE` subfolder, incl some documentation notes (in Czech). We used Groovy for the DSL (that's why it is named "gscript"); these are just examples for inspiration, not requirements!!!






