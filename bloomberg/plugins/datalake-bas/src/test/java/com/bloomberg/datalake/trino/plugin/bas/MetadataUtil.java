/*
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
package com.bloomberg.datalake.trino.plugin.bas;

import com.bloomberg.datalake.trino.plugin.bas.config.BasServiceConfiguration;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.StandardTypes;
import io.trino.spi.type.Type;

import java.util.List;
import java.util.Map;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static java.util.Locale.ENGLISH;

public final class MetadataUtil
{
    public static final JsonCodec<List<BasServiceConfiguration>> SERVICES_CODEC;
    public static final Type VARCHARARRAY = new ArrayType(VARCHAR);
    public static final Type INTARRAY = new ArrayType(INTEGER);

    private MetadataUtil() {}

    /**
     * TypeDeserializer uses TypeManager as an injected dependency, so we can't use that here.
     * This mimics the usage that is needed for testing.
     */
    public static final class TestingTypeDeserializer
            extends FromStringDeserializer<Type>
    {
        private final Map<String, Type> types = ImmutableMap.of(
                StandardTypes.BOOLEAN, BOOLEAN,
                StandardTypes.BIGINT, BIGINT,
                StandardTypes.INTEGER, INTEGER,
                StandardTypes.DOUBLE, DOUBLE,
                StandardTypes.VARCHAR, createUnboundedVarcharType(),
                VARCHARARRAY.getDisplayName(), VARCHARARRAY);

        public TestingTypeDeserializer()
        {
            super(Type.class);
        }

        @Override
        protected Type _deserialize(String value, DeserializationContext context)
        {
            Type type = types.get(value.toLowerCase(ENGLISH));
            if (type == null) {
                throw new IllegalArgumentException("Unknown type " + value);
            }
            return type;
        }
    }

    static {
        JsonMapperProvider jsonMapperProvider = new JsonMapperProvider();

        jsonMapperProvider.setJsonDeserializers(ImmutableMap.of(
                Type.class, new TestingTypeDeserializer()));

        JsonCodecFactory codecFactory = new JsonCodecFactory(jsonMapperProvider.get());
        SERVICES_CODEC = codecFactory.listJsonCodec(BasServiceConfiguration.class);
    }
}
