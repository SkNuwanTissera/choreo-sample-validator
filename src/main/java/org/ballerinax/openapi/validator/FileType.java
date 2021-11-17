package org.ballerinax.openapi.validator;

public enum FileType {
    YAML("yaml"), TOML("toml"), PROPERTIES("properties"), YML("yml"), JSON("json");
    private String value;
    FileType(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
