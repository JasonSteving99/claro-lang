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