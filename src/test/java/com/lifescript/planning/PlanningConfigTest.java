package com.lifescript.planning;

import com.lifescript.scheduler.csp.ChocoScheduler;
import com.lifescript.scheduler.csp.HybridScheduler;
import com.lifescript.scheduler.greedy.GreedyScheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanningConfigTest {

    @Test
    void allThreeSchedulers_areAvailableByName() {
        Schedulers schedulers = PlanningConfig.schedulersFor(new SchedulerProperties("greedy", Duration.ofMillis(500)));

        assertEquals(List.of("greedy", "csp", "hybrid"), schedulers.names());
        assertInstanceOf(GreedyScheduler.class, schedulers.find("greedy"));
        assertInstanceOf(ChocoScheduler.class, schedulers.find("csp"));
        assertInstanceOf(HybridScheduler.class, schedulers.find("hybrid"));
    }

    @Test
    void theAlgorithmProperty_picksTheDefault() {
        Schedulers schedulers = PlanningConfig.schedulersFor(new SchedulerProperties("csp", Duration.ofMillis(500)));

        assertEquals("csp", schedulers.defaultName());
        assertInstanceOf(ChocoScheduler.class, schedulers.find(null));
        assertInstanceOf(ChocoScheduler.class, schedulers.find(" "));
    }

    @Test
    void withoutSettings_itIsGreedy_withTwoSeconds() {
        SchedulerProperties defaults = new SchedulerProperties(null, null);

        assertEquals("greedy", defaults.algorithm());
        assertEquals(Duration.ofSeconds(2), defaults.timeLimit());
    }

    @Test
    void anUnknownAlgorithm_stopsTheApp_withAClearMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PlanningConfig.schedulersFor(new SchedulerProperties("genetic", Duration.ofSeconds(1))));

        assertEquals("Unknown lifescript.scheduler.algorithm 'genetic' (use greedy, csp or hybrid)", e.getMessage());
    }

    @Test
    void anUnknownRequestedAlgorithm_namesTheKnownOnes() {
        Schedulers schedulers = PlanningConfig.schedulersFor(new SchedulerProperties(null, null));

        UnknownAlgorithmException e = assertThrows(UnknownAlgorithmException.class, () -> schedulers.find("genetic"));

        assertEquals("Unknown algorithm 'genetic' (use greedy, csp or hybrid)", e.getMessage());
    }
}
