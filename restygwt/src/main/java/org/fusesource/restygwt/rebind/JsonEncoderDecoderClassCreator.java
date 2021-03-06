/**
 * Copyright (C) 2009-2010 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.restygwt.rebind;

import java.util.ArrayList;
import java.util.List;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.NotFoundException;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;

import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonTypeInfo.As;
import org.codehaus.jackson.annotate.JsonTypeInfo.Id;
import org.codehaus.jackson.annotate.JsonTypeName;
import org.fusesource.restygwt.client.Json;
import org.fusesource.restygwt.client.Json.Style;

/**
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 *
 *         Updates: added getter & setter support, enhanced generics support
 * @author <a href="http://www.acuedo.com">Dave Finch</a>
 *
 *                  added polymorphic support
 * @author <a href="http://charliemason.info">Charlie Mason</a>
 *
 */

public class JsonEncoderDecoderClassCreator extends BaseSourceCreator {
    private static final String JSON_ENCODER_SUFFIX = "_Generated_JsonEncoderDecoder_";

    private String JSON_ENCODER_DECODER_CLASS = JsonEncoderDecoderInstanceLocator.JSON_ENCODER_DECODER_CLASS;
    private static final String JSON_VALUE_CLASS = JSONValue.class.getName();
    private static final String JSON_OBJECT_CLASS = JSONObject.class.getName();

    JsonEncoderDecoderInstanceLocator locator;

    public JsonEncoderDecoderClassCreator(TreeLogger logger, GeneratorContext context, JClassType source) throws UnableToCompleteException {
        super(logger, context, source, JSON_ENCODER_SUFFIX);
    }

    @Override
    protected ClassSourceFileComposerFactory createComposerFactory() {
        ClassSourceFileComposerFactory composerFactory = new ClassSourceFileComposerFactory(packageName, shortName);

        composerFactory.setSuperclass(JSON_ENCODER_DECODER_CLASS + "<" + getClassParameterizedQualifiedSourceName(source) + ">");
        return composerFactory;
    }

