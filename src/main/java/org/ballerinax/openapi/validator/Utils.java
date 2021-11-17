/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.openapi.validator;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Util class with supporting methods to validate the samples
 */
public class Utils {

    private static final Properties sampleProperties = new Properties();
    private static Path baseDir;
    private static final String MD5 = "MD5";
    private static final String PACKAGE = "package";
    private static final String VERSION = "version";
    private static final String SNAPSHOT = "-SNAPSHOT";
    private static final String EMPTY_STRING = "";
    private static final String SAMPLE_PROPERTIES = "sample.properties";
    private static final String BALLERINA_TOML = "Ballerina.toml";
    private static final String PACKAGE_MD = "Package.md";
    private static final String MODULE_MD = "Module.md";

    /**
     * Load the openapi-properties file to find out the updated OpenAPI
     * It is required to call this method before calling any other methods in this class
     *
     * @param projectBaseDir the base project directory
     * @throws IOException if an error occurred while loading the openapi.properties file
     */
    public static void loadSampleProperties(String projectBaseDir) throws IOException {
        baseDir = Paths.get(projectBaseDir);
        try (var fileInputStream =
                     new FileInputStream(Paths.get(projectBaseDir, SAMPLE_PROPERTIES).toString())) {
            sampleProperties.load(fileInputStream);
        }
    }

