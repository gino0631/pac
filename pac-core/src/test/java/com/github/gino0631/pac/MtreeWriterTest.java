package com.github.gino0631.pac;

import org.junit.Test;

import java.io.StringWriter;
import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class MtreeWriterTest {
    @Test
    public void testWriteEntry() throws Exception {
        StringWriter sw = new StringWriter();

        MtreeWriter mtreeWriter = new MtreeWriter(sw)
                .writeHeader()
                .writeEntry("opt/testąš.txt", MtreeWriter.FileType.FILE, 123, Instant.parse("2016-06-29T22:20:30.999Z"),
                        FilePermissions.DEFAULT_FILE_MODE, 0, 0, null)
                .writeEntry("opt/lib.so", MtreeWriter.FileType.LINK, 0, Instant.parse("2016-06-29T22:20:30.999Z"),
                        FilePermissions.DEFAULT_LINK_MODE, 0, 0, "/opt/lib.so.0.0");

        String[] lines = sw.toString().split("\n");
        assertEquals(4, lines.length);
        assertEquals("#mtree", lines[0]);
        assertEquals("./opt/test\\304\\205\\305\\241.txt time=1467238830.999000000 size=123", lines[2]);
        assertEquals("./opt/lib.so time=1467238830.999000000 mode=777 type=link link=/opt/lib.so.0.0", lines[3]);
    }
}
