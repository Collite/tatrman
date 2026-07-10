package com.alteryx.kyx;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class PropertyAnnotationBase {
    public String name = "Annot";
    public Properties properties = new Properties();

    public PropertyAnnotationBase() {
    }

    public PropertyAnnotationBase(String name) {
        this.name = name;
    }


    public PropertyAnnotationBase(@NotNull String s, @NotNull Properties properties) {
        this.name = s;
        this.properties = properties;
    }

    public PropertyAnnotationBase(@NotNull String s, @NotNull Map<String, Object> properties) {
        this.name = s;
        this.properties = new Properties();
        this.properties.putAll(properties);
    }

    @Override
    public String toString() {
       return UtilsKt.printPropertyAnnotationBase(this);
    }

    public PropertyAnnotationBase copy() {
        PropertyAnnotationBase newOne = new PropertyAnnotationBase(this.name);
        newOne.properties.putAll(this.properties);
        return newOne;
    }

}
