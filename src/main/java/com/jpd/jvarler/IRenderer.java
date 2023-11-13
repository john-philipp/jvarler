package com.jpd.jvarler;

/** Renderer interface. */
public interface IRenderer<T> {

    /** Render. */
    void render();

    /** Get output (if any). */
    T getOutput();
}
