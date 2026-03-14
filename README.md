# Four Concurrent Rings

**Wyatt Smith & Longiy Tsin**

---

## Explaining Architecture and Concurrency Designs

### Elxir

Enter with `iex`

Optionally start the Beam Monitor with `BeamMon.start()`

Then `Main.start(N, H)`

Inputs are correctly routed by first classifying the input integer (either negative, zero, positive even, or positive odd). The token is then sent as a message {:token, token_id, ring_id, x} to that ring's manager process. Token Id keeps increasing by 1 so every token is unique.

Exactly H hops are executed per token thanks to the hops counter. Once a node changes the current value, hops is decremented. If hops == 0, it is the last hop and it sends a completed message to the manager along with the final token value. Otherwise, it continues the hops to the next node, and loops around if H > N.

FIFO is enforced within each ring using a queue (strictly FIFO) which is built into elixir. The ring manager can be idle, where the first available (first in) token in the queue is processed, or busy, where a new token is pushed on the queue with :queue.in. Completed messages make the manager pop from the queue.

Each ring has only a single in-flight token thanks to the ring manager processes which only injects a token when transitioning from idle to busy or when a completed message arrives.

Deadlocks are avoided because each ring has an independent set of processes, and messages are not sent to eachother. Also, no node within the rings waits on responses from the manager or previous nodes.

Shutdown happens cleanly because 'done' sends a message to all ring managers. If a ring is busy, it won't accept new tokens, but it finishes the current token with the rest of the queue.

All processes are terminated properly because of node_loop, where stop is forwarded to the next neighbor, and around the ring.

### Java
Enter with `javac -d out src/main/*.java` then `java -cp out main.Main`

Optionally the JVM Monitor starts automatically with `JvmMon.start(5_000)`

Then enter N and H when prompted, and start typing integers.

Inputs are first classified as negative, zero, positive even, or positive odd. Then the token {:token, token_id, ring_id, x} is sent to the correct ring manager. The token_id increases by 1 so each token is unique.

Each token makes exactly H hops using a hops counter. Every time a node updates the value, hops decreases by 1. When hops == 0, the node sends the final result to the manager. Otherwise, the token continues to the next node and loops if H > N.

FIFO order is maintained using Elixir’s built-in queue. If the manager is busy, new tokens are added to the queue. When a token finishes, the manager processes the next one.

Each ring has only one active token at a time. Deadlocks are avoided because rings work independently and nodes do not wait for responses.

Shutdown works by sending a done message to all managers, and nodes stop cleanly by passing a stop message around the ring.
---

## 1. Requirements / Overview

Build a concurrent system composed of four independent rings of processes. You will implement this in **Elixir**, and then again in **Java**. In Elixir the rings will have spawned processes. In Java, these will be threads. Using the two programs, we will compare the relative performance of actor-style processes vs. threads on shared data.

A central **Main process (Coordinator)** will:

- Read integer inputs from the keyboard
- Route each integer to exactly one of four rings (based on value)
- Print a completion message from each ring when that ring finishes processing one input

Each **ring**:

- Contains **N** nodes arranged in a directed cycle (parameter N given at start of execution)
- Circulates a work token (message) for exactly **H** hops (H given at start)
- Allows at most one token "in flight" at a time (other inputs needing that ring wait)
- Maintains **FIFO** ordering for queued inputs

The four rings may operate concurrently. While the user may have input dozens of inputs, only 4 at most are being processed at any time.

You will implement this twice: in **Java** (threads) and **Elixir** (processes/BEAM). You may use whatever AI coding tools and assistants you wish.

---

## 2. Program Parameters

At startup, the program receives:

| Parameter | Description | Constraint |
|-----------|-------------|------------|
| **N** | Ring size (number of nodes per ring) | N >= 1 |
| **H** | Number of hops per token | H >= 1 |

**Examples:**

- **N=1000, H=3000** — Each ring has 1,000 nodes; each input makes 3 full trips around its ring.
- **N=50000, H=36** — Each ring has 50,000 nodes; each input visits only the first 36 nodes (reported by node 36, remaining nodes not visited). This is an unusual but possible combination.
- **N=6000, H=12456** — Each work request goes twice around the ring plus a little more.

You should test with increasingly large N values (100, 5000, 20000, 100000, etc.) until your machine cannot manage it. H can be used to keep cores busy doing more arithmetic and less process swapping (e.g., N=5000, H=100000).

---

## 3. Input Behavior

The program repeatedly prompts the user for input:

- If the user types **`done`**:
  - Stop accepting new input
  - Finish all queued and in-flight work
  - Shut down cleanly
- Otherwise:
  - Attempt to parse input as integer **X**
  - If parsing fails, print an error
  - If parsing succeeds, route X to a ring

Each accepted input is assigned a unique **token_id**.

---

## 4. Routing Rules

| Condition | Ring |
|-----------|------|
| X < 0 | **NEG** |
| X == 0 | **ZERO** |
| X > 0 and even | **POS_EVEN** |
| X > 0 and odd | **POS_ODD** |

---

## 5. Ring Structure

Each ring consists of:

- Exactly **N** nodes (processes or threads)
- Nodes connected in a directed cycle
- A mechanism enforcing:
  - At most one token active in the ring at a time
  - FIFO queueing of additional inputs

The rings operate independently and concurrently.

---

