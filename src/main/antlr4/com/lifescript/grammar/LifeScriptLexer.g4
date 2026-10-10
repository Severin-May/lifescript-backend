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
FLEXIBLE        : 'flexible' ;
MONDAY          : 'monday' ;
TUESDAY         : 'tuesday' ;
WEDNESDAY       : 'wednesday' ;
THURSDAY        : 'thursday' ;
FRIDAY          : 'friday' ;
SATURDAY        : 'saturday' ;
SUNDAY          : 'sunday' ;

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
DEPENDENCIES    : 'dependencies' ;
NOTE            : 'note' ;
CRITICAL        : 'critical' ;
MEDIUM          : 'medium' ;

// Identifiers
IDENTIFIER
    : [a-z_] [a-z0-9_]*
    ;

// Symbols
COLON           : ':' ;
DASH            : '-' ;
COMMA           : ',' ;

// Literals
TIME_VAL        : HOUR ':' MINUTE ;
DATE_VAL        : YEAR '-' MONTH '-' DAY ;
STRING          : '"' (~["\r\n])* '"' ;
DURATION_VAL    : DIGIT+ 'h' (DIGIT+ 'm')? | DIGIT+ 'm' ;

// Whitespace
NEWLINE         : '\r'? '\n' ;
WS              : [ \t]+ -> skip ;
COMMENT         : '#' ~[\r\n]* -> skip;