    @Override
    public void generate() throws UnableToCompleteException {

        JsonTypeInfo typeInfo = source.getAnnotation(JsonTypeInfo.class);
        JsonSubTypes jacksonSubTypes = null;
        ArrayList<JClassType> possibleTypes = new ArrayList<JClassType>();

        locator = new JsonEncoderDecoderInstanceLocator(context, logger);

        JClassType sourceClazz = source.isClass();
        if (sourceClazz == null) {
            error("Type is not a class");
        }

        if(sourceClazz.isAbstract()){
            if(typeInfo == null){
                error("Abstract classes must be annotated with JsonTypeInfo");
            }
        }
        else if (!sourceClazz.isDefaultInstantiable()) {
            error("No default constuctor");
        }

        if(typeInfo == null){
            //Just add this type
            possibleTypes.add(source);
        }
        else{
            //Get all the possible types from the annotation
            jacksonSubTypes = source.getAnnotation(JsonSubTypes.class);

            if(jacksonSubTypes != null){
                for(JsonSubTypes.Type type : jacksonSubTypes.value())
                {
                    try{
                        //Look up and add each declared type
                        possibleTypes.add(context.getTypeOracle().getType(type.value().getName()));
                    }
                    catch (NotFoundException e){
                        error("Unable to find decalred JsonSubType " + type.name());
                    }
                }
            }
            else{
                error("Unable to find required JsonSubTypes annotion on " + source.getQualifiedSourceName());
            }
        }

        Json jsonAnnotation = source.getAnnotation(Json.class);
        final Style classStyle = jsonAnnotation != null ? jsonAnnotation.style() : Style.DEFAULT;

        p();
        p("public static final " + shortName + " INSTANCE = new " + shortName + "();");
        p();

        if(null != sourceClazz.isEnum()) {
            p();
            p("public " + JSON_VALUE_CLASS + " encode(" + getClassParameterizedQualifiedSourceName(source) + " value) {").i(1);
            {
                p("if( value==null ) {").i(1);
                {
                    p("return com.google.gwt.json.client.JSONNull.getInstance();").i(-1);
                }
                p("}");
                p("return new com.google.gwt.json.client.JSONString(value.name());");
            }
            i(-1).p("}");
            p();
            p("public " + source.getName() + " decode(" + JSON_VALUE_CLASS + " value) {").i(1);
            {
                p("if( value == null || value.isNull()!=null ) {").i(1);
                {
                    p("return null;").i(-1);
                }
                p("}");
                p("com.google.gwt.json.client.JSONString str = value.isString();");
                p("if( null == str ) {").i(1);
                {
                    p("throw new DecodingException(\"Expected a json string (for enum), but was given: \"+value);").i(-1);
                }
                p("}");
                p("return Enum.valueOf("+getClassParameterizedQualifiedSourceName(source)+".class, str.stringValue());").i(-1);
            }
            p("}");
            p();
            return;
        }




        p("public " + JSON_VALUE_CLASS + " encode(" + getClassParameterizedQualifiedSourceName(source) + " value) {").i(1);
        {
            p("if( value==null ) {").i(1);
            {
                p("return null;");
            }
            i(-1).p("}");

            p(JSON_OBJECT_CLASS + " rc = new " + JSON_OBJECT_CLASS + "();");

            JsonTypeInfo sourceTypeInfo = source.getAnnotation(JsonTypeInfo.class);

            for(JClassType possibleType : possibleTypes){

                if(possibleTypes.size() > 1){
                    //Generate a decoder for each possible type
                    p("if(value.getClass().getName().equals(\"" + getClassParameterizedQualifiedSourceName(possibleType) + "\"))");
                    p("{");
                }

                if(sourceTypeInfo != null && sourceTypeInfo.include()==As.PROPERTY){
                    //Write out the type info so it can be decoded correctly
                    p("com.google.gwt.json.client.JSONValue className=org.fusesource.restygwt.client.AbstractJsonEncoderDecoder.STRING.encode(\"" + getTypeIdentifier(sourceTypeInfo, jacksonSubTypes, possibleType) + "\");");
                    p("if( className!=null ) { ");
                    p("rc.put(\"" + sourceTypeInfo.property() +"\", className);");
                    p("}");
                }

                p(getClassParameterizedQualifiedSourceName(possibleType) + " parseValue = (" + getClassParameterizedQualifiedSourceName(possibleType) +")value;");


                for (final JField field : getFields(possibleType))
                {

                    final String getterName = getGetterName(field);

                    // If can ignore some fields right off the back..
                    if (getterName == null && (field.isStatic() || field.isFinal() || field.isTransient())) {
                        continue;
                    }

                    branch("Processing field: " + field.getName(), new Branch<Void>() {
                        public Void execute() throws UnableToCompleteException {
                            // TODO: try to get the field with a setter or JSNI
                            if (getterName != null || field.isDefaultAccess() || field.isProtected() || field.isPublic()) {

                                Json jsonAnnotation = field.getAnnotation(Json.class);

                                String name = field.getName();
                                String jsonName = name;

                                if( jsonAnnotation !=null && jsonAnnotation.name().length() > 0  ) {
                                    jsonName = jsonAnnotation.name();
                                }

                                String fieldExpr = "parseValue." + name;
                                if (getterName != null) {
                                    fieldExpr = "parseValue." + getterName + "()";
                                }

                                Style style = jsonAnnotation!=null ? jsonAnnotation.style() : classStyle;
                                String expression = locator.encodeExpression(field.getType(), fieldExpr, style);

                                p("{").i(1);
                                {
                                    if(null != field.getType().isEnum()) {
                                        p("if("+fieldExpr+" == null) {").i(1);
                                        p("rc.put(" + wrap(name) + ", null);");
                                        i(-1).p("} else {").i(1);
                                    }

                                    p(JSON_VALUE_CLASS + " v=" + expression + ";");
                                    p("if( v!=null ) {").i(1);
                                    {
                                        p("rc.put(" + wrap(jsonName) + ", v);");
                                    }
                                    i(-1).p("}");

                                    if(null != field.getType().isEnum()) {
                                        i(-1).p("}");
                                    }

                                }
                                i(-1).p("}");

                            } else {
                                error("field must not be private: " + field.getEnclosingType().getQualifiedSourceName() + "." + field.getName());
                            }
                            return null;
                        }
                    });

                }

                p("return rc;");

                if(possibleTypes.size() > 1)
                {
                    p("}");
                }
            }

            if(possibleTypes.size() > 1)
            {
                //Shouldn't get called
                p("return null;");
            }
        }
        i(-1).p("}");
        p();
        p("public " + source.getName() + " decode(" + JSON_VALUE_CLASS + " value) {").i(1);
        {
            p(JSON_OBJECT_CLASS + " object = toObject(value);");


            JsonTypeInfo sourceTypeInfo = source.getAnnotation(JsonTypeInfo.class);

            if(sourceTypeInfo != null && sourceTypeInfo.include()==As.PROPERTY){
                p("String sourceName = org.fusesource.restygwt.client.AbstractJsonEncoderDecoder.STRING.decode(object.get("
                        + wrap(sourceTypeInfo.property()) + "));");
            }

            for(JClassType possibleType : possibleTypes){
                if(possibleTypes.size() > 1){
                    //Generate a decoder for each possible type
                    p("if(sourceName.equals(\"" + getTypeIdentifier(sourceTypeInfo, jacksonSubTypes, possibleType) + "\"))");
                    p("{");
                }

                p("" + getClassParameterizedQualifiedSourceName(possibleType) + " rc = new " + getClassParameterizedQualifiedSourceName(possibleType) + "();");

                for (final JField field : getFields(possibleType)) {


                    final String setterName = getSetterName(field);

                    // If can ignore some fields right off the back..
                    if (setterName == null && (field.isStatic() || field.isFinal() || field.isTransient())) {
                        continue;
                    }

                    branch("Processing field: " + field.getName(), new Branch<Void>() {
                        public Void execute() throws UnableToCompleteException {

                            // TODO: try to set the field with a setter or JSNI
                            if (setterName != null || field.isDefaultAccess() || field.isProtected() || field.isPublic()) {

                                Json jsonAnnotation = field.getAnnotation(Json.class);
                                Style style = jsonAnnotation != null ? jsonAnnotation.style() : classStyle;

                                String name = field.getName();
                                String jsonName = field.getName();

                                if( jsonAnnotation !=null && jsonAnnotation.name().length() > 0  ) {
                                    jsonName = jsonAnnotation.name();
                                }

                                String objectGetter = "object.get(" + wrap(jsonName) + ")";
                                String expression = locator.decodeExpression(field.getType(), objectGetter, style);

                                p("if(" + objectGetter + " != null) {").i(1);

                                if (! field.getType().equals(locator.SAFE_HTML_TYPE)) {
                                    if (field.getType().isPrimitive() == null) {
                                        p("if(" + objectGetter + " instanceof com.google.gwt.json.client.JSONNull) {").i(1);

                                        if (setterName != null) {
                                            p("rc." + setterName + "(null);");
                                        } else {
                                            p("rc." + name + "=null;");
                                        }

                                        i(-1).p("} else {").i(1);
                                    }
                                }

                                if (setterName != null) {
                                    p("rc." + setterName + "(" + expression + ");");
                                } else {
                                    p("rc." + name + "=" + expression + ";");
                                }
                                i(-1).p("}");

                                if (! field.getType().equals(locator.SAFE_HTML_TYPE)) {
                                    if (field.getType().isPrimitive() == null) {
                                        i(-1).p("}");
                                    }
                                }

                            } else {
                                error("field must not be private.");
                            }
                            return null;
                        }
                    });
                }

                p("return rc;");

                if(possibleTypes.size() > 1)
                {
                    p("}");
                }
            }

            if(possibleTypes.size() > 1)
            {
                //Shouldn't get called
                p("return null;");
            }

            i(-1).p("}");
            p();
        }
    }

