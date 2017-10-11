package com.cronutils.model.time;

import static org.junit.Assert.*;
import static org.threeten.bp.ZoneOffset.UTC;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.definition.TestCronDefinitionsFactory;
import com.cronutils.parser.CronParser;
import org.junit.Before;
import org.junit.Test;
import org.threeten.bp.*;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*
 * Copyright 2015 jmrozanec
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ExecutionTimeQuartzWithDayOfYearExtensionIntegrationTest {
    private static final String BI_WEEKLY_STARTING_WITH_FIRST_DAY_OF_YEAR = "0 0 0 ? * ? * 1/14";
    private static final String FIRST_QUATER_BI_WEEKLY_STARTING_WITH_FIRST_DAY_OF_YEAR = "0 0 0 ? 1-3 ? * 1/14";
    private static final String WITHOUT_DAY_OF_YEAR = "0 0 0 1 * ? *"; // i.e. DoY field omitted
    private static final String WITHOUT_SPECIFIC_DAY_OF_YEAR = "0 0 0 1 * ? * ?"; // i.e. DoY field set to question mark
    
    
    private CronParser parser;
    private CronParser quartzParser;

    @Before
    public void setUp() throws Exception {
        parser = new CronParser(TestCronDefinitionsFactory.withDayOfYearDefinitionWhereYearAndDoYOptionals());
        quartzParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
    }

    @Test
    public void testForCron() {
        assertEquals(ExecutionTime.class, ExecutionTime.forCron(parser.parse(BI_WEEKLY_STARTING_WITH_FIRST_DAY_OF_YEAR)).getClass());
        assertEquals(ExecutionTime.class, ExecutionTime.forCron(parser.parse(FIRST_QUATER_BI_WEEKLY_STARTING_WITH_FIRST_DAY_OF_YEAR)).getClass());
        assertEquals(ExecutionTime.class, ExecutionTime.forCron(parser.parse(WITHOUT_DAY_OF_YEAR)).getClass());
        assertEquals(ExecutionTime.class, ExecutionTime.forCron(parser.parse(WITHOUT_SPECIFIC_DAY_OF_YEAR)).getClass());
    }

    /**
     * Gets the expected execution times for a biweekly schedule starting from the first day of the year at midnight
     * @param year The year to get the execution times for
     * @param zoneId The time zone ID
     * @return The expected execution times
     */
    private static List<ZonedDateTime> getBiweeklyExpectedExecutionTimesOfYear(int year, ZoneId zoneId){
        List<ZonedDateTime> executionTimes = new ArrayList<>();
        // Starting from the first day of the year
        LocalDate startDate = LocalDate.of(year, 1, 1);
        ZonedDateTime beginning = ZonedDateTime.of(startDate, LocalTime.MIN, zoneId);
        // Add all execution times for the year
        for (int week = 0; week <= 52; week += 2) {
            executionTimes.add(beginning.plusWeeks(week));
        }
        return executionTimes;
    }

    @Test
    public void testNextExecutionEveryTwoWeeksStartingWithFirstDayOfYear() {
        final int START_YEAR = 2017;
        final ZoneId TIME_ZONE_ID = ZoneId.systemDefault();
        List<ZonedDateTime> expectedExecutionTimes = getBiweeklyExpectedExecutionTimesOfYear(START_YEAR, TIME_ZONE_ID);

        LocalDate startDate = LocalDate.of(START_YEAR, 1, 1);
        ZonedDateTime beginning = ZonedDateTime.of(startDate, LocalTime.MIN, TIME_ZONE_ID);

        // Add first execution time of the next year - Jan 1.
        // This should be the result if nextExecution is called on/after the last execution of this year
        expectedExecutionTimes.add(ZonedDateTime.of(START_YEAR + 1, 1, 1, 0, 0, 0, 0, TIME_ZONE_ID));

        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(BI_WEEKLY_STARTING_WITH_FIRST_DAY_OF_YEAR));

        Iterator<ZonedDateTime> iterator = expectedExecutionTimes.iterator();
        ZonedDateTime expected = iterator.next();
        // Check the first execution time - Jan. 1
        assert(executionTime.nextExecution(beginning.minusDays(1)).isPresent());
        assertEquals(expected, executionTime.nextExecution(beginning.minusDays(1)).get());

        // Check next execution times for all days in the year
        for(int dayOfYear = 1; dayOfYear <= startDate.lengthOfYear(); dayOfYear++){
            ZonedDateTime dayToTest = ZonedDateTime.of(LocalDate.ofYearDay(START_YEAR, dayOfYear), LocalTime.MIN, TIME_ZONE_ID);

            // every 2 weeks take the next execution time
            if (dayOfYear % 14 == 1 && iterator.hasNext()){
                expected = iterator.next();
            }
            assert (executionTime.nextExecution(dayToTest).isPresent());
            assertEquals(expected, executionTime.nextExecution(dayToTest).get());
        }
    }

    @Test
    public void testLastExecutionEveryTwoWeeksStartingWithFirstDayOfYearIssue() {
        final int START_YEAR = 2017;
        final ZoneId TIME_ZONE_ID = ZoneId.systemDefault();
        List<ZonedDateTime> expectedExecutionTimes = getBiweeklyExpectedExecutionTimesOfYear(START_YEAR, TIME_ZONE_ID);

        // Add the last expected execution of the previous year - always the 365th day (52*7 = 364)
        ZonedDateTime lastExecutionOfPreviousYear =
                ZonedDateTime.of(LocalDate.ofYearDay(START_YEAR - 1, 365),
                        LocalTime.MIN,
                        TIME_ZONE_ID);
        expectedExecutionTimes.add(0, lastExecutionOfPreviousYear);

        // Bi-weekly starting from Jan. 1
        LocalDate startDate = LocalDate.of(START_YEAR, 1, 1);
        ZonedDateTime beginning = ZonedDateTime.of(startDate, LocalTime.MIN, TIME_ZONE_ID);
        // Add all execution times for the year
        for (int week = 0; week <= 52; week += 2) {
            expectedExecutionTimes.add(beginning.plusWeeks(week));
        }

        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(BI_WEEKLY_STARTING_WITH_FIRST_DAY_OF_YEAR));

        // Check last execution times for all days in the year
        Iterator<ZonedDateTime> iterator = expectedExecutionTimes.iterator();
        ZonedDateTime expected = iterator.next();
        for(int dayOfYear = 1; dayOfYear <= startDate.lengthOfYear(); dayOfYear++){
            ZonedDateTime dayToTest = ZonedDateTime.of(LocalDate.ofYearDay(START_YEAR, dayOfYear), LocalTime.MIN, TIME_ZONE_ID);
            assert (executionTime.lastExecution(dayToTest).isPresent());
            assertEquals(expected, executionTime.lastExecution(dayToTest).get());

            // every 2 weeks take the next execution time
            if (dayOfYear % 14 == 1 && iterator.hasNext()){
                expected = iterator.next();
            }
        }
    }
    
    @Test
    public void testExecutionTimesEveryTwoWeeksStartingWithFirstDayOfYear() {
        ZonedDateTime[] expectedExecutionTimes = new ZonedDateTime[]{
            ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 1, 15, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 1, 29, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 2, 12, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 2, 26, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 3, 12, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 3, 26, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 4, 9, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 4, 23, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 5, 7, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 5, 21, 0, 0, 0, 0, UTC),
            ZonedDateTime.of(2017, 6, 4, 0, 0, 0, 0, UTC)
        };
        
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(BI_WEEKLY_STARTING_WITH_FIRST_DAY_OF_YEAR));
        
        for (ZonedDateTime expectedExecutionTime : expectedExecutionTimes)
          assertEquals(expectedExecutionTime, executionTime.nextExecution(expectedExecutionTime.minusDays(1)).get());
        
        for (int i = 1; i < expectedExecutionTimes.length; i++)
            assertEquals(expectedExecutionTimes[i], executionTime.nextExecution(expectedExecutionTimes[i-1]).get());
        
        for (int i = 1; i < expectedExecutionTimes.length; i++)
            assertEquals(expectedExecutionTimes[i-1], executionTime.lastExecution(expectedExecutionTimes[i]).get());
    }
    
    @Test //issue #188
    public void testQuartzCompatibilityIfDoYisOmitted() {
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(WITHOUT_DAY_OF_YEAR));
        ExecutionTime quartzExecutionTime = ExecutionTime.forCron(quartzParser.parse(WITHOUT_DAY_OF_YEAR));
        
        ZonedDateTime start = ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, UTC).minusSeconds(1); 
        for (int i = 0; i < 12; i++) {
            ZonedDateTime expectedDateTime = quartzExecutionTime.nextExecution(start).get();
            assertEquals(quartzExecutionTime.nextExecution(start).get(), executionTime.nextExecution(start).get());
            start = expectedDateTime.plusSeconds(1);
        }
    }
    
    @Test //issue #188
    public void testQuartzCompatibilityIfDoYisQuestionMark() {
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(WITHOUT_SPECIFIC_DAY_OF_YEAR));
        ExecutionTime quartzExecutionTime = ExecutionTime.forCron(quartzParser.parse(WITHOUT_DAY_OF_YEAR));
        
        ZonedDateTime start = ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, UTC).minusSeconds(1); 
        for (int i = 0; i < 12; i++) {
            ZonedDateTime expectedDateTime = quartzExecutionTime.nextExecution(start).get();
            assertEquals(quartzExecutionTime.nextExecution(start).get(), executionTime.nextExecution(start).get());
            start = expectedDateTime.plusSeconds(1);
        }
    }
    
    @Test //issue #190
    public void testExecutionTimesWithIncrementsGreaterThanDaysOfMonth() {
        final int increment = 56;
        final String incrementGreaterDaysOfMonthStartingWithFirstDayOfYear = "0 0 0 ? * ? * 1/" + String.valueOf(increment);
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(incrementGreaterDaysOfMonthStartingWithFirstDayOfYear));
        ZonedDateTime start = ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, UTC); 
        for (int i = 0; i < 6; i++) {
            ZonedDateTime expected = start;
            ZonedDateTime actual = executionTime.nextExecution(start.minusSeconds(1)).get();
            assertEquals(expected, actual);
            start = expected.plusDays(increment);
        }
    }
    
    private static ZonedDateTime truncateToDays(ZonedDateTime dateTime){
        return dateTime.truncatedTo(ChronoUnit.DAYS);
    }
}
