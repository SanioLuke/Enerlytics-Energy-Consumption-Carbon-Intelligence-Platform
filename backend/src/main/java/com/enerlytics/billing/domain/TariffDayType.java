package com.enerlytics.billing.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Calendar classification a tariff rate window applies to, evaluated on the
 * local date in the tariff's timezone.
 */
public enum TariffDayType {
    ALL,
    WEEKDAY,
    WEEKEND;

    public boolean appliesTo(LocalDate date) {
        return switch (this) {
            case ALL -> true;
            case WEEKDAY -> {
                DayOfWeek dow = date.getDayOfWeek();
                yield dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            }
            case WEEKEND -> {
                DayOfWeek dow = date.getDayOfWeek();
                yield dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            }
        };
    }
}
