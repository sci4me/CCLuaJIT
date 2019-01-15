package com.sci.cclj.computer;

public interface IComputer {
    void queueEvent(final String event, final Object[] arguments);
}