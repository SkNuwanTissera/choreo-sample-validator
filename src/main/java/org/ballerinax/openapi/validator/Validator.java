package org.ballerinax.openapi.validator;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is used to validate the OpenAPI files and Ballerina.toml file in each Ballerina package of
 * ballerinax-openapi-connectors repository.
 */
public class Validator {

    private static final Logger log = Logger.getLogger(Validator.class);
    private static final String DISPLAY_EXTENSION = "x-display";
    private static final String LABEL = "label";
    private static final String ICON = "icon";

    /**
     * Validate the updated OpenAPI ihe given ballerina package
     *
     * @param openAPIFilePath the absolute path of the OpenAPI file
     * @throws IOException
     * @throws ValidatorException
     */
    public static void validateOpenAPI(String openAPIFilePath) throws IOException, ValidatorException {
        var openAPI = parseOpenAPI(openAPIFilePath);
        validateXDisplayExtension(openAPI, openAPIFilePath);

    }

    /**
     * Validate the x-display annotation correctly added in the given OpenAPI file
     *
     * @param openAPI         the OpenAPI object
     * @param openAPIFilePath the absolute path of the OpenAPI file
     * @throws ValidatorException
     */
    private static void validateXDisplayExtension(OpenAPI openAPI, String openAPIFilePath) throws ValidatorException {
        validateXDisplayExtensionInfoSection(openAPI, openAPIFilePath);
        validateXDisplayExtensionPathsSection(openAPI, openAPIFilePath);
        validateXDisplayExtensionComponentsSection(openAPI, openAPIFilePath);
    }

    /**
     * Validate the x-display extension has added under info section in the given OpenAPI file
     *
     * @param openAPI         the OpenAPI object
     * @param openAPIFilePath the absolute path of the OpenAPI file
     * @throws ValidatorException
     */
    private static void validateXDisplayExtensionInfoSection(OpenAPI openAPI, String openAPIFilePath)
            throws ValidatorException {
        if (openAPI.getInfo() != null) {
            var info = openAPI.getInfo();
            Map<String, Object> extensions = info.getExtensions();
            validateExtensions(extensions, openAPIFilePath, "Info");
        }
    }

    /**
     * Validate the x-display extension has added under paths section in the given OpenAPI file
     *
     * @param openAPI         the OpenAPI object
     * @param openAPIFilePath the absolute path of the OpenAPI file
     * @throws ValidatorException
     */
    private static void validateXDisplayExtensionPathsSection(OpenAPI openAPI, String openAPIFilePath)
            throws ValidatorException {
        if (openAPI.getPaths() != null) {
            var paths = openAPI.getPaths();
            for (Map.Entry<String, PathItem> path : paths.entrySet()) {
                Map<String, Object> extensions = path.getValue().getGet().getExtensions();
                validateExtensions(extensions, openAPIFilePath, "Paths");
            }
        }
    }

    /**
     * Validate the x-display extension has added under components section in the given OpenAPI file
     *
     * @param openAPI         the OpenAPI object
     * @param openAPIFilePath the absolute path of the OpenAPI file
     * @throws ValidatorException
     */
    private static void validateXDisplayExtensionComponentsSection(OpenAPI openAPI, String openAPIFilePath)
            throws ValidatorException {
        if (openAPI.getComponents() != null) {
            var components = openAPI.getComponents();
            if (components.getParameters() != null) {
                Map<String, Parameter> parameters = components.getParameters();
                for (Map.Entry<String, Parameter> param : parameters.entrySet()) {
                    Parameter paraVal = param.getValue();
                    Map<String, Object> extensions = paraVal.getExtensions();
                    validateExtensions(extensions, openAPIFilePath, "Components");
                }
            }
        }
    }

    /**
     * Validate the x-display extension in each location
     *
     * @param extensions      the x-display extension map
     * @param openAPIFilePath the absolute path of the OpenAPI file
     * @param section         the section of x-display extension; possible values could be Info, Paths, Components
     * @throws ValidatorException
     */
    private static void validateExtensions(Map<String, Object> extensions, String openAPIFilePath, String section)
            throws ValidatorException {
        if (extensions == null) {
            throw new ValidatorException("Could not find OpenAPI Extensions 'x-display' in '" + section +
                    "': " + openAPIFilePath);
        }
        for (Map.Entry<String, Object> ext : extensions.entrySet()) {
            String extensionName = ext.getKey();
            if (extensionName.equals(DISPLAY_EXTENSION)) {
                LinkedHashMap<String, String> extFields = (LinkedHashMap<String, String>) ext.getValue();
                for (Map.Entry<String, String> field : extFields.entrySet()) {
                    String fieldName = field.getKey();
                    if (fieldName.equals(LABEL)) {
                        String labelVal = field.getValue();
                        if (labelVal.isEmpty()) {
                            throw new ValidatorException("Invalid 'x-display' extension. Could not find the 'label' " +
                                    "field in the '" + section + "': " + openAPIFilePath);
                        }
                    } else if (fieldName.equals(ICON)) {
                        String icon = field.getValue();
                        if (icon.isEmpty() && section.equals("Info")) {
                            throw new ValidatorException("Invalid 'x-display' extension. Could not find the 'icon' " +
                                    "field in the '" + section + "': " + openAPIFilePath);
                        }
                    }
                }
            } else {
                throw new ValidatorException("Could not find the 'x-display' extension in the '" + section +
                        "': " + openAPIFilePath);
            }
        }
    }

    /**
     * Parse the given OpenAPI file to see any syntax errors
     *
     * @param definitionURI the absolute path of the OpenAPI file
     * @return
     * @throws ValidatorException
     * @throws IOException
     */
    private static OpenAPI parseOpenAPI(String definitionURI) throws ValidatorException, IOException {
        var contractPath = java.nio.file.Paths.get(definitionURI);
        var openAPIFileContent = Files.readString(contractPath);
        SwaggerParseResult parseResult = new OpenAPIV3Parser().readContents(openAPIFileContent);
        if (!parseResult.getMessages().isEmpty()) {
            throw new ValidatorException("Parse Error in " + contractPath);
        }
        return parseResult.getOpenAPI();
    }

}
