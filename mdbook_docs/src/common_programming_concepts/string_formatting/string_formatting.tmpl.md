# String Formatting

Formatting strings is an incredibly common programming task, whether it be for the sake of debugging or for building 
full-fledged CLI programs. Claro attempts to simplify this process by providing syntax sugar for directly templating
arbitrary expressions directly into a string. 

<div class="warning">

**Note**: At the moment, Claro only supports single-line strings, but multi-line strings are planned. Stay tuned for 
this in a future release.
</div>

To take advantage of this, any expression can be formatted into a string by wrapping it in `{...}`.

{{EX1}}

## Escaping Curly-Braces in Strings

While Format Strings are very convenient, this does have the consequence of giving curly-braces a special significance
in string literals. So, to type a string literal that contains the `{` char, you must escape it using `\{`, for example:

{{EX2}}