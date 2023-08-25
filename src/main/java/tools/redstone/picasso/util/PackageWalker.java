package tools.redstone.picasso.util;

import tools.redstone.picasso.util.functional.ThrowingSupplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Iterates over a package listing the contained resources.
 */
public class PackageWalker {

    // A resource found in a package tree
    public record Resource(String path, String name, ThrowingSupplier<InputStream, IOException> inputStreamSupplier) {
        public static Resource fromOnlyPath(String path, ThrowingSupplier<InputStream, IOException> inputStreamSupplier) {
            String[] pSplit = path.split("/");
            return new Resource(path, pSplit[pSplit.length - 1], inputStreamSupplier);
        }

        // Get the name without an extension
        public String trimmedName() {
            int dot = name.lastIndexOf('.');
            if (dot == -1)
                return name;
            return name.substring(0, dot);
        }

        // Get the path without an extension
        public String trimmedPath() {
            int dot = path.lastIndexOf('.');
            if (dot == -1)
                return path;
            return path.substring(0, dot);
        }

        // Get the public class name
        public String publicPath() {
            return trimmedPath().replace('/', '.');
        }

        public InputStream open() {
            try {
                return inputStreamSupplier.get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to open input stream to " + this, e);
            }
        }

        @Override
        public String toString() {
            return "Resource(" + path + ")";
        }
    }

    private final Path codeSourcePath; // The path of the code source as provided by the protection domain of the owner class
    private final String pkg;          // The name of the package to walk and find resources in

    public PackageWalker(Class<?> owner, String pkg) {
        this.codeSourcePath = Path.of(owner.getProtectionDomain().getCodeSource()
                .getLocation().getPath().substring(1));
        this.pkg = pkg;
    }

    public Stream<Resource> findResources() {
        try {
            Stream<Resource> stream;

            // check type of code source
            if (Files.isDirectory(codeSourcePath)) {
                stream = Files.walk(codeSourcePath.resolve(pkg.replace('.', '/')))
                        .filter(path -> !Files.isDirectory(path))
                        .map(codeSourcePath::relativize) // relativize paths
                        .map(path -> new Resource(path.toString().replace('\\', '/'), path.getFileName().toString(),
                                () -> Files.newInputStream(path)));
            } else {
                // open jar/zip and find entries
                try (var zip = new ZipFile(codeSourcePath.toString())) {
                    stream = zip.stream().map(e -> Resource.fromOnlyPath(e.getName(), () -> zip.getInputStream(e)));
                }
            }

            return stream;
        } catch (Exception e) {
            throw new RuntimeException("Failed to walk package " + pkg, e);
        }
    }

}
