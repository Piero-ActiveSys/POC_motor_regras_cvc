package br.com.cvc.poc.contracts;

import java.io.IOException;
import java.util.Optional;

/**
 * Abstraction for reading/writing versioned ruleset artifacts.
 * Current implementation: local filesystem.
 * Future implementation: S3 / MinIO.
 */
public interface ArtifactStorageService {

    /**
     * Write an artifact file for a specific ruleset version.
     *
     * @param rulesetId the ruleset identifier
     * @param version   the version number
     * @param fileName  the file name (e.g. "ruleset_v3.drl")
     * @param content   the file content
     * @throws IOException if writing fails
     */
    void writeArtifact(String rulesetId, long version, String fileName, String content) throws IOException;

    /**
     * Read an artifact file for a specific ruleset version.
     *
     * @param rulesetId the ruleset identifier
     * @param version   the version number
     * @param fileName  the file name
     * @return the file content, or empty if not found
     * @throws IOException if reading fails
     */
    Optional<String> readArtifact(String rulesetId, long version, String fileName) throws IOException;

    /**
     * Read an artifact by its full relative path (e.g. from manifestPath).
     *
     * @param relativePath relative path from storage root
     * @return the file content, or empty if not found
     * @throws IOException if reading fails
     */
    Optional<String> readByPath(String relativePath) throws IOException;

    /**
     * Ensure the directory structure exists for a given ruleset and version.
     *
     * @param rulesetId the ruleset identifier
     * @param version   the version number
     * @throws IOException if creation fails
     */
    void ensureDirectory(String rulesetId, long version) throws IOException;

    /**
     * Resolve the relative manifest path for a given ruleset and version.
     *
     * @param rulesetId the ruleset identifier
     * @param version   the version number
     * @return the relative path to the manifest file
     */
    String resolveManifestPath(String rulesetId, long version);

    /**
     * Check if an artifact exists.
     *
     * @param rulesetId the ruleset identifier
     * @param version   the version number
     * @param fileName  the file name
     * @return true if the file exists
     */
    boolean exists(String rulesetId, long version, String fileName);
}

