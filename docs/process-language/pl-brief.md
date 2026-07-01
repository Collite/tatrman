# Processing TTR Language

We will extend the TTR langhuage into new domain - instead of "just a modeling language", we will prepare the processing domain of the TTR.
The TTR processing domain will be actually a set of user-facing "languages", with many syntactic sugars, all parsed into a common TTR processing internal model.

## Execution engines
At the beginning, the TTR processing language will NOT have a dedicated execution engine. We will use transpilers to translate the TTR language into "scripts" in other data processing languages (and their runtimes), namely:\
- Python + pandas
- Python + Polars
- SQL (different dialects and different scripting and procedural extensions)
- Kotlin + Kotlin DataFrames (to be added leter)
We will use the Kantheon's Kyklop workers (see `~/Dev/kantheon` repo, `docs/architecture/kantheon-architecture.md` the "workers" section) for the execution in different runtimes. However, we will need to add the "scripting" part, or, maybe better, an "orchestrator".
The ability to transpile TTR into different languages and use different execution engines is the CORE feature of the TTR. The TTR (both modeling and processing) should be target-agnostic and allow execution in many different environments. \
The Kantheon platform is already supporting different execution engines (Kyklop workers) and Charon is available to move data around. We will test TTR on the Kantheon platform and build a data processing super-engine on it.\

Later on we might develop our own execution engine (maybe Kotlin + Arrow, maybe Rust + Arrow), but not now.

## Examples
Historically, I have made a lot of trials and PoCs with different appraoches to the data procesing language. You will find some of them in the `examples` subfolder:
- RAE: the first attempt, using Groovy as the DSL-basis
- Kyx: other version, built on Kotlin as the DSL-basis\
- Byx: the variant with NL-like commands
Read through them; they are not the designs we want to us (maybe with the exception of Byx), but they show you different angles for different user-facing languages.

## Internal Model
For the internal model, I want to have an execution graph, with operation nodes.
Operation nodes can have at least two different types of connections:
- data-flow connections (the data flows from input to output, similar to tools like Knime, Alteryx, etc.)
- control-flow connections (telling things like "start-to-start" dependency (can run in parallel), "start-to-end" dependency (must run in order), "finish-to-start" dependency (must run serially) etc
Another feature will be error-flow connections (telling things like "if-this-fails-then-this-happens") - these can be both control-flow and data-flow ones.

The operation nodes, at least at the beginning, for the v1, will be essentially the same as Calcite RelNodes - as we want to be able to target SQL, we will use this set of nodes.
Later on, we will be adding more operation nodes, and their "compilation" to the RelNodes. Ultimately, we might get into a situation "this workflow does not run on SQL, it needs different engine" and we will have the capability (and affinity) of engines to nodes

Special operation nodes are "data transfer nodes", moving data around, and "materialization nodes", creating data structures - either for the final output, or for intermediate steps (optimization)

## Optimizer

Not for the v1, but for v2 we want to have an optimizer, which will take the execution graph and optimize it:
- classical optimization (e.g. remove redundant joins, move filters upstream etc)
- materialization optimization (materialize and index intermediate steps, before major / complex joins and / or aggregations)
- engine optimization (decide which operation can be performed where and which engine can be used, taking the data transfers into account)
The first attempt to do this is in the `tatrman` repository - the optimization somehow works there, but it is very slow. The goal would be to assess different optimizer approaches (do we use Calcite? do we take OptaPlanner on board? do we develop this ourselves? The thing is that the task is very different to existing SQL optimizers)
This will be a separate feature, separate design discussion, just mentioning it here for the future reference and for the "roadmap".

## Languages and Features

I want to have, from the v1, several "languages" that would result in the same execution graph:
- graphical language (like Knime and Alteryx) - graphical view of the execution graph, with different "skins"
- natural-language-like language - evolution of Byx (we will rename this)
- DSL "flow" (dot-linked operations) language, like Kyx, but better (hopefully). Also see the Kantheon's DSL here
- SQL(-like) declarative language
- Pandas-like language (much better version of RAE)
Design of these languages is the current focus of the project.

## Models

Language structures are not enough. We also need to define the "objects" the language works with, in our case the data objects.
For this, I assume we will be able to use objects from all the different model types (relational - db, E-R, multidimensional, later conceptual etc).
Similarly to the current ai-platform and Kantheon "translator", that understands SQL using E-R models, we will be able to use similar operations on tables, entities, cubes etc (not every operation will be available for every model, but the essential relational ones will be)
Especially for the multidimensional model, there will be lots of syntactical sugaring, allowing shortened expressions building. This will be again a special session.

