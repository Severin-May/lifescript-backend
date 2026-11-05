lexer grammar LifeScriptLexer;

// Keywords match in any case; names keep their case and are compared exactly.
options { caseInsensitive = true; }

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
BREAK           : 'break' ;
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

EVENTS          : 'events' ;
EVENT           : 'event' ;
DATE            : 'date' ;

// Identifiers: letters of any language. Marks (\p{M}) are needed by scripts like Hindi.
// The text is NFC-normalized so "é" typed as one or as two characters (e + accent) is the same name;
// the token keeps its original positions, so diagnostics still match the editor.
IDENTIFIER
    : [\p{L}_] [\p{L}\p{M}\p{N}_]*
      { setText(java.text.Normalizer.normalize(getText(), java.text.Normalizer.Form.NFC)); }
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

// Anything else becomes one whole word, so the parser can explain a mistake instead of the lexer
// dropping characters. Last, so on a tie of length every rule above wins.
UNCLOSED_STRING : '"' ~["\r\n]* ;
UNKNOWN_WORD    : ~[ \t\r\n:,\-#"]+ ;