    /**
     *
     * @param field
     * @return the name for the setter for the specified field or null if a
     *         setter can't be found.
     */
    private String getSetterName(JField field) {
        String fieldName = field.getName();
        fieldName = "set" + upperCaseFirstChar(fieldName);
        JClassType type = field.getEnclosingType();
        if (exists(type, field, fieldName, true)) {
            return fieldName;
        } else {
            return null;
        }
    }

    /**
     *
     * @param field
     * @return the name for the getter for the specified field or null if a
     *         getter can't be found.
     */
    private String getGetterName(JField field) {
        String fieldName = field.getName();
        JType booleanType = null;
        try {
            booleanType = find(Boolean.class);
        } catch (UnableToCompleteException e) {
            // do nothing
        }
        JClassType type = field.getEnclosingType();
        if (field.getType().equals(JPrimitiveType.BOOLEAN) || field.getType().equals(booleanType)) {
            fieldName = "is" + upperCaseFirstChar(field.getName());
            if (exists(type, field, fieldName, false)) {
                return fieldName;
            }
            fieldName = "has" + upperCaseFirstChar(field.getName());
            if (exists(type, field, fieldName, false)) {
                return fieldName;
            }
        }
        fieldName = "get" + upperCaseFirstChar(field.getName());
        if (exists(type, field, fieldName, false)) {
            return fieldName;
        } else {
            return null;
        }
    }


