package org.example.plugin;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;


final class StarterBookClaims {
    private final Path directory;

    StarterBookClaims(Path universeDirectory) {
        this(universeDirectory, "starter-books");
    }

    StarterBookClaims(Path universeDirectory, String claimDirectory) {
        directory = universeDirectory.resolve("axotales").resolve(claimDirectory);
    }

    boolean isClaimed(UUID playerUuid) {
        return Files.exists(claimPath(playerUuid));
    }


    boolean reserve(UUID playerUuid) throws IOException {
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(claimPath(playerUuid),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.force(true);
            return true;
        } catch (FileAlreadyExistsException alreadyClaimed) {
            return false;
        }
    }


    void release(UUID playerUuid) throws IOException {
        Files.deleteIfExists(claimPath(playerUuid));
    }

    private Path claimPath(UUID playerUuid) {
        return directory.resolve(playerUuid + ".claim");
    }
}
