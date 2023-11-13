package com.jpd.utils;

/** Some static helpers. */
public class Helpers {

    /** Hidden utility constructor. */
    private Helpers() {
    }

    /** Producer interface. */
    @FunctionalInterface
    public interface IProducer<T> {
        T get();
    }
}
