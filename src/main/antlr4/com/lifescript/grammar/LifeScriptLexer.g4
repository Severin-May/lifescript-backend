lexer grammar LifeScriptLexer;

// Fragments
fragment DIGIT  : [0-9] ;
fragment HOUR   : DIGIT DIGIT ;
fragment MINUTE : DIGIT DIGIT ;
fragment YEAR   : DIGIT DIGIT DIGIT DIGIT ;
fragment MONTH  : DIGIT DIGIT ;
fragment DAY    : DIGIT DIGIT ;

// Keywords
PLAN            : 'plan' ;
PERIOD          : 'period' ;
TO              : 'to' ;

SETTINGS        : 'settings' ;
MORNING         : 'morning' ;
AFTERNOON       : 'afternoon' ;
EVENING         : 'evening' ;

AVAILABILITY    : 'availability' ;
OFF             : 'off' ;
FLEXIBLE        : 'flexible' ;
MONDAY          : 'Monday' ;
TUESDAY         : 'Tuesday' ;
WEDNESDAY       : 'Wednesday' ;
THURSDAY        : 'Thursday' ;
FRIDAY          : 'Friday' ;
SATURDAY        : 'Saturday' ;
SUNDAY          : 'Sunday' ;

ENERGY          : 'energy' ;
PROFILE         : 'profile' ;
DEFAULT         : 'default' ;
HIGH            : 'high' ;
MODERATE        : 'moderate' ;
LOW             : 'low' ;

ROUTINES        : 'routines' ;
ROUTINE         : 'routine' ;
ACTIVITIES      : 'activities' ;
TIME            : 'time' ;
REPEATS         : 'repeats' ;
DAILY           : 'daily' ;
WEEKDAYS        : 'weekdays' ;
WEEKENDS        : 'weekends' ;

TASKS           : 'tasks' ;
TASK            : 'task' ;
DURATION        : 'duration' ;
PRIORITY        : 'priority' ;
EFFORT          : 'effort' ;
DEADLINE        : 'deadline' ;
START           : 'start' ;
DEPENDENCIES    : 'dependencies' ;
NOTE            : 'note' ;
CRITICAL        : 'critical' ;
MEDIUM          : 'medium' ;

// Symbols
COLON           : ':' ;
DASH            : '-' ;
COMMA           : ',' ;


// Literals
TIME_VAL        : HOUR ':' MINUTE ;
DATE            : YEAR '-' MONTH '-' DAY ;
STRING          : '"' (~["\r\n])* '"' ;
DURATION_VAL    : DIGIT+ 'h' (DIGIT+ 'm')? | DIGIT+ 'm' ;


// Whitespace
NEWLINE         : '\r'? '\n' ;
WS              : [ \t]+ -> skip ;