## 6. Token Definition

Each token must contain:

| Field | Description |
|-------|-------------|
| `token_id` | Unique identifier |
| `ring_id` | Which ring this token belongs to |
| `orig_input` | Original input value (constant) |
| `current_val` | Current computed value (mutable) |
| `remaining_hops` | Hops left (initially H) |

**Initialization:**

```
current_val  := orig_input
remaining_hops := H
```

---

## 7. Hop Processing

When a node receives a token:

1. Apply the ring-specific transformation to `current_val`
2. Decrement `remaining_hops`
3. If `remaining_hops > 0`, forward token to next node
4. If `remaining_hops == 0`, report completion to manager process for reporting results

All arithmetic must use **signed 64-bit wraparound** semantics. (Java `long` overflow is acceptable; Elixir must implement wraparound explicitly.)

---

## 8. Ring Transformations

| Ring | Transformation |
|------|---------------|
| **NEG** | `v := v * 3 + 1` |
| **ZERO** | `v := v + 7` |
| **POS_EVEN** | `v := v * 101` |
| **POS_ODD** | `v := v * 101 + 1` |

---

## 9. Queueing Constraint

For each ring independently:

- If the ring is **idle**, inject a new token immediately
- If the ring is **busy**, enqueue the request in a FIFO queue
- When the active token completes, inject the next queued request

**Invariant:** At most one token may circulate within a ring at any time.

---

## 10. Correctness Requirements

Your implementation must:

- Route inputs correctly
- Execute exactly H hops per token
- Enforce FIFO per ring
- Enforce single in-flight token per ring
- Avoid deadlock
- Shut down cleanly after `done`
- Terminate all threads/processes properly

---

## 11. Exploratory Performance Investigation

Instrument your system and explore questions such as:

- How does increasing N affect latency?
- How does increasing H affect throughput?
- What happens if inputs are heavily skewed to one ring?
- Does Java behave differently from Elixir under load?
- How does the system scale as concurrency increases? Compare how many threads Java can handle vs. how many processes Elixir can manage.
- Where do you observe bottlenecks?
- Is performance CPU-bound? Synchronization-bound? Memory-bound?
- What scheduling behavior do you observe?

You may design and measure any performance metrics you find meaningful.

### BEAM Monitor (Elixir)

The following module creates a monitor process that periodically (every 5 seconds) prints information about processes and activities in the BEAM:

```elixir
defmodule BeamMon do
  @moduledoc false

  def start(interval_ms \\ 5_000) do
    spawn(fn -> loop(interval_ms, true) end)
  end

  defp loop(interval_ms, first?) do
    receive do
      :stop -> :ok
    after
      if(first?, do: 0, else: interval_ms) ->
        mem_bytes = :erlang.memory(:total)
        mem_gb = mem_bytes / (1024 * 1024 * 1024)
        proc_count = :erlang.system_info(:process_count)
        run_queue = :erlang.statistics(:run_queue)

        IO.puts("[BEAM] mem=#{fmt(mem_gb)} GB procs=#{proc_count} run_queue=#{run_queue}")
        loop(interval_ms, false)
    end
  end

  defp fmt(x), do: :io_lib.format("~.2f", [x]) |> List.to_string()
end
```

Add this to your Elixir code and call `BeamMon.start()` to monitor the BEAM. Find a way to do something similar with Java for the JVM.

---

## 12. Required Analysis

Submit a brief analysis (1-2 pages) discussing:

- What you chose to measure
- Why you chose those metrics
- What patterns you observed
- How Java threads compare to BEAM processes
- What surprised you (if anything)

Also explain your test machine specs (cores, RAM) and what trials you ran (values of N and H).

---

## 13. Deliverables

- **Source code** (Java and Elixir implementations)
- **README** — Explaining architecture and concurrency designs
- **ANALYSIS** — Brief analysis document

---

## 14. Alternate Versions

If you find this experiment intriguing, consider these alternatives:

### Lift the single-token restriction

The default spec (one token at a time per ring) primarily tests how many processes each system can create and manipulate, with only 4 (or a few more, like managers) active at a time. The primary CPU usage is the 4 processes doing arithmetic plus the BEAM/JVM doing process/thread management (message passing, thread locking, waking up, etc.).

Try lifting the "one active token per ring" restriction and letting all nodes in a ring do arithmetic concurrently (given sufficient inputs).

- **Elixir** handles this easily since a mailbox is a FIFO queue — route all inputs for a ring to the first process, and work requests queue up in the mailbox.
- **Java** may require different thinking. If your "one at a time" version uses a single token data structure locked by each thread, you'll need multiple token data structures with appropriate synchronization.

### Randomized hop counts

When an input is routed to a ring, generate a random number of hops (perhaps related to H, like a random integer multiple of H). This alters repeatability and causes Elixir and Java results to differ for the same inputs.

### Computationally intensive nodes

Replace simple arithmetic with something heavier. Keep the value transforms from Section 8, then add a CPU burn loop:

```
sq = sqrt(v)
p = power(sq, sq)    // sqrt(v) raised to the power sqrt(v)
x = p
```

Convert to int if necessary, and handle overflow gracefully (take `sqrt(sqrt(...))` to keep values smaller if needed). Run this in a loop a large number of times (varying randomly or per hop). The assignments help prevent compiler optimizations from removing the computation.
