# Fearless Concurrency

Developed by a Xoogler taking inspiration from years of hands-on development experience with Google-internal backend web
frameworks, Claro moves well beyond async/await. Claro’s declarative, DAG-based structured concurrency model is the 
first of its kind in a programming language - statically guaranteeing that programs are:

- Non-blocking
- Data-race free
- Deadlock free
- Optimally scheduled
- Scalable by default

The following sections will introduce you to the language features that enable Claro's safe concurrency guarantees.