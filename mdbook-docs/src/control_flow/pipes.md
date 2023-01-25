# Pipes

Piping is a control flow mechanism that is not common to all languages, but is truly just syntactic sugar (one of the
few pure-sugar features in the language). Piping gives you a mechanism to decompose deeply nested function calls into a
linear chain of operations that happen one after the other much like any other imperative code you're familiar with. The
main thing to know is that on each line beginning with the "pipe" operator `|>`, the token `^` (known as the
"backreference" operator) refers to the value of the expression before the pipe operator. It is intended that the `^`
operator, visually resembles an arrow pointing upwards to the value produced on the line above.

```
var firstPipingSource = ["Claro", "piping", "is", "so", "cool"];

firstPipingSource
  |> getFirstAndLast(^)
  |> join(^, " is damn ")
  |> "{^}! I'll say it again... {^}!!"  # <-- Can backreference prev value more than once.
  |> var firstPipingRes = ^;

print(firstPipingRes);
```

The above prints the following:

`Claro is damn cool! I'll say it again... Claro is damn cool!!`

Compare to the highly nested alternative without piping (notice how use of piping in the above example even allows
elimination of an entire temporary variable):

```
var nonPipingSource = ["Claro", "piping", "is", "so", "cool"];

# With piping, this tmp var is unnecessary.
var joinedTmp = join(getFirstAndLast(nonPipingSource));

var nonPipingRes = "{joinedTmp}! I'll say it again... {joinedTmp}!!";

print(nonPipingRes);
```