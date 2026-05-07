parser grammar LifeScriptParser;
options { tokenVocab=LifeScriptLexer; }

plan
    : PLAN COLON STRING NEWLINE
      period
      settings?
      availability?
      energyProfile?
      routines?
      NEWLINE*
      EOF
    ;

routines
    : ROUTINES COLON NEWLINE
      routineEntry+
      NEWLINE*
    ;

routineEntry
    : ROUTINE COLON STRING NEWLINE
      routineTime
      routineRepeats?
      routineActivities
    ;

routineTime
    : TIME COLON (namedPeriod | timeRange) NEWLINE*
    ;

routineRepeats
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
    : dayOfWeek COLON availabilityValue NEWLINE*
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
    : PERIOD COLON DATE TO DATE NEWLINE
    ;
