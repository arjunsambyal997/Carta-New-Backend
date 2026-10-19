package com.equigcc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Packages are organised by feature, not by layer: a feature's entities, repositories, DTOs, services and
 * controller live together. This keeps a new "util" or "dto" package from creeping back in.
 */
class PackageStructureTest {

    private static final Path MAIN = Path.of("src/main/java/com/equigcc");
    private static final Set<String> LAYER_NAMES =
            Set.of("entity", "domain", "dto", "service", "repository", "controller", "util", "model");

    @Test
    void noLayerPackages() throws IOException {
        try (Stream<Path> dirs = Files.walk(MAIN)) {
            List<String> offenders = dirs.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(LAYER_NAMES::contains)
                    .toList();
            assertThat(offenders).as("layer-named packages").isEmpty();
        }
    }

    @Test
    void everyJavaFileSitsInAFeaturePackageOrTheRoot() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            List<String> nested = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> MAIN.relativize(p).getNameCount() > 2)
                    .map(p -> MAIN.relativize(p).toString())
                    .toList();
            assertThat(nested).as("files nested deeper than <feature>/<File>.java").isEmpty();
        }
    }

    @Test
    void entitiesAndRepositoriesLiveInsideAFeature() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            List<String> stray = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> MAIN.relativize(p).getNameCount() == 1)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> !n.equals("EquiGccApplication.java"))
                    .toList();
            assertThat(stray).isEmpty();
        }
    }
}