    /**
     * Get absolute path of each ballerina package which was subjected to a change, in the given root directory
     *
     * @param projectBaseDir the base project directory
     * @return list of ballerina package paths
     */
    public static List<String> findUpdatedBallerinaPackages(String projectBaseDir)
            throws IOException {
        List<String> packageDirs;
        List<String> updatedPackageDirs = new ArrayList<>();
        try (Stream<Path> path = Files.walk(Paths.get(projectBaseDir))) {
            packageDirs = path.map(Path::toString).filter(f -> f.contains(BALLERINA_TOML))
                    .map(s -> s.substring(0, s.lastIndexOf("/"))).collect(Collectors.toList());
        }
        for (String packageDir : packageDirs) {
            if (isPackageChanged(packageDir)) {
                updatedPackageDirs.add(packageDir);
            }
        }
        return updatedPackageDirs.stream().distinct().collect(Collectors.toList());
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

    /**
     * Get version of a Ballerina package
     *
     * @param packagePath path to Ballerina package
     * @return package version
     * @throws IOException if an error occurred while accessing file inside the given path.
     */
    public static String getPackageVersion(String packagePath) throws IOException {
        String filePath = packagePath + File.separator + BALLERINA_TOML;
        try (InputStream input = new FileInputStream(filePath)) {
            Toml toml = new Toml().read(input);
            return toml.getTable(PACKAGE).getString(VERSION);
        }
    }

    /**
     * Remove snapshot suffix from the version
     *
     * @param packagePath path to Ballerina package
     * @throws IOException if an error occurred while accessing file inside the given path.
     */
    public static void removeSnapshotSuffixFromVersion(String packagePath) throws IOException {
        String filePath = packagePath + File.separator + BALLERINA_TOML;
        try (InputStream input = new FileInputStream(filePath)) {
            Toml toml = new Toml().read(input);
            String version = toml.getTable(PACKAGE).getString(VERSION);
            String updatedVersionWithSnapshotRemoved = version.replace(SNAPSHOT, EMPTY_STRING);
            TomlWriter tomlWriter = new TomlWriter();
            Map<String, Object> currentTomlFileMap = toml.toMap();
            Map<String, String> packageTable = (Map<String, String>) currentTomlFileMap.get(PACKAGE);
            packageTable.replace(VERSION, updatedVersionWithSnapshotRemoved);
            currentTomlFileMap.replace(PACKAGE, packageTable);
            try (OutputStream out = new FileOutputStream(filePath)) {
                tomlWriter.write(currentTomlFileMap, out);
            }
        }
    }

    /**
     * Add snapshot suffix to the version
     *
     * @param packagePath path to Ballerina package
     * @throws IOException if an error occurred while accessing file inside the given path.
     */
    public static void addSnapshotSuffixToVersion(String packagePath) throws IOException {
        String filePath = packagePath + File.separator + BALLERINA_TOML;
        try (InputStream input = new FileInputStream(filePath)) {
            Toml toml = new Toml().read(input);
            String version = toml.getTable(PACKAGE).getString(VERSION);
            String updatedVersionWithSnapshotAdded = version + SNAPSHOT;
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

    /**
     * Validate the existence of Package.md & Module.md files.
     *
     * @param packagePath path to Ballerina package
     * @throws BuildPrecheckException if failed to pass the prechecks
     */
    public static void executePrechecks(String packagePath) throws BuildPrecheckException {
        // validateMdFiles(packagePath);
        // Validate Readme.md files instead
    }

    private static void validateMdFiles(String packagePath) throws BuildPrecheckException {
        String packageMdFilePath = packagePath + File.separator + PACKAGE_MD;
        String moduleMdFilePath = packagePath + File.separator + MODULE_MD;
        File packageMdFile = new File(packageMdFilePath);
        File moduleMdFile = new File(moduleMdFilePath);

        if (!packageMdFile.exists()) {
            throw new BuildPrecheckException(PACKAGE_MD + " doesn't exist in package " + packagePath);
        } else if (packageMdFile.length() == 0) {
            throw new BuildPrecheckException(PACKAGE_MD + " is empty in package " + packagePath);
        }

        if (!moduleMdFile.exists()) {
            throw new BuildPrecheckException(MODULE_MD + " doesn't exist in package " + packagePath);
        } else if (moduleMdFile.length() == 0) {
            throw new BuildPrecheckException(MODULE_MD + " is empty in package " + packagePath);
        }
    }

    /**
     * Get Ballerina package name using the given package path
     *
     * @param packagePath path of the Ballerina package
     * @return name of the Ballerina package or null
     */
    public static String getPackageName(String packagePath) {
        List<String> pathComponents = Arrays.asList(packagePath.split("/"));
        return pathComponents.isEmpty() ? null : pathComponents.get(pathComponents.size() - 1);
    }

    /**
     * Get absolute path of the changed OpenAPI spec file of the connector
     *
     * @param updatedPackageDir path to the updated ballerina package directory
     * @return absolute path of the changed OpenAPI spec file of the given package directory or an empty string
     * @throws IOException              if an error occurred while accessing files inside given path
     * @throws NoSuchAlgorithmException if an error occurred while checking for the hash of files.
     */
    public static String findUpdatedYamlFile(String updatedPackageDir)
            throws IOException, NoSuchAlgorithmException {
        try (Stream<Path> files = Files.list(Paths.get(updatedPackageDir))) {
            List<String> apis = files.map(Path::toString)
                    .filter(f -> f.endsWith(FileType.YAML.getValue()) || f.endsWith(FileType.YML.getValue()) || f.endsWith(FileType.JSON.getValue()))
                    .collect(Collectors.toList());
            for (String api : apis) {
                if (isPackageChanged(api)) {
                    return api;
                }
            }
        }
        return "";
    }

    /**
     * Get absolute path of each updated OpenAPI-file in the given ballerina package directory
     *
     * @param balPackageDir the ballerina package directory
     * @return list of OpenAPI file paths
     */
    public static List<String> findOpenAPIs(String balPackageDir) throws IOException {
        List<String> openAPIs;
        try (Stream<Path> openAPIFiles = Files.list(Paths.get(balPackageDir))) {
            openAPIs = openAPIFiles.map(Path::toString)
                    .filter(f -> f.endsWith(FileType.YAML.getValue()) || f.endsWith(FileType.YML.getValue()) || f.endsWith(FileType.JSON.getValue()))
                    .collect(Collectors.toList());
        }
        return openAPIs;
    }

    /**
     * Check the OpenAPI file has changes
     *
     * @param openAPIFilePath the path of the OpenAPI file
     * @return true if OpenAPI file updated, else false
     * @throws NoSuchAlgorithmException if an error occurred while checking for the hash of files.
     * @throws IOException              if an error occurred while accessing files inside given path
     */
    private static boolean isOpenAPIChanged(String openAPIFilePath) throws NoSuchAlgorithmException, IOException {
        final String oldHash = (String) sampleProperties.get(baseDir.relativize(Paths.get(openAPIFilePath)).toString());
        if (oldHash != null) {
            final String currentHash = getMd5(openAPIFilePath);
            return !oldHash.equalsIgnoreCase(currentHash);
        }
        return true;
    }

    /**
     * Check the OpenAPI file has changes
     *
     * @param packageDirectoryPath the path of the OpenAPI file
     * @return true if OpenAPI file updated, else false
     * @throws IOException if an error occurred while accessing files inside given path
     */
    private static boolean isPackageChanged(String packageDirectoryPath) throws IOException {
        final String oldHash = (String) sampleProperties.get(baseDir.relativize(Paths.get(packageDirectoryPath)).toString());
        if (oldHash != null) {
            final String currentHash = generateHashString(packageDirectoryPath);
            return !oldHash.equalsIgnoreCase(currentHash);
        }
        return true;
    }

    /**
     * Get the MD5 of the OpenAPI file
     *
     * @param openAPIFilePath OpenAPI file path
     * @return the MD5 of the given OpenAPI file
     * @throws NoSuchAlgorithmException if an error occurred while checking for the hash of files.
     * @throws IOException              if an error occurred while accessing files inside given path
     */
    private static String getMd5(String openAPIFilePath) throws NoSuchAlgorithmException, IOException {
        var md5 = MessageDigest.getInstance(MD5);
        md5.update(Files.readAllBytes(Paths.get(openAPIFilePath)));
        return DigestUtils.md5Hex(md5.digest());
    }

    /**
     * Update/Add the md5 hash of the changed/new OpenAPI spec files in openapi.properties file.
     *
     * @param projectBaseDir           absolute path of the project root
     * @param updatedBallerinaPackages path list of updated ballerina packages
     * @throws IOException if an error occurred while accessing files inside given path
     */
    public static void writeUpdatedFileHashes(String projectBaseDir, List<String> updatedBallerinaPackages)
            throws IOException {
        loadSampleProperties(projectBaseDir);
        for (String path : updatedBallerinaPackages) {
            String relativeFilePath = baseDir.relativize(Paths.get(path)).toString();
            String newHash = generateHashString(relativeFilePath);
            sampleProperties.setProperty(relativeFilePath, newHash);
        }
        baseDir = Paths.get(projectBaseDir);
        try (var fileInputStream = new FileOutputStream(Paths.get(projectBaseDir, SAMPLE_PROPERTIES).toString())) {
            sampleProperties.store(fileInputStream, null);
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
}

