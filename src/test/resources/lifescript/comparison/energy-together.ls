# 'x' is equally off at 09:00 (high energy) and 10:00 (low), so greedy takes the earlier hour,
# the only good one for 'y'. Matching both at once gives 'y' the high hour.
plan: energy_together
period: 2026-10-05 to 2026-10-05

availability:
    monday: 09:00-11:00

energy profile:
    default:
        09:00-10:00: high
        10:00-11:00: low

tasks:
    task: x
        duration: 1h
        priority: critical
        effort: moderate

    task: y
        duration: 1h
        priority: high
        effort: high
