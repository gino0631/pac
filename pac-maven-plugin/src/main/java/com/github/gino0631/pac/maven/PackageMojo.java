package com.github.gino0631.pac.maven;

import com.github.gino0631.pac.FilePermissions;
import com.github.gino0631.pac.PackageBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractMojo {
    /**
     * The directory containing payload to install.
     */
    @Parameter(required = true)
    private File root;

    /**
     * Additional entries to be included in the package.
     */
    @Parameter
    private List<Entry> entries;

    /**
     * A special install script that is to be included in the package.
     */
    @Parameter
    private File installScript;

    /**
     * The directory where the package will be created.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File buildDirectory;

    /**
     * Output file name.
     */
    @Parameter
    private String outputFile;

    /**
     * The name of the package.
     * <p>
     * Valid characters are alphanumerics, and any of the following characters: {@code @ . _ + -}.
     * Additionally, names are not allowed to start with hyphens or dots.
     */
    @Parameter(defaultValue = "${project.artifactId}", required = true)
    private String packageName;

    /**
     * The version of the package (e.g., 2.7.1). The variable is not allowed to contain colons or hyphens.
     */
    @Parameter(defaultValue = "${project.artifact.selectedVersion.majorVersion}" +
            ".${project.artifact.selectedVersion.minorVersion}" +
            ".${project.artifact.selectedVersion.incrementalVersion}", required = true)
    private String packageVersion;

    /**
     * The release number specific to the Arch Linux release. The variable is not allowed to contain hyphens.
     */
    @Parameter(defaultValue = "1", required = true)
    private String releaseNumber;

    /**
     * Defines on which architecture the given package is available.
     */
    @Parameter(defaultValue = "any", required = true)
    private String architecture;

    /**
     * A brief description of the package and its functionality. Try to keep the description to one line of text and to not use the package’s name.
     */
    @Parameter
    private String description;

    /**
     * A URL that is associated with the software being packaged.
     */
    @Parameter(defaultValue = "${project.organization.url}")
    private URL url;

    /**
     * The builder of the package. It is recommended to change this to your name and email address, e.g., {@code John Doe <john@example.com>}.
     */
    @Parameter(defaultValue = "${project.organization.name}")
    private String packager;

    /**
     * The license(s) that apply to the package.
     */
    @Parameter
    private List<String> licenses;

    /**
     * A list of packages this package depends on to run.
     */
    @Parameter
    private List<String> depends;

    /**
     * A list of optional packages that provide additional features.
     */
    @Parameter
    private List<String> optDepends;

    /**
     * File permissions.
     */
    @Parameter
    private List<PermissionSet> permissionSets;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            final Path target = buildDirectory.toPath();

            PackageBuilder pkgBuilder = new PackageBuilder()
                    .setRootDir(root.toPath())
                    .setPkgName(packageName)
                    .setPkgVer(packageVersion)
                    .setPkgRel(releaseNumber)
                    .setPkgDesc(description)
                    .setUrl((url != null) ? url.toString() : null)
                    .setPackager(packager)
                    .setArch(architecture)
                    .addLicenses(licenses)
                    .addDepends(depends)
                    .addOptDepends(optDepends);

            if (permissionSets != null) {
                pkgBuilder.setPermissionSupplier((name, isDirectory) -> {
                    int mode = isDirectory ? FilePermissions.DEFAULT_DIRECTORY_MODE : FilePermissions.DEFAULT_FILE_MODE;
                    int uid = FilePermissions.DEFAULT_UID;
                    int gid = FilePermissions.DEFAULT_GID;

                    for (PermissionSet p : permissionSets) {
                        if (p.matches(name)) {
                            mode = notNull(isDirectory ? p.getDirectoryMode() : p.getFileMode(), mode);
                            uid = notNull(p.getUid(), uid);
                            gid = notNull(p.getGid(), gid);
                        }
                    }

                    return new FilePermissions(mode, uid, gid);
                });
            }

            if (entries != null) {
                for (Entry e : entries) {
                    if (e instanceof Symlink) {
                        Symlink symlink = (Symlink) e;
                        pkgBuilder.addSymlink(symlink.getName(), symlink.getLinkTo());
                    }
                }
            }

            if (installScript != null) {
                pkgBuilder.setInstallScript(installScript.toPath());
            }

            if (outputFile == null) {
                outputFile = packageName + "-" + packageVersion + "-" + releaseNumber + "-" + architecture + ".pkg.tar.xz";
            }

            Path outputPath = target.resolve(outputFile);
            Files.createDirectories(outputPath.getParent());
            boolean succeeded = false;

            try (OutputStream os = Files.newOutputStream(outputPath)) {
                pkgBuilder.build(os);
                succeeded = true;

            } finally {
                if (!succeeded) {
                    Files.deleteIfExists(outputPath);
                }
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Error building package", e);
        }
    }

    private static int notNull(Integer value, int defaultValue) {
        return (value != null) ? value : defaultValue;
    }
}
