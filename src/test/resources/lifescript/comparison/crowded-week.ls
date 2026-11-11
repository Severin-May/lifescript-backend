# A realistic week with more work (about 25h of tasks) than free time (about 21h).
plan: crowded_week
period: 2026-10-05 to 2026-10-11

settings:
    break: 10m

availability:
    monday: 09:00-12:00, 14:00-17:00
    tuesday: 09:00-12:00
    wednesday: 13:00-18:00
    thursday: 09:00-11:00, 15:00-17:00
    friday: 09:00-13:00

energy profile:
    default:
        09:00-11:00: high
        13:00-15:00: low
        15:00-18:00: moderate

routines:
    routine: standup
        time: 09:00
        repeats: weekdays
        activities:
            meeting: 15m

tasks:
    task: planning
        duration: 1h
        priority: critical
        effort: moderate
        deadline: 2026-10-05 12:00

    task: research
        duration: 3h
        priority: high
        effort: high

    task: write_report
        duration: 4h
        priority: high
        effort: high
        deadline: 2026-10-08 17:00
        dependencies: research

    task: review
        duration: 1h
        priority: medium
        effort: moderate
        dependencies: write_report

    task: slides
        duration: 2h
        priority: high
        effort: moderate
        deadline: 2026-10-09 13:00

    task: code_review
        duration: 2h
        priority: high
        effort: high

    task: budget
        duration: 2h
        priority: medium
        effort: low

    task: gym
        duration: 1h
        priority: medium
        effort: high
        repeats: monday, wednesday, friday

    task: email
        duration: 30m
        priority: low
        effort: low
        repeats: weekdays

    task: reading
        duration: 3h
        priority: low
        effort: low

    task: cleanup
        duration: 1h30m
        priority: low
        effort: low