    private String upperCaseFirstChar(String in) {
        if (in.length() == 1) {
            return in.toUpperCase();
        } else {
            return in.substring(0, 1).toUpperCase() + in.substring(1);
        }
    }

    /**
     * checks whether a getter or setter exists on the specified type or any of
     * its super classes excluding Object.
     *
     * @param type
     * @param field
     * @param fieldName
     * @param isSetter
     * @return
     */
    private boolean exists(JClassType type, JField field, String fieldName, boolean isSetter) {
        JType[] args = null;
        if (isSetter) {
            args = new JType[] { field.getType() };
        } else {
            args = new JType[] {};
        }

        if (null != type.findMethod(fieldName, args)) {
            return true;
        } else {
            try {
                JType objectType = find(Object.class);
                JClassType superType = type.getSuperclass();
                if (!objectType.equals(superType)) {
                    return exists(superType, field, fieldName, isSetter);
                }
            } catch (UnableToCompleteException e) {
                // do nothing
            }
        }
        return false;
    }

    /**
     * Inspects the supplied type and all super classes up to but excluding
     * Object and returns a list of all fields found in these classes.
     *
     * @param type
     * @return
     */
    private List<JField> getFields(JClassType type) {
        return getFields(new ArrayList<JField>(), type);
    }

    private List<JField> getFields(List<JField> allFields, JClassType type) {
        JField[] fields = type.getFields();
        for (JField field : fields) {
            allFields.add(field);
        }
        try {
            JType objectType = find(Object.class);
            JClassType superType = type.getSuperclass();
            if (!objectType.equals(superType)) {
                return getFields(allFields, superType);
            }
        } catch (UnableToCompleteException e) {
            // do nothing
        }
        return allFields;
    }

    private String getTypeIdentifier(JsonTypeInfo typeInfo, JsonSubTypes subTypes, JClassType classType) throws UnableToCompleteException
    {
        if(typeInfo.use() == Id.CLASS){

            //Just return the full class name
            return classType.getQualifiedSourceName();
        }
        else if(typeInfo.use() == Id.NAME){

            //Find the subtype entry
            for(JsonSubTypes.Type type : subTypes.value()){

                //Check if this is correct type and return its name
                if(type.value().getName().equals(classType.getParameterizedQualifiedSourceName())){

                    if(type.name() != null && !type.name().isEmpty())
                        return type.name();
                }
            }

            //We obviously couldn't find it so check if its got
            //it declared as an annotation on the class its self
            JsonTypeName typeName = classType.getAnnotation(JsonTypeName.class);

            if(typeName != null){
                return typeName.value();
            }
            else{
                error("Unable to find custom type name for " + classType.getParameterizedQualifiedSourceName());
            }
        }

        else if(typeInfo.use() == Id.MINIMAL_CLASS){
            error("JsonTypeInfo.use MINIMAL_CLASS is currently unsupported");
        }

        else if(typeInfo.use() == Id.CUSTOM){
            error("JsonTypeInfo.use CUSTOM is currently unsupported");
        }

        return "";
    }

    private String getClassParameterizedQualifiedSourceName(final JClassType source) {
        return source.getParameterizedQualifiedSourceName();
    }
}