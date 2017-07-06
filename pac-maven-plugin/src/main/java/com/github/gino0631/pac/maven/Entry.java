package com.github.gino0631.pac.maven;

import org.apache.maven.plugins.annotations.Parameter;

public abstract class Entry {
    @Parameter
    private String name;

    public String getName() {
        return name;
    }
}
