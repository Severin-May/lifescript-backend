package com.lifescript.compiler;

import org.junit.jupiter.api.Test;

import static com.lifescript.TestPlans.syntaxErrors;

class TmpSyntaxSurveyTest {

    private static final String HEAD = "plan: p\nperiod: 2030-01-07 to 2030-01-13\n\navailability:\nmonday: flexible\n\ntasks:\n";

    private static void show(String label, String body) {
        System.out.println("### " + label + "  |  " + body.replace("\n", " / "));
        for (String e : syntaxErrors(HEAD + body)) {
            System.out.println("    " + e.replace("\n", "\\n").replace("\r", "\\r"));
        }
    }

    @Test
    void survey() {
        show("missing colon", "task: a\nduration 1h\npriority: high\neffort: high\n");
        show("missing colon after task", "task a\nduration: 1h\npriority: high\neffort: high\n");
        show("typo keyword", "task: a\nduraton: 1h\npriority: high\neffort: high\n");
        show("typo value", "task: a\nduration: 1h\npriority: hgh\neffort: high\n");
        show("wrong value kind", "task: a\nduration: 1h\npriority: moderate\neffort: high\n");
        show("decimal duration", "task: a\nduration: 1.5h\npriority: high\neffort: high\n");
        show("duration words", "task: a\nduration: 90 min\npriority: high\neffort: high\n");
        show("bare number", "task: a\nduration: 90\npriority: high\neffort: high\n");
        show("bare number at end", "task: a\npriority: high\neffort: high\nduration: 5");
        show("missing value", "task: a\nduration:\npriority: high\neffort: high\n");
        show("space in name", "task: write report\nduration: 1h\npriority: high\neffort: high\n");
        show("dash in name", "task: write-report\nduration: 1h\npriority: high\neffort: high\n");
        show("digit first name", "task: 2nd_draft\nduration: 1h\npriority: high\neffort: high\n");
        show("slash date", "task: a\nduration: 1h\npriority: high\neffort: high\ndeadline: 2030/01/10 17:00\n");
        show("one digit hour", "task: a\nduration: 1h\npriority: high\neffort: high\ndeadline: 2030-01-10 9:00\n");
        show("unterminated note", "task: a\nduration: 1h\npriority: high\neffort: high\nnote: \"hello\n");
        show("note without quotes", "task: a\nduration: 1h\npriority: high\neffort: high\nnote: hello there\n");
        show("event prop in task", "task: a\nduration: 1h\npriority: high\neffort: high\ndate: 2030-01-08\n");
        show("unknown section", "task: a\nduration: 1h\npriority: high\neffort: high\n\ngoals:\nx: 1h\n");
        show("deps without names", "task: a\nduration: 1h\npriority: high\neffort: high\ndependencies:\n");
        show("deps trailing comma", "task: a\nduration: 1h\npriority: high\neffort: high\ndependencies: b,\n");
        show("empty tasks", "");
        show("odd char", "task: a\nduration: 1h\npriority: high!\neffort: high\n");
        System.out.println("### no period");
        for (String e : syntaxErrors("plan: p\n\ntasks:\ntask: a\nduration: 1h\npriority: high\neffort: high\n")) System.out.println("    " + e);
        System.out.println("### period dash");
        for (String e : syntaxErrors("plan: p\nperiod: 2030-01-07 - 2030-01-13\n")) System.out.println("    " + e);
        System.out.println("### day typo");
        for (String e : syntaxErrors("plan: p\nperiod: 2030-01-07 to 2030-01-13\n\navailability:\nmondy: flexible\n")) System.out.println("    " + e);
        System.out.println("### range with spaces 9-17");
        for (String e : syntaxErrors("plan: p\nperiod: 2030-01-07 to 2030-01-13\n\navailability:\nmonday: 9-17\n")) System.out.println("    " + e);
    }
}
