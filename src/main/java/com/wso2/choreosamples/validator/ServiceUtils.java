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

package com.wso2.choreosamples.validator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.wso2.choreosamples.validator.Constants.BALLERINA_TOML;
import static com.wso2.choreosamples.validator.Constants.SERVICE_PROPERTIES;

/**
 * The Util class with supporting methods to validate the samples
 */
public class ServiceUtils extends CommonUtils {

    private static final Properties serviceProperties = new Properties();
    private static Path baseDir;

    /**
     * Load the properties file to find out the updated service in Choreo samples.
     * It is required to call this method before calling any other methods in this class
     *
     * @param projectBaseDir the base project directory
     * @throws IOException if an error occurred while loading the openapi.properties file
     */
    public static void loadServiceProperties(String projectBaseDir) throws IOException {
        baseDir = Paths.get(projectBaseDir);
        try (var fileInputStream =
                     new FileInputStream(Paths.get(projectBaseDir, SERVICE_PROPERTIES).toString())) {
            serviceProperties.load(fileInputStream);
        }
    }

    /**
     * Get absolute path of each ballerina package which was subjected to a change, in the given root directory
     *
     * @param projectBaseDir the base project directory
     * @return list of ballerina package paths
     */
    public static List<String> findUpdatedServices(String projectBaseDir)
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
     * Check the OpenAPI file has changes
     *
     * @param packageDirectoryPath the path of the OpenAPI file
     * @return true if OpenAPI file updated, else false
     * @throws IOException if an error occurred while accessing files inside given path
     */
    private static boolean isPackageChanged(String packageDirectoryPath) throws IOException {
        final String oldHash = (String) serviceProperties.get(baseDir.relativize(Paths.get(packageDirectoryPath)).toString());
        if (oldHash != null) {
            final String currentHash = generateHashString(packageDirectoryPath);
            return !oldHash.equalsIgnoreCase(currentHash);
        }
        return true;
    }

    /**
     * Update/Add the md5 hash of the changed/new OpenAPI spec files in openapi.properties file.
     *
     * @param projectBaseDir           absolute path of the project root
     * @param updatedBallerinaPackages path list of updated ballerina packages
     * @throws IOException if an error occurred while accessing files inside given path
     */
    public static void updateHashesForServices(String projectBaseDir, List<String> updatedBallerinaPackages)
            throws IOException {
        loadServiceProperties(projectBaseDir);
        for (String path : updatedBallerinaPackages) {
            String relativeFilePath = baseDir.relativize(Paths.get(path)).toString();
            String newHash = generateHashString(relativeFilePath);
            serviceProperties.setProperty(relativeFilePath, newHash);
        }
        baseDir = Paths.get(projectBaseDir);
        try (var fileInputStream = new FileOutputStream(Paths.get(projectBaseDir, SERVICE_PROPERTIES).toString())) {
            serviceProperties.store(fileInputStream, null);
        }
    }
}

