# Recursive Types

Claro supports the definition of new types that contain recursive self-references. For example a binary tree structure
is a classic recursive data structure where each Node in the tree contains a left and right child that may either be
another Node or nothing. The below is the definition of a Node that can only hold ints:

{{EX1}}

For example, the following initializes a simple tree with the root pointing to two children that have no children of 
their own:

{{EX2}}

## Parameterized Recursive Types

Of course, the above `IntNode` definition is too constrained, so ideally we'd define a single Node type that's able to
represent trees of arbitrary data types. So, a better Node type definition looks like:

{{EX3}}

Initialization looks exactly the same as in the concrete `IntNode` example above:

{{EX4}}