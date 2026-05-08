package com.lifescript;
import com.lifescript.compiler.*;
import com.lifescript.compiler.Compiler;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: lifescript <file.ls>");
            return;
        }

        String filePath = args[0];

        Compiler compiler = new Compiler();
        compiler.compile(filePath);
    }
}