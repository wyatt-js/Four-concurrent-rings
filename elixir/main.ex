defmodule Main do
  @moduledoc "Wyatt Smith, Longiy Tsin"
  import Bitwise

  def wrap_around(v) do
    masked = v |> band(0xFFFFFFFFFFFFFFFF)
    if masked >= (1 <<< 63), do: masked - (1 <<< 64), else: masked
  end

  def start(n, h) do
    rings = %{
      neg:      spawn(fn -> ring_manager(:neg, n, h) end),
      zero:     spawn(fn -> ring_manager(:zero, n, h) end),
      pos_even: spawn(fn -> ring_manager(:pos_even, n, h) end),
      pos_odd:  spawn(fn -> ring_manager(:pos_odd, n, h) end)
    }
    IO.puts("Started 4 rings (N=#{n}, H=#{h})")
    input_loop(rings, 1)
  end

  defp input_loop(rings, token_id) do
    case IO.gets("Enter integer (or 'done'): ") do
      :eof ->
        shutdown(rings)

      {:error, _} ->
        shutdown(rings)

      line ->
        trimmed = String.trim(line)

        case String.downcase(trimmed) do
          "done" ->
            shutdown(rings)

          _ ->
            case Integer.parse(trimmed) do
              {x, ""} ->
                ring_id =
                  cond do
                    x < 0 -> :neg
                    x == 0 -> :zero
                    rem(x, 2) == 0 -> :pos_even
                    true -> :pos_odd
                  end
                ring_pid = Map.fetch!(rings, ring_id)
                send(ring_pid, {:token, token_id, ring_id, x})
                IO.puts("  -> token #{token_id} routed to #{ring_id}")
                input_loop(rings, token_id + 1)

              _ ->
                IO.puts("Error: '#{trimmed}' is not a valid integer")
                input_loop(rings, token_id)
            end
        end
    end
  end

  defp shutdown(rings) do
    IO.puts("Shutting down... waiting for work")
    me = self()
    Enum.each(rings, fn {_id, pid} -> send(pid, {:done, me}) end)

    Enum.each(Map.values(rings), fn pid ->
      receive do
        {:stopped, ^pid} -> :ok
      end
    end)

    IO.puts("All rings stopped.")
  end


  defp ring_manager(ring_id, n, h) do
    first = build_ring(ring_id, n)
    ring_manager_loop(ring_id, h, first, :idle, :queue.new(), nil)
  end

  defp ring_manager_loop(ring_id, h, first, state, queue, done_ref) do
    receive do
      {:token, token_id, ^ring_id, x} ->
        case state do
          :idle ->
            inject(first, token_id, ring_id, x, h)
            ring_manager_loop(ring_id, h, first, :busy, queue, done_ref)

          :busy ->
            q2 = :queue.in({token_id, ring_id, x}, queue)
            ring_manager_loop(ring_id, h, first, :busy, q2, done_ref)
        end

      {:completed, token_id, _ring_id, orig_input, final_val} ->
        IO.puts("Ring #{ring_id}: token #{token_id} done (input=#{orig_input}, result=#{final_val})")

        case :queue.out(queue) do
          {{:value, {next_tid, next_rid, next_x}}, q2} ->
            inject(first, next_tid, next_rid, next_x, h)
            ring_manager_loop(ring_id, h, first, :busy, q2, done_ref)

          {:empty, _} when done_ref != nil ->
            stop_ring(first)
            send(done_ref, {:stopped, self()})

          {:empty, q2} ->
            ring_manager_loop(ring_id, h, first, :idle, q2, done_ref)
        end

      {:done, caller} ->
        case state do
          :idle ->
            stop_ring(first)
            send(caller, {:stopped, self()})

          :busy ->
            ring_manager_loop(ring_id, h, first, :busy, queue, caller)
        end
    end
  end

  defp inject(first, token_id, ring_id, x, h) do
    send(first, {:work, token_id, ring_id, x, x, h, self()})
  end

  defp stop_ring(first) do
    send(first, :stop)
  end


  defp build_ring(ring_id, n) when n >= 1 do
    first = spawn(fn -> node_init(ring_id) end)
    last = build_chain(ring_id, n - 1, first)
    send(last, {:set_next, first})
    first
  end

  defp build_chain(_ring_id, 0, prev), do: prev
  defp build_chain(ring_id, remaining, prev) do
    next = spawn(fn -> node_init(ring_id) end)
    send(prev, {:set_next, next})
    build_chain(ring_id, remaining - 1, next)
  end

  defp node_init(ring_id) do
    receive do
      {:set_next, next} -> node_loop(ring_id, next)
      :stop -> :ok
    end
  end

  defp node_loop(ring_id, next) do
    receive do
      {:work, token_id, rid, orig_input, cur_val, hops, manager} ->
        new_val = wrap_around(transform(rid, cur_val))

        case hops - 1 do
          0 ->
            send(manager, {:completed, token_id, rid, orig_input, new_val})

          left ->
            send(next, {:work, token_id, rid, orig_input, new_val, left, manager})
        end

        node_loop(ring_id, next)

      :stop ->
        send(next, :stop)
        :ok
    end
  end


  defp transform(:neg, v),      do: v * 3 + 1
  defp transform(:zero, v),     do: v + 7
  defp transform(:pos_even, v), do: v * 101
  defp transform(:pos_odd, v),  do: v * 101 + 1
end
