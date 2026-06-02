parser grammar LifeScriptParser;
options { tokenVocab=LifeScriptLexer; }

plan
    : PLAN COLON STRING NEWLINE
      period
      planSection*
      NEWLINE*
      EOF
    ;

planSection
    : settings
    | availability
    | energyProfile
    | routines
    | tasks
    ;

tasks
    : TASKS COLON NEWLINE
      task+
      NEWLINE*
    ;

task
    : TASK COLON STRING NEWLINE
      taskProperty+
    ;

taskProperty
    : taskDuration
    | taskPriority
    | taskEffort
    | taskDeadline
    | taskStart
    | repeats
    | taskDependencies
    | taskNote
    ;

taskDuration
    : DURATION COLON DURATION_VAL NEWLINE*
    ;

taskPriority
    : PRIORITY COLON priorityLevel NEWLINE*
    ;

priorityLevel
    : CRITICAL | HIGH | MEDIUM | LOW
    ;

taskEffort
    : EFFORT COLON energyLevel NEWLINE*
    ;

taskDeadline
    : DEADLINE COLON (dayOfWeek | DATE) NEWLINE*
    ;

taskStart
    : START COLON TIME_VAL NEWLINE*
    ;

taskDependencies
    : DEPENDENCIES COLON STRING (COMMA STRING)* NEWLINE*
    ;

taskNote
    : NOTE COLON STRING NEWLINE*
    ;

routines
    : ROUTINES COLON NEWLINE
      routineEntry+
      NEWLINE*
    ;

routineEntry
    : ROUTINE COLON STRING NEWLINE
      routineProperty+
    ;

routineProperty
    : routineTime
    | repeats
    | routineActivities
    ;

routineTime
    : TIME COLON (namedPeriod | timeRange) NEWLINE*
    ;

repeats
    : REPEATS COLON repeatPattern NEWLINE*
    ;

repeatPattern
    : DAILY | WEEKDAYS | WEEKENDS | dayList
    ;

dayList
    : dayOfWeek (COMMA dayOfWeek)*
    ;

routineActivities
    : ACTIVITIES COLON NEWLINE
      activityEntry+
      NEWLINE*
    ;
activityEntry
    : STRING COLON DURATION_VAL NEWLINE*
    ;

energyProfile
    : ENERGY PROFILE COLON NEWLINE
      energyProfileEntry+
      NEWLINE*
    ;

energyProfileEntry
    : (DEFAULT | dayOfWeek) COLON NEWLINE
      energyEntry+
    ;

energyEntry
    : (namedPeriod | timeRange) COLON energyLevel NEWLINE*
    ;

energyLevel
    : HIGH | MODERATE | LOW
    ;

availability
    : AVAILABILITY COLON NEWLINE
      availabilityEntry+
      NEWLINE*
    ;

availabilityEntry
    : (dayOfWeek | DATE) COLON availabilityValue NEWLINE*
    ;

dayOfWeek
    : MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY | SATURDAY | SUNDAY
    ;

availabilityValue
    : OFF
    | FLEXIBLE
    | namedPeriodList
    | timeRangeList
    | namedPeriodList COMMA timeRangeList
    ;

namedPeriodList
    : namedPeriod (COMMA namedPeriod)*
    ;

timeRangeList
    : timeRange (COMMA timeRange)*
    ;

settings
    : SETTINGS COLON NEWLINE
      settingsEntry+
      NEWLINE*
    ;

settingsEntry
    : namedPeriod COLON timeRange NEWLINE*
    ;

namedPeriod
    : MORNING | AFTERNOON | EVENING
    ;

timeRange
    : TIME_VAL DASH TIME_VAL
    ;

period
    : PERIOD COLON DATE TO DATE NEWLINE+
    ;
