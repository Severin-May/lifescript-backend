package com.lifescript.planning;

import com.lifescript.compiler.LifeScriptCompiler;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.SchedulerSettings;
import com.lifescript.scheduler.csp.ChocoScheduler;
import com.lifescript.scheduler.csp.HybridScheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(SchedulerProperties.class)
public class PlanningConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public Schedulers schedulers(SchedulerProperties properties) {
        return schedulersFor(properties);
    }

    static Schedulers schedulersFor(SchedulerProperties properties) {
        Map<String, Scheduler> byName = new LinkedHashMap<>();
        byName.put("greedy", new GreedyScheduler());
        byName.put("csp", new ChocoScheduler(SchedulerSettings.DEFAULTS, properties.timeLimit()));
        byName.put("hybrid", new HybridScheduler(SchedulerSettings.DEFAULTS, properties.timeLimit()));
        if (!byName.containsKey(properties.algorithm())) {
            throw new IllegalArgumentException(String.format(
                    "Unknown lifescript.scheduler.algorithm '%s' (use greedy, csp or hybrid)", properties.algorithm()));
        }
        return new Schedulers(byName, properties.algorithm());
    }

    @Bean
    public LifeScriptCompiler lifeScriptCompiler() {
        return new LifeScriptCompiler();
    }
}
