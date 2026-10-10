# A complete, valid plan that uses every section and property.

plan: my_week
period: 2026-10-05 to 2026-10-11

settings:
  morning: 08:00-12:00
  afternoon: 13:00-17:00

availability:
  monday: flexible
  tuesday: 09:00-12:00, 14:00-18:00
  wednesday: morning, evening
  thursday: afternoon
  friday: 09:00-17:00

energy profile:
  default:
    morning: high
    afternoon: moderate
    evening: low
  friday:
    afternoon: low

routines:
  routine: morning_routine
    time: 07:00
    repeats: daily
    activities:
      stretch: 15m
      shower: 20m

  routine: evening_walk
    time: 19:00
    repeats: weekdays
    activities:
      walk: 45m

  routine: team_sync
    time: 10:00
    repeats: monday, wednesday
    activities:
      standup: 30m

tasks:
  task: research
    duration: 2h
    priority: high
    effort: high
    deadline: 2026-10-07

  task: write_report
    duration: 1h30m
    priority: critical
    effort: high
    deadline: 2026-10-09
    dependencies: research
    note: "final draft for review"

  task: inbox_zero
    duration: 20m
    priority: low
    effort: low
    repeats: weekdays

events:
  event: dentist
    date: 2026-10-07
    time: 10:00-11:00
    note: "bring insurance card"

  event: team_offsite
    date: 2026-10-09
    time: afternoon
