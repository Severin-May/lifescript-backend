package com.lifescript;

import com.lifescript.api.CompilerController;
import com.lifescript.planning.PlanningService;
import com.lifescript.scheduler.Scheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import(TestDatabase.class)
@SpringBootTest
class MainTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextStarts_andWiresThePlanningBeans() {
        assertNotNull(context.getBean(CompilerController.class));
        assertNotNull(context.getBean(PlanningService.class));
        assertInstanceOf(GreedyScheduler.class, context.getBean(Scheduler.class));
    }
}
