# Deadlock Bug in Concurrent Lock Acquisition

The file `src/main/java/Deadlock01Bad.java` contains a concurrent program
that uses two `ReentrantLock` instances. Two threads acquire these locks in
different orders, which can cause a deadlock under certain thread interleavings.

Your task: **Find and fix the deadlock** so that the program completes
successfully under all possible thread interleavings.

You can verify your fix by running:

```
./gradlew frayTest
```

The test uses Fray (a concurrency testing framework) to systematically
explore thread interleavings. A passing test means your fix eliminates the
deadlock.
