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
package com.bloomberg.datalake.trino.plugin.geotools;
import com.uber.h3core.H3Core;
import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.type.BigintType;

import java.io.IOException;
import java.util.List;

public final class H3Helper
{
    private H3Helper()
    {
        //not used
    }

    private static final H3Core h3;

    static {
        try {
            h3 = H3Core.newInstance();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to initialize H3", e);
        }
    }

    public static H3Core getH3()
    {
        return h3;
    }

    public static Block longListToBlock(List<Long> list)
    {
        BlockBuilder blockBuilder = BigintType.BIGINT.createFixedSizeBlockBuilder(list.size());
        for (Long cell : list) {
            BigintType.BIGINT.writeLong(blockBuilder, cell);
        }
        return blockBuilder.build();
    }
}
