## What are Maps
Let's start with a simple example from the Bank DB. We have Clients and Branches, and the Bank assigns a home branch to every Client.
The relation between every Client and "his own home" Branch we will call a mapping, and the (logical) object that holds these relations we will call a Map.
In our logical model both Clients and Branches are Domains. A Map in our logical model therefore is an object that allows going from one Domain to another.

### Client-To-Branch Historical Mapping
Our initial example of Clients belonging to a home Branch was oversimplified, as in reality this assignment can (and does) change over time. What it means is that the relation between a Client and a Branch is not dependent only on the Client, but also on time. In our logical model let's assume that Clients are always re-assigned to new branches at the end of a period, so the mapping has to be a mapping of two Domains, Client and Period, to a Branch Domain.

We can achieve this by defining a Map that has two input (incoming) Domains (Client and Month) and one output (outgoing) Domain (Branch)

In general there can be Maps for relationships between N incoming and M outgoing Domains, although Maps with one output Domain only will be most common ones.
Another example of using 2:1 Map is a mapping at the beginning of the cost allocation process, where as an input we have a cost base on the General Leger level of detail (accounts x cost centers) and as an initial step we want to reorganize it on a less granular level of basic activities and cost centers. For this we are using a Map mapping Account and CostCenter Domains to Activities (it is not enough to map just an Account to Activity)

### Building Hierarchies
With the mapping concept on Domains a Hierarchy is simply a sequence of (single Domain) Maps, where an output Domain of Mapi is and input Domain of Mapi+1.
