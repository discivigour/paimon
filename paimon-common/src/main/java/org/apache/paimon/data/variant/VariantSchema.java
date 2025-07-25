/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.data.variant;

import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/* This file is based on source code from the Spark Project (http://spark.apache.org/), licensed by the Apache
 * Software Foundation (ASF) under the Apache License, Version 2.0. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership. */

/**
 * Defines a valid shredding schema, as described in
 * https://github.com/apache/parquet-format/blob/master/VariantShredding.md. A shredding schema
 * contains a value and optional typed_value field. If a typed_value is an array or struct, it
 * recursively contains its own shredding schema for elements and fields, respectively. The schema
 * also contains a metadata field at the top level, but not in recursively shredded fields.
 */
public class VariantSchema {

    /** Represents one field of an object in the shredding schema. */
    public static final class ObjectField {
        public final String fieldName;
        public final VariantSchema schema;

        public ObjectField(String fieldName, VariantSchema schema) {
            this.fieldName = fieldName;
            this.schema = schema;
        }

        @Override
        public String toString() {
            return "ObjectField{" + "fieldName=" + fieldName + ", schema=" + schema + '}';
        }
    }

    /** ScalarType. */
    public abstract static class ScalarType {}

    /** StringType. */
    public static final class StringType extends ScalarType {}

    /** IntegralSize. */
    public enum IntegralSize {
        BYTE,
        SHORT,
        INT,
        LONG
    }

    /** IntegralType. */
    public static final class IntegralType extends ScalarType {
        public final IntegralSize size;

        public IntegralType(IntegralSize size) {
            this.size = size;
        }
    }

    /** FloatType. */
    public static final class FloatType extends ScalarType {}

    /** DoubleType. */
    public static final class DoubleType extends ScalarType {}

    /** BooleanType. */
    public static final class BooleanType extends ScalarType {}

    /** BinaryType. */
    public static final class BinaryType extends ScalarType {}

    /** DecimalType. */
    public static final class DecimalType extends ScalarType {
        public final int precision;
        public final int scale;

        public DecimalType(int precision, int scale) {
            this.precision = precision;
            this.scale = scale;
        }
    }

    /** DateType. */
    public static final class DateType extends ScalarType {}

    /** TimestampType. */
    public static final class TimestampType extends ScalarType {}

    /** TimestampNTZType. */
    public static final class TimestampNTZType extends ScalarType {}

    /** UuidType. */
    public static final class UuidType extends ScalarType {}

    // The index of the typed_value, value, and metadata fields in the schema, respectively. If a
    // given field is not in the schema, its value must be set to -1 to indicate that it is invalid.
    // The indices of valid fields should be contiguous and start from 0.
    public int typedIdx;
    public int variantIdx;
    // topLevelMetadataIdx must be non-negative in the top-level schema, and -1 at all other nesting
    // levels.
    public final int topLevelMetadataIdx;
    // The number of fields in the schema. I.e. a value between 1 and 3, depending on which of
    // value,
    // typed_value and metadata are present.
    public final int numFields;

    public final ScalarType scalarSchema;
    public final ObjectField[] objectSchema;
    // Map for fast lookup of object fields by name. The values are an index into `objectSchema`.
    public final Map<String, Integer> objectSchemaMap;
    public final VariantSchema arraySchema;

    public VariantSchema(
            int typedIdx,
            int variantIdx,
            int topLevelMetadataIdx,
            int numFields,
            ScalarType scalarSchema,
            ObjectField[] objectSchema,
            VariantSchema arraySchema) {
        this.typedIdx = typedIdx;
        this.numFields = numFields;
        this.variantIdx = variantIdx;
        this.topLevelMetadataIdx = topLevelMetadataIdx;
        this.scalarSchema = scalarSchema;
        this.objectSchema = objectSchema;
        if (objectSchema != null) {
            objectSchemaMap = new HashMap<>();
            for (int i = 0; i < objectSchema.length; i++) {
                objectSchemaMap.put(objectSchema[i].fieldName, i);
            }
        } else {
            objectSchemaMap = null;
        }

        this.arraySchema = arraySchema;
    }

    public void setTypedIdx(int typedIdx) {
        this.typedIdx = typedIdx;
        this.variantIdx = -1;
        setMetadata();
    }

    private byte[] metadata;

    public byte[] getMetadata() {
        return metadata;
    }

    public void setMetadata() {
        try {
            String s = new ObjectMapper().writeValueAsString(objectSchemaMap);
            metadata = GenericVariant.fromJson(s).metadata();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // Return whether the variant column is unshrededed. The user is not required to do anything
    // special, but can have certain optimizations for unshrededed variant.
    public boolean isUnshredded() {
        return topLevelMetadataIdx >= 0 && variantIdx >= 0 && typedIdx < 0;
    }

    @Override
    public String toString() {
        return "VariantSchema{"
                + "typedIdx="
                + typedIdx
                + ", variantIdx="
                + variantIdx
                + ", topLevelMetadataIdx="
                + topLevelMetadataIdx
                + ", numFields="
                + numFields
                + ", scalarSchema="
                + scalarSchema
                + ", objectSchema="
                + objectSchema
                + ", arraySchema="
                + arraySchema
                + '}';
    }
}
