package com.github.gino0631.pac;

import org.apache.commons.compress.archivers.zip.UnixStat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class MtreeWriter {
    private final Appendable appendable;

    public enum FileType {
        BLOCK,
        CHAR,
        DIR,
        FIFO,
        FILE,
        LINK,
        SOCKET;

        String getCode() {
            return name().toLowerCase();
        }
    }

    MtreeWriter(Appendable appendable) {
        this.appendable = appendable;
    }

    MtreeWriter writeHeader() throws IOException {
        appendable.append("#mtree\n");
        appendable.append("/set type=file uid=0 gid=0 mode=644\n");

        return this;
    }

    MtreeWriter writeEntry(String name, FileType fileType, long size, Instant lastModified,
                           int mode, long uid, long gid, String link) throws IOException {
        return writeEntry(name, fileType, size, lastModified, mode, uid, gid, link, null, null);
    }

    MtreeWriter writeEntry(String name, FileType fileType, long size, Instant lastModified,
                           int mode, long uid, long gid, String link,
                           String md5digest, String sha256digest) throws IOException {
        appendable.append("./");
        writeFilename(name);

        appendable.append(" time=").append(Long.toString(lastModified.getEpochSecond())).append('.').append(Integer.toString(lastModified.getNano()));

        if (mode != UnixStat.DEFAULT_FILE_PERM) {
            appendable.append(" mode=");
            writeOctal(appendable, mode);
        }

        if ((uid != 0) || (gid != 0)) {
            write(" uid=", uid);
            write(" gid=", gid);
        }

        if (fileType == FileType.FILE) {
            write(" size=", size);

        } else {
            appendable.append(" type=").append(fileType.getCode());
        }

        if ((link != null) && !link.isEmpty()) {
            write(" link=", link);
        }

        if (md5digest != null) {
            write(" md5digest=", md5digest);
        }

        if (sha256digest != null) {
            write(" sha256digest=", md5digest);
        }

        appendable.append('\n');

        return this;
    }

    private void write(String name, long value) throws IOException {
        write(name, Long.toString(value));
    }

    private void write(String name, String value) throws IOException {
        appendable.append(name).append(value);
    }

    private void writeFilename(String filename) throws IOException {
        StringBuilder sb = new StringBuilder(filename.length() + 16);
        int charCount;

        for (int i = 0; i < filename.length(); i += charCount) {
            int cp = filename.codePointAt(i);
            charCount = Character.charCount(cp);

            if ((charCount == 1) && (0x20 <= cp) && (cp <= 0x7E) && (cp != '\\')) {
                sb.append((char) cp);

            } else {
                ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(filename, i, i + charCount));
                while (buf.hasRemaining()) {
                    writeEscaped(sb, buf.get());
                }
            }
        }

        int end;
        for (end = sb.length(); end > 0; end--) {
            if (sb.charAt(end - 1) != '/') {
                break;
            }
        }

        appendable.append(sb, 0, end);
    }

    private static void writeEscaped(Appendable appendable, byte b) throws IOException {
        appendable.append('\\');
        writeOctal(appendable, b & 0xff);
    }

    private static void writeOctal(Appendable appendable, int b) throws IOException {
        String s = Integer.toOctalString(b);

        for (int i = 0; i < 3 - s.length(); i++) {
            appendable.append('0');
        }

        appendable.append(s);
    }
}
