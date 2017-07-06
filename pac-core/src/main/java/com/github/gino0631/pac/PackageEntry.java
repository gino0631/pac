package com.github.gino0631.pac;

import com.github.gino0631.common.io.InputStreamSupplier;
import com.github.gino0631.common.io.IoStreams;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

final class PackageEntry extends TarArchiveEntry {
    private static final String MD5_DIGEST = "MD5";
    private static final String SHA256_DIGEST = "SHA-256";
    private static final char[] HEX_CODE = "0123456789abcdef".toCharArray();

    private final Instant lastModified;
    private final InputStreamSupplier streamSupplier;
    private final String md5digest;
    private final String sha256digest;

    PackageEntry(Path path, String fileName, PackageBuilder.PermissionSupplier permissionSupplier) throws IOException {
        super(path.toFile(), fileName);

        int mode = isDirectory() ? FilePermissions.DEFAULT_DIRECTORY_MODE : FilePermissions.DEFAULT_FILE_MODE;
        int uid = FilePermissions.DEFAULT_UID;
        int gid = FilePermissions.DEFAULT_GID;

        if (permissionSupplier != null) {
            FilePermissions permissions = permissionSupplier.get(fileName, isDirectory());
            if (permissions != null) {
                mode = permissions.getMode();
                uid = permissions.getUserId();
                gid = permissions.getGroupId();
            }
        }

        mode |= (isDirectory() ? UnixStat.DIR_FLAG : UnixStat.FILE_FLAG);

        setMode(mode);
        setIds(uid, gid);
        setNames(FilePermissions.DEFAULT_USER_NAME, FilePermissions.DEFAULT_GROUP_NAME);

        lastModified = Files.getLastModifiedTime(path).toInstant();

        if (isFile()) {
            streamSupplier = InputStreamSupplier.of(path);

            MessageDigest md5 = getMessageDigest(MD5_DIGEST);
            MessageDigest sha256 = getMessageDigest(SHA256_DIGEST);
            updateDigests(md5, sha256);

            md5digest = toHexString(md5.digest());
            sha256digest = toHexString(sha256.digest());

        } else {
            streamSupplier = null;
            md5digest = null;
            sha256digest = null;
        }
    }

    PackageEntry(String name, byte[] data) {
        super(name, LF_NORMAL);

        setSize(data.length);
        setMode(FilePermissions.DEFAULT_FILE_MODE | UnixStat.FILE_FLAG);
        setNames(FilePermissions.DEFAULT_USER_NAME, FilePermissions.DEFAULT_GROUP_NAME);

        lastModified = Instant.now();
        streamSupplier = () -> new ByteArrayInputStream(data);

        MessageDigest md5 = getMessageDigest(MD5_DIGEST);
        MessageDigest sha256 = getMessageDigest(SHA256_DIGEST);

        try {
            updateDigests(md5, sha256);

        } catch (IOException e) {
            throw new RuntimeException(e);  // unlikely, as we are working with byte arrays here
        }

        md5digest = toHexString(md5.digest());
        sha256digest = toHexString(sha256.digest());
    }

    PackageEntry(String name, String linkName) {
        super(name, LF_SYMLINK);

        setLinkName(linkName);
        setMode(FilePermissions.DEFAULT_LINK_MODE | UnixStat.LINK_FLAG);
        setNames(FilePermissions.DEFAULT_USER_NAME, FilePermissions.DEFAULT_GROUP_NAME);

        lastModified = Instant.now();
        streamSupplier = null;
        md5digest = null;
        sha256digest = null;
    }

    PackageEntry(String name) {
        super(name.endsWith("/") ? name : name + "/", LF_DIR);

        setMode(FilePermissions.DEFAULT_DIRECTORY_MODE | UnixStat.DIR_FLAG);
        setNames(FilePermissions.DEFAULT_USER_NAME, FilePermissions.DEFAULT_GROUP_NAME);

        lastModified = Instant.now();
        streamSupplier = null;
        md5digest = null;
        sha256digest = null;
    }

    void writeTo(MtreeWriter mtreeWriter) throws IOException {
        mtreeWriter.writeEntry(getName(), getFileType(), getSize(), lastModified,
                getMode() & FilePermissions.MODE_MASK, getLongUserId(), getLongGroupId(), getLinkName(),
                md5digest, sha256digest);
    }

    void writeTo(TarArchiveOutputStream tar) throws IOException {
        tar.putArchiveEntry(this);

        if (streamSupplier != null) {
            try (InputStream in = streamSupplier.newInputStream()) {
                IoStreams.copy(in, tar);
            }
        }

        tar.closeArchiveEntry();
    }

    private void updateDigests(MessageDigest md5, MessageDigest sha256) throws IOException {
        try (InputStream is = new DigestInputStream(new DigestInputStream(streamSupplier.newInputStream(), md5), sha256)) {
            IoStreams.exhaust(is);
        }
    }

    private MtreeWriter.FileType getFileType() {
        if (isDirectory()) {
            return MtreeWriter.FileType.DIR;

        } else if (isSymbolicLink()) {
            return MtreeWriter.FileType.LINK;

        } else if (isFile()) {
            return MtreeWriter.FileType.FILE;   // should be the last, as other types might get incorrectly detected as files

        } else {
            throw new IllegalArgumentException("Unsupported file type");
        }
    }

    private static MessageDigest getMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHexString(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(HEX_CODE[(b >> 4) & 0xf]);
            r.append(HEX_CODE[(b & 0xf)]);
        }

        return r.toString();
    }
}
