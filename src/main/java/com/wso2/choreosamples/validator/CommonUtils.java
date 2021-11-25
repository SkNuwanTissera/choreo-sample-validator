package com.wso2.choreosamples.validator;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.wso2.choreosamples.validator.Constants.*;
import static com.wso2.choreosamples.validator.Constants.PACKAGE;

public class CommonUtils {
    /**
     * Validate the existence of documentation related files.
     *
     * @param packagePath path to Ballerina package
     * @throws BuildPrecheckException if failed to pass the prechecks
     */
    public static void executePrechecks(String packagePath) throws BuildPrecheckException {
        validateMdFiles(packagePath);
    }

    private static void validateMdFiles(String packagePath) throws BuildPrecheckException {
        String packageMdFilePath = packagePath + File.separator + PACKAGE_MD;
        File packageMdFile = new File(packageMdFilePath);

        if (!packageMdFile.exists()) {
            throw new BuildPrecheckException(PACKAGE_MD + " doesn't exist in package " + packagePath);
        } else if (packageMdFile.length() == 0) {
            throw new BuildPrecheckException(PACKAGE_MD + " is empty in package " + packagePath);
        }
    }

    /**
     * Generate MD5 hash for connector package
     *
     * @param directoryPath Path to the connector package
     * @return Hashed string
     * @throws IOException On an error while reading files
     */
    public static String generateHashString(String directoryPath) throws IOException {
        File dirToHash = new File(directoryPath);
        assert (dirToHash.isDirectory());
        Vector<FileInputStream> fileStreams = new Vector<>();
        collectInputStreams(dirToHash, fileStreams);
        SequenceInputStream seqStream = new SequenceInputStream(fileStreams.elements());
        String md5Hash = DigestUtils.md5Hex(seqStream);
        seqStream.close();
        return md5Hash;
    }

    private static void collectInputStreams(File dir, List<FileInputStream> foundStreams) throws FileNotFoundException {
        File[] fileList = dir.listFiles();
        assert fileList != null;
        // Sort by name - This ensures reproducible sorting mechanism.
        Arrays.sort(fileList, Comparator.comparing(File::getName));
        for (File f : fileList) {
            if (!f.getName().startsWith(".") && !f.getName().startsWith("target") && !f.getName().equals("Dependencies.toml")) {
                if (f.isDirectory()) {
                    collectInputStreams(f, foundStreams);
                } else {
                    foundStreams.add(new FileInputStream(f));
                }
            }
        }
    }

    /**
     * Increment the TOML file version of the given package
     *
     * @param packagePath Ballerina package path
     * @throws IOException        if an error occurred while reading/writing the toml file in the given path
     * @throws ValidatorException if version of the Ballerina.toml file is invalid
     */
    public static void bumpBallerinaTomlVersion(String packagePath) throws IOException, ValidatorException {
        String filePath = packagePath + File.separator + BALLERINA_TOML;
        try (InputStream input = new FileInputStream(filePath)) {
            Toml toml = new Toml().read(input);
            String version = toml.getTable(PACKAGE).getString(VERSION);
            String semVer = version.replace(SNAPSHOT, EMPTY_STRING);
            String[] semVerPartitions = semVer.split("\\.");
            if (semVerPartitions.length != 3) {
                throw new ValidatorException("Invalid version pattern");
            } else {
                int patchVersion = Integer.parseInt(semVerPartitions[2]);
                String updatedVersion = String.format("%s.%s.%s", semVerPartitions[0], semVerPartitions[1], (patchVersion + 1));
                String updatedVersionWithSnapshotAdded = updatedVersion + SNAPSHOT;
                TomlWriter tomlWriter = new TomlWriter();
                Map<String, Object> currentTomlFileMap = toml.toMap();
                Map<String, String> packageTable = (Map<String, String>) currentTomlFileMap.get(PACKAGE);
                packageTable.replace(VERSION, updatedVersionWithSnapshotAdded);
                currentTomlFileMap.replace(PACKAGE, packageTable);
                try (OutputStream out = new FileOutputStream(filePath)) {
                    tomlWriter.write(currentTomlFileMap, out);
                }
            }
        }
    }

    /**
     * Get absolute path of each ballerina package in the given root directory
     *
     * @param projectBaseDir the base project directory
     * @return list of ballerina package paths
     */
    public static List<String> findBallerinaPackages(String projectBaseDir)
            throws IOException {
        List<String> packageDirs;
        try (Stream<Path> path = Files.walk(Paths.get(projectBaseDir))) {
            packageDirs = path.map(Path::toString).filter(f -> f.contains(BALLERINA_TOML))
                    .map(s -> s.substring(0, s.lastIndexOf("/"))).collect(Collectors.toList());
        }
        return packageDirs.stream().distinct().collect(Collectors.toList());
    }
}
