# Greedy places 'a' first at 09:00 and has no 2h gap left for 'b'; moving 'a' to 12:00 fits both.
plan: blocks_itself
period: 2026-10-05 to 2026-10-05

availability:
    monday: 09:00-11:00, 12:00-13:00

tasks:
    task: a
        duration: 1h
        priority: high
        effort: moderate

    task: b
        duration: 2h
        priority: medium
        effort: moderate
