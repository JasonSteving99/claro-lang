# Procedures

All languages tend to have a way to encapsulate a block of logic in one place so that it can be reused throughout the
program. Generally, however, languages tend to provide only a single tool for this job, the function. The problem I see
with this is that not all functions in these languages are created equal - but yet they're all forced to share the same
structure which has some unfortunate implications. The general idea is straightforward: a function takes in some data,
manipulates it somehow, and possibly returns some data. However, not all functions take input, and not all of them
return data ("void" is not data... looking at you, Java and friends). To me, this is very unclear using a single
structure, functions, for meaningfully different purposes. Claro addresses this by getting specific. Claro provides
"Procedures" broken into a few sub-categories: Functions, Consumers, and Providers.