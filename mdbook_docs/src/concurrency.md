# Concurrency

There is one remaining significant factor that a programming language should provide builtin mechanisms for in order to
enable programmers to develop very highly performant code that can take full advantage of the available CPU hardware:
concurrency.

Sometimes you have already squeezed every last drop of performance out of your algorithmic designs, or you are
constrained by waiting for slow operations to complete (DB requests, networked API calls, file I/O) before your program
may even make progress through its workload. In these situations, often the *only* way possible to get more work done is
to do more than one thing at the same time.

In order to achieve this, Claro asks you to first think about the dependencies between the various steps in your desired
workflow. These dependencies come in the form of data, so you should be asking yourself, "At any given step in my
workflow, what data do I need to be available in order to make the decisions I'll need to make or to take the actions
needed?". When you start to reason in this way, you will likely come across opportunities where certain components of
your workflow are completely independent, in the sense that they do not rely at all upon the same data in order to do
their work. Examples of this are easy to see in web service request handling (each reg can typically be handled
independently of any others), or if you look a bit closer it can also be seen in MapReduce style batch processing (the
large input is partitioned for the workers to map independently of other partitions. There will be many more examples,
but the key takeaway is that if these work items can be partitioned to be completely independent like this, then they
should be run at exactly the same time rather than sequentially. In a single-machine context, you achieve this by using
multiple threads to execute your program, or portions of your program, concurrently.

Unfortunately, using threads is known to have inherent dangers. Mistakes with threaded programs have been known to
cause "deadlocking" or other issues where a program becomes completely stock and is unable to make forward progress.
Alternatively, you may run into "data races" where multiple threads attempt to read/write the same shared data
simultaneously, each not knowing that another thread may be impacting or be impacted by the state change - this leads to
consistency problems where threads end up operating on stale, corrupted, or inconsistent data. These have tended to be
reasons for people to fully avoid working with multithreaded code at all - but that caution is just leaving performance
on the table. Thankfully, Claro addresses these issues and provides convenient, fearless concurrency! 
