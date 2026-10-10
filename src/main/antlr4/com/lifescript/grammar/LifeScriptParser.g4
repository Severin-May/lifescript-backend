parser grammar LifeScriptParser;
options { tokenVocab=LifeScriptLexer; }

plan
    : eol? PLAN COLON IDENTIFIER eol
      period
      planSection*
      EOF
    ;

period
    : PERIOD COLON DATE TO DATE eol
    ;

planSection
    : settings
    | availability
    | energyProfile
    | routines
    | tasks
    ;

// Settings
settings
    : SETTINGS COLON eol
      settingsEntry+
    ;

settingsEntry
    : namedPeriod COLON timeRange eol
    ;

// Availability
availability
    : AVAILABILITY COLON eol
      availabilityEntry+
    ;

availabilityEntry
    : dayOfWeek COLON availabilityValue eol
    ;

availabilityValue
    : FLEXIBLE
    | namedPeriodList
    | timeRangeList
    ;

namedPeriodList
    : namedPeriod (COMMA namedPeriod)*
    ;

timeRangeList
    : timeRange (COMMA timeRange)*
    ;

// Energy profile
energyProfile
    : ENERGY PROFILE COLON eol
      energyProfileEntry+
    ;

energyProfileEntry
    : (DEFAULT | dayOfWeek) COLON eol
      energyEntry+
    ;

energyEntry
    : (namedPeriod | timeRange) COLON energyLevel eol
    ;

energyLevel
    : HIGH | MODERATE | LOW
    ;

// Routines
routines
    : ROUTINES COLON eol
      routineEntry+
    ;

routineEntry
    : ROUTINE COLON IDENTIFIER eol
      routineProperty+
    ;

routineProperty
    : routineTime
    | repeats
    | routineActivities
    ;

// Start time only; the routine lasts as long as its activities add up to.
routineTime
    : TIME COLON TIME_VAL eol
    ;

routineActivities
    : ACTIVITIES COLON eol
      activityEntry+
    ;

activityEntry
    : IDENTIFIER COLON DURATION_VAL eol
    ;

// Repetition
repeats
    : REPEATS COLON repeatPattern eol
    ;

repeatPattern
    : DAILY | WEEKDAYS | WEEKENDS | dayList
    ;

dayList
    : dayOfWeek (COMMA dayOfWeek)*
    ;

// Tasks
tasks
    : TASKS COLON eol
      task+
    ;

task
    : TASK COLON IDENTIFIER eol
      taskProperty+
    ;

taskProperty
    : taskDuration
    | taskPriority
    | taskEffort
    | taskDeadline
    | repeats
    | taskDependencies
    | taskNote
    ;

taskDuration
    : DURATION COLON DURATION_VAL eol
    ;

taskPriority
    : PRIORITY COLON priorityLevel eol
    ;

priorityLevel
    : CRITICAL | HIGH | MEDIUM | LOW
    ;

taskEffort
    : EFFORT COLON energyLevel eol
    ;

taskDeadline
    : DEADLINE COLON DATE eol
    ;

taskDependencies
    : DEPENDENCIES COLON IDENTIFIER (COMMA IDENTIFIER)* eol
    ;

taskNote
    : NOTE COLON STRING eol
    ;

// Common stuff
dayOfWeek
    : MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY | SATURDAY | SUNDAY
    ;

namedPeriod
    : MORNING | AFTERNOON | EVENING
    ;

timeRange
    : TIME_VAL DASH TIME_VAL
    ;

eol
    : NEWLINE+
    ;