**Brief context:**

Claro depends on JCUP as its parser generator. This tool has actually been very
useful in allowing me to quickly, and correctly iterate on Claro's syntax and
easily generate the AST from which to do the real heavy lifting. However,
starting around Nov/Dec 2022, I ended up running into a severe issue with the
generated parser class that JCUP was producing. Specifically, it was generating
a single CUP$ClaroParser$do_action_part00000000() method that was thousands of
lines long and actually triggered Java's fundamental "Code too large" compilation
error. I tried for a little while to work around this by refactoring the actions
for grammar productions that contained lots in common with other actions, but
it was clear that this was simply kicking the can down the road as it was having
only a minor reduction on the overall code size in the generated method. After
doing some code reading in the JCUP source that I found at
https://github.com/vbmacher/cup-maven-plugin I ended up discovering that JCUP
actually needed the most minimal change to a hardcoded constant
`UPPERLIMIT = 300` which controlled the number of actions whose code was
inlined into the generated CUP$ClaroParser$do_action_part00000000() method
before JCUP would automatically split the action code and produce a second
method CUP$ClaroParser$do_action_part00000001() (and so on at each multiple of
`UPPERLIMIT`). Unfortunately, the Claro grammar only had ~275 actions and had
already overflowed the limit. So, thankfully, all I needed to do was to reduce
the limit at which it splits the generated action handler method, and JCUP would
no longer generate code exceeding Java's codesize limit. This package simply
contains some build level hacking to reduce that limit <275. Read the code for
specifics on the "how".

*_Update - 4/26/23:_*

Ok, so after several months of smooth sailing after the above fix, yesterday I
ended up running into the same sort of issue once more. This time, rather than
the generated CUP$ClaroParser$do_action_part00000000() method being too large,
it was instead the generated `_action_table`/`_reduce_table`/`_production_table`
nested arrays that ended up being individually so large (as they're just massive
string concatenations passed into a preprocessing method) that they caused both
the CUP$ClaroParser class to hit Java's dreaded `code too large` error, but also
for the string concatenations representing the tables to actually become too
large for Java's constant pool. I managed to figure out a hack that changes
CUP's generated code to now split each generated string at 65500 bytes, bundle
it into a synthetic class called something along the lines of
`$SYNTHETIC_(ACTION|REDUCE|PRODUCTION)_TABLE_HOLDER_TO_WORKAROUND_CODE_TOO_LARGE_ERROR[0-9]+`
so that no individual class had to be responsible for holding all of the static
state. There's then some backflips done to dynamically concatenate all of these
giant strings using StringBuilders just to work around the builtin `+` concat
operator actually trying to be _too_ helpful and trying to optimize the full
concatenation into the constant pool only to find it too large still. I actually
believe that this is a robust fix that should mean I likely won't need to come
back to hack on CUP anymore for this same type of code too large error, but I
guess only time will tell.