package com.lifescript.planning;

import com.lifescript.compiler.LifeScriptCompiler;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PlanningConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public Scheduler scheduler() {
        return new GreedyScheduler();
    }

    @Bean
    public LifeScriptCompiler lifeScriptCompiler() {
        return new LifeScriptCompiler();
    }
}
