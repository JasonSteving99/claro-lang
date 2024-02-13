# Opaque Types

Programs of any significant size, particularly those developed among potentially large groups of developers, require a
significant level of coordination to ensure the program evolves in a controlled manner throughout entire development 
lifecycle. Claro aspires to provide users with tools to that end. One particular technique that can aid this is hiding
the internal representation of a data structure. There are various reasons why this may be desirable:

| Concern                                                                                         | Example Undesirable Outcome                                                                                                                                                 |
|-------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Internal representation is subject to significant future change                                 | Many downstream usages of the Type's internal representation arise, making future changes to the Type's internal representation unbearably onerous                          |
| Internal representation must restrict the legal domain of values                                | Users directly manipulating the value can invalidate these necessary constraints and produce invalid data                                                                   |
| Internal representation is encoded in some way not explicitly described by the types themselves | Complex data structures, such as a Heap, may be represented internally as a simple `mut [int]` but a downstream user mutating this structure is inherently bug-prone        |
| Internal representation contains Type's that give access to sensitive behaviors                 | A database connection, for example, may have various open channels to the DB itself, but this doesn't mean it's safe to use this to manually send it arbitrary network reqs |

To address all of these, developers typically **hide the internal representation** of such sensitive Types so that
any direct interaction with them must necessarily go through "the front door" of a certain published, verified API. 
Claro honors this as a first class capability, by allowing Types exported by a Module definition to be marked "Opaque":

{{EX1}}

Now, consumers of a Module with the above Type definition will not be exposed whatsoever to any internal details of its
internal representation, which may initially look like:

{{EX2}}

but then, in the future, it could be updated to something like:

{{EX3}}

and you'd have a guarantee that you wouldn't have to make **any** changes outside the defining Module itself because it
was never possible for any downstream consumer to actually depend on the internal representation directly.