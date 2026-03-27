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
package io.trino.plugin.session.db;

import io.trino.plugin.session.SessionMatchSpec;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowMapper;

import java.util.List;

import static io.trino.plugin.session.db.util.SessionPropertiesDaoUtil.CLIENT_TAGS_TABLE;
import static io.trino.plugin.session.db.util.SessionPropertiesDaoUtil.PROPERTIES_TABLE;
import static io.trino.plugin.session.db.util.SessionPropertiesDaoUtil.SESSION_SPECS_TABLE;

public interface PostgresSessionPropertiesDao
        extends SessionPropertiesDao
{
    @SqlQuery("SELECT " +
            "S.spec_id,\n" +
            "S.user_regex,\n" +
            "S.user_group_regex,\n" +
            "S.source_regex,\n" +
            "S.query_type,\n" +
            "S.group_regex,\n" +
            "S.client_tags,\n" +
            "string_agg(P.session_property_name, ',' ORDER BY P.session_property_name) session_property_names,\n" +
            "string_agg(P.session_property_value, ',' ORDER BY P.session_property_name) session_property_values\n" +
            "FROM\n" +
            "(SELECT\n" +
            "A.spec_id, A.user_regex, A.user_group_regex, A.source_regex, A.query_type, A.group_regex, A.priority,\n" +
            "string_agg(DISTINCT B.client_tag, ',') client_tags\n" +
            "FROM " + SESSION_SPECS_TABLE + " A\n" +
            "LEFT JOIN " + CLIENT_TAGS_TABLE + " B\n" +
            "ON A.spec_id = B.tag_spec_id\n" +
            "GROUP BY A.spec_id, A.user_regex, A.user_group_regex, A.source_regex, A.query_type, A.group_regex, A.priority)\n" +
            " S JOIN\n" +
            PROPERTIES_TABLE + " P\n" +
            "ON S.spec_id = P.property_spec_id\n" +
            "GROUP BY S.spec_id, S.user_regex, S.user_group_regex, S.source_regex, S.query_type, S.group_regex, S.priority, S.client_tags\n" +
            "ORDER BY S.priority asc")
    @UseRowMapper(SessionMatchSpec.Mapper.class)
    List<SessionMatchSpec> getSessionMatchSpecs();
}
