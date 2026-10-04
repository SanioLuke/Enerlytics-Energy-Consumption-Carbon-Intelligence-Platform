package com.enerlytics.alert.domain;

import java.math.BigDecimal;

public enum ComparisonOperator {
    GT, GTE, LT, LTE, EQ, NE;

    public boolean matches(BigDecimal left, BigDecimal right) {
        int cmp = left.compareTo(right);
        return switch (this) {
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
        };
    }
}
