package com.github.gino0631.pac.maven;

import org.apache.maven.plugins.annotations.Parameter;

public final class Symlink extends Entry {
    @Parameter
    private String linkTo;

    public String getLinkTo() {
        return linkTo;
    }
}
