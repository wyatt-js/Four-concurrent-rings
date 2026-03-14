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
