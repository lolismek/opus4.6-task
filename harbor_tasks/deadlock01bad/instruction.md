# Deadlock Bug in Concurrent Lock Acquisition

The file `Deadlock01Bad.java` contains a concurrent program that uses two
`ReentrantLock` instances. Two threads acquire these locks in different orders,
which can cause a deadlock under certain thread interleavings.

Your task: **Find and fix the deadlock** so that the program completes
successfully under all possible thread interleavings.

You can verify your fix by running:

```
javac Deadlock01Bad.java && fray -cp . Deadlock01Bad -- --iter 100000
```

The `fray` tool systematically explores thread interleavings to find
concurrency bugs. A passing run (exit code 0) means your fix eliminates the
deadlock.
