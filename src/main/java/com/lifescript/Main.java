package com.lifescript;
import com.lifescript.compiler.*;
import com.lifescript.compiler.Compiler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class Main {
//    public static void main(String[] args) {
//        if (args.length == 0) {
//            System.err.println("File input is missing!");
//            return;
//        }
//
//        String filePath = args[0];
//
//        Compiler compiler = new Compiler();
//        compiler.compile(filePath);
//    }

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}