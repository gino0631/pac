package com.github.gino0631.pac;

import com.github.gino0631.common.io.IoStreams;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public final class PackageBuilder {
    private Path rootDir;
    private Path installScript;
    private String pkgName;
    private String pkgVer;
    private String pkgRel;
    private String pkgDesc;
    private String url;
    private String packager;
    private String arch;
    private PermissionSupplier permissionSupplier;
    private Map<String, String> symlinks = new HashMap<>();
    private List<String> licenses = new ArrayList<>();
    private List<String> depends = new ArrayList<>();

    @FunctionalInterface
    public interface PermissionSupplier {
        FilePermissions get(String name, boolean isDirectory);
    }

    public PackageBuilder setRootDir(Path rootDir) {
        this.rootDir = rootDir;
        return this;
    }

    public PackageBuilder setInstallScript(Path installScript) {
        this.installScript = installScript;
        return this;
    }

    public PackageBuilder setPkgName(String pkgName) {
        this.pkgName = pkgName;
        return this;
    }

    public PackageBuilder setPkgVer(String pkgVer) {
        this.pkgVer = pkgVer;
        return this;
    }

    public PackageBuilder setPkgRel(String pkgRel) {
        this.pkgRel = pkgRel;
        return this;
    }

    public PackageBuilder setPkgDesc(String pkgDesc) {
        this.pkgDesc = pkgDesc;
        return this;
    }

    public PackageBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    public PackageBuilder setPackager(String packager) {
        this.packager = packager;
        return this;
    }

    public PackageBuilder setArch(String arch) {
        this.arch = arch;
        return this;
    }

    public PackageBuilder setPermissionSupplier(PermissionSupplier permissionSupplier) {
        this.permissionSupplier = permissionSupplier;
        return this;
    }

    public PackageBuilder addSymlink(String name, String linkTo) {
        symlinks.put(name, linkTo);
        return this;
    }

    public PackageBuilder addLicense(String name) {
        licenses.add(name);
        return this;
    }

    public PackageBuilder addLicenses(Collection<String> names) {
        if (names != null) {
            licenses.addAll(names);
        }
        return this;
    }

    public PackageBuilder addDepend(String nameVersion) {
        depends.add(nameVersion);
        return this;
    }

    public PackageBuilder addDepends(Collection<String> nameVersions) {
        if (nameVersions != null) {
            depends.addAll(nameVersions);
        }
        return this;
    }

    public void build(OutputStream outputStream) throws IOException {
        // Validation
        Objects.requireNonNull(rootDir, "Root directory must be specified");
        if (Files.notExists(rootDir)) {
            throw new IllegalArgumentException(MessageFormat.format("Root directory {0} does not exist", rootDir));
        }

        Objects.requireNonNull(pkgName, "Package name must be specified");

        Objects.requireNonNull(pkgVer, "Package version must be specified");
        if (pkgVer.contains(":") || pkgVer.contains("-")) {
            throw new IllegalArgumentException("Package version is not allowed to contain colons or hyphens");
        }

        Objects.requireNonNull(pkgRel, "Release number must be specified");
        if (pkgRel.contains("-")) {
            throw new IllegalArgumentException("Release number is not allowed to contain hyphens");
        }

        Objects.requireNonNull(arch, "Target architecture must be specified");

        // Creation
        final int rootPathLength = rootDir.toString().length() + 1;
        long installBytes = 0;

        List<Path> files = Files.walk(rootDir).skip(1).collect(Collectors.toList());
        NavigableMap<String, PackageEntry> entries = new TreeMap<>();

        // Process payload
        for (Path path : files) {
            final String name = path.toString().substring(rootPathLength).replace('\\', '/');
            final boolean isDirectory = Files.isDirectory(path);
            final long size = isDirectory ? 0 : Files.size(path);
            installBytes += size;

            PackageEntry entry = new PackageEntry(path, name, permissionSupplier);
            entries.put(entry.getName(), entry);
        }

        // Process symlinks
        for (Map.Entry<String, String> e : symlinks.entrySet()) {
            PackageEntry entry = new PackageEntry(e.getKey(), e.getValue());
            if (entries.putIfAbsent(entry.getName(), entry) != null) {
                throw new IllegalArgumentException(MessageFormat.format("Duplicate path {0}", entry.getName()));
            }

            addParentDirs(entries, entry);
        }

        // Add install script
        if (installScript != null) {
            PackageEntry install = new PackageEntry(installScript, ".INSTALL", null);
            entries.put(install.getName(), install);
        }

        // Write .PKGINFO
        {
            ByteArrayOutputStream pkginfoOs = new ByteArrayOutputStream(4096);
            try (Writer wr = new OutputStreamWriter(pkginfoOs, StandardCharsets.UTF_8)) {
                writePkginfoEntry(wr, "pkgname", pkgName);
                writePkginfoEntry(wr, "pkgver", pkgVer + "-" + pkgRel);
                writePkginfoEntry(wr, "pkgdesc", pkgDesc, true);
                writePkginfoEntry(wr, "url", url);
                writePkginfoEntry(wr, "builddate", Long.toString(Instant.now().getEpochSecond()));
                writePkginfoEntry(wr, "packager", packager);
                writePkginfoEntry(wr, "size", Long.toString(installBytes));
                writePkginfoEntry(wr, "arch", arch);

                for (String license : licenses) {
                    writePkginfoEntry(wr, "license", license);
                }

                for (String depend : depends) {
                    writePkginfoEntry(wr, "depend", depend);
                }
            }
            PackageEntry pkginfo = new PackageEntry(".PKGINFO", pkginfoOs.toByteArray());
            entries.put(pkginfo.getName(), pkginfo);
        }

        // Write .MTREE
        {
            ByteArrayOutputStream mtreeOs = new ByteArrayOutputStream(4096);
            try (Writer wr = new OutputStreamWriter(new GZIPOutputStream(mtreeOs), StandardCharsets.UTF_8)) {
                MtreeWriter mtreeWriter = new MtreeWriter(wr).writeHeader();
                for (PackageEntry entry : entries.values()) {
                    entry.writeTo(mtreeWriter);
                }
            }
            PackageEntry mtree = new PackageEntry(".MTREE", mtreeOs.toByteArray());
            entries.put(mtree.getName(), mtree);
        }

        // Write .PKG.TAR.XZ
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new XZCompressorOutputStream(IoStreams.closeProtect(outputStream)))) {
            for (PackageEntry entry : entries.values()) {
                entry.writeTo(tar);
            }
        }
    }

    private static void addParentDirs(Map<String, PackageEntry> entries, PackageEntry entry) {
        Path path = Paths.get(entry.getName());

        while ((path = path.getParent()) != null) {
            PackageEntry dir = new PackageEntry(path.toString());
            entries.putIfAbsent(dir.getName(), dir);
        }
    }

    private static void writePkginfoEntry(Writer writer, String key, String value) throws IOException {
        writePkginfoEntry(writer, key, value, false);
    }

    private static void writePkginfoEntry(Writer writer, String key, String value, boolean emptyIfNull) throws IOException {
        if (value == null) {
            if (emptyIfNull) {
                value = "";

            } else {
                return;
            }
        }

        writer.write(key);
        writer.write(" = ");
        writer.write(value);
        writer.write('\n');
    }
}
