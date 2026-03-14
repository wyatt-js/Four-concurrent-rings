## Required Analysis

We chose to measure how increasing N affects latency. Increasing N increases the total number of threads. For the small N, OS can handle normally, but past 20000 N, it is exponentially getting harder to process, because the scheduler struggles with context.

Increasing H makes the token travel ring more times. Latency grows linearly with H. Unlike N, increasing H doesn’t create new threads; it just keeps the existing ones busy longer. This is a much healthier axis for Java because the system can stay stable and predictable. 

The worst case is when large N and large H because N floods the OS with threads while large H keep them all active simultaneously putting the pressure both on scheduler and the CPU at the same time. 

In short, N drives latency through scheduling overhead and scales badly, while H drives latency through computation and scales cleanly.

We chose those metrics as we found that granted an observable and managable way to change the latency of threads. The machine we tested on was very fast, so finding a way to quicly ramp up computation requirements was neccesary, which meant changing N and H. Simply changing the arguments in the start function call is very managable.

Java threads compare to elixir processes in that elixir processes are much more lightweight. Java threads need large amounts of stack allocation. When making a lot of nodes within each ring, in java much more memory will be taken up when compared to elixir. Also, elixir threads communicate through send/recieve, whereas communication like that in java takes more wiring and risks blocking. BEAM mailboxes are lock free which is nice when compared to Java threads sharing memory. Also, elixir using recursive functions is easier while Java uses while(true) to power loops. Elixir send is also non blocking while Java can have blocking queues. Also shutdown is nice in elixir where messages can be sent around the ring with no cleanup needed. Java needs shutdown flags and thread interruption.

We found surprising and learned that BEAM makes solving this problem feel natural throught the actor model. Java requires tip toeing over thread safety and shutdown ordering.

The test machine has 8 cores and 8GB of RAM. 
