package org.springframework.data.querydsl;

import be.heydari.lib.expressions.Disjunction;

import java.util.List;

public class ABACContext {

    private static ThreadLocal<List<Disjunction>> currentAbacContext = new InheritableThreadLocal<>();

    public static List<Disjunction> getCurrentAbacContext() {
        return currentAbacContext.get();
    }

    public static void setCurrentAbacContext(List<Disjunction> tenant) {
        currentAbacContext.set(tenant);
    }

    public static void clear() {
        currentAbacContext.set(null);
    }
}
