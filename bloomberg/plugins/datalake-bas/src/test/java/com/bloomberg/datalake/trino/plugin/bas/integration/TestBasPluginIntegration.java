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
package com.bloomberg.datalake.trino.plugin.bas.integration;

import com.bloomberg.codegen.bxsdsvc.Document;
import com.bloomberg.codegen.bxsdsvc.GetDocumentResult;
import com.bloomberg.codegen.crsrvpx.CrFieldInfoPrimaryRcode;
import com.bloomberg.codegen.crsrvpx.CrRequestStatus;
import com.bloomberg.codegen.crsrvpx.CrsvcResponse;
import com.bloomberg.codegen.crsrvpx.DataStatus;
import com.bloomberg.codegen.crsrvpx.FieldId;
import com.bloomberg.codegen.crsrvpx.FieldSpec;
import com.bloomberg.codegen.crsrvpx.SecDataStatus;
import com.bloomberg.codegen.crsrvpx.SecurityId;
import com.bloomberg.codegen.crsrvpx.SecuritySpec;
import com.bloomberg.codegen.crsrvpx.SecurityType;
import com.bloomberg.codegen.crsrvpx.YkFieldData;
import com.bloomberg.codegen.crsrvpx.YkFieldDataValue;
import com.bloomberg.codegen.crsrvpx.YkFieldIdData;
import com.bloomberg.codegen.crsrvpx.YkResponse;
import com.bloomberg.codegen.crsrvpx.YkSecurityData;
import com.bloomberg.codegen.etsdspbisvc.DataResponse;
import com.bloomberg.codegen.etsdspbisvc.ResultData;
import com.bloomberg.datalake.trino.plugin.bas.BasPlugin;
import com.bloomberg.testcontainers.bas.BasRouterExtension;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.airlift.testing.Closeables;
import io.trino.spi.security.Identity;
import io.trino.testing.DistributedQueryRunner;
import io.trino.testing.MaterializedResult;
import io.trino.testing.MaterializedRow;
import io.trino.testing.QueryRunner;
import io.trino.testing.TestingGroupProvider;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.MoreFiles.asCharSink;
import static io.trino.testing.TestingSession.testSessionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(BasRouterExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestBasPluginIntegration
{
    private static final String BAS_CONNECTOR_METADATA = """
            [{
              "name": "calcrt",
              "serviceInfo": "crsrvpx:166616-1.30",
              "functions": [
                {
                  "name": "ykdata",
                  "columns": [
                    {
                      "columnName": "security_name",
                      "columnType": "VARCHAR",
                      "jsonKey": "CalcrtResponse.ykresponse.responseData.secId.secname",
                      "nullable": false
                    },
                    {
                      "columnName": "calcrt_field_name",
                      "columnType": "VARCHAR",
                      "jsonKey": "CalcrtResponse.ykresponse.responseData.fieldIdData.fieldId.fieldNameDefault"
                    },
                    {
                      "columnName": "calcrt_field_str",
                      "columnType": "VARCHAR",
                      "jsonKey": "CalcrtResponse.ykresponse.responseData.fieldIdData.fieldData.stringV"
                    },
                    {
                      "columnName": "calcrt_field_dbl",
                      "columnType": "VARCHAR",
                      "jsonKey": "CalcrtResponse.ykresponse.responseData.fieldIdData.fieldData.doubleV"
                    }
                  ],
                  "parameterColumns": [
                    {
                      "columnName": "securities",
                      "columnType": "ARRAY(VARCHAR)",
                      "jsonKey": "securities",
                      "maxEntries": 100
                    },
                    {
                      "columnName": "fields",
                      "columnType": "ARRAY(VARCHAR)",
                      "jsonKey": "fields",
                      "nullable": true,
                      "maxEntries": 500
                    },
                    {
                      "columnName": "field_mnemonics",
                      "columnType": "ARRAY(VARCHAR)",
                      "jsonKey": "field_mnemonics",
                      "nullable": true,
                      "maxEntries": 500
                    },
                    {
                      "columnName": "overrides",
                      "columnType": "MAP(VARCHAR, VARCHAR)",
                      "jsonKey": "overrides",
                      "nullable": true
                    },
                    {
                      "columnName": "override_mnemonics",
                      "columnType": "MAP(VARCHAR, VARCHAR)",
                      "jsonKey": "override_mnemonics",
                      "nullable": true
                    }
                  ],
                  "requestTemplate": "calcrt.fields_request.json.jinja2",
                  "responseTransforms": {
                    "actions": [
                      {
                        "type": "unnest",
                        "key": "CalcrtResponse.ykresponse.responseData"
                      },
                      {
                        "type": "unnest",
                        "key": "CalcrtResponse.ykresponse.responseData.fieldIdData"
                      },
                      {
                        "type": "rename",
                        "key": "CalcrtResponse.ykresponse.responseData.fieldIdData.fieldId.fieldNameMnemonic",
                        "newName": "CalcrtResponse.ykresponse.responseData.fieldIdData.fieldId.fieldNameDefault"
                      }
                    ]
                  },
                  "errorDetectors": [
                    {
                      "type": "ifKeyExists",
                      "key": [
                        "CalcrtResponse",
                        "error"
                      ]
                    }
                  ]
                }
              ]
            },
            {
              "name": "etsdspbisvc",
              "serviceInfo": "etsdspbisvc:390196-1.0",
              "functions": [
                {
                  "name": "trade_store_fi_restricted",
                  "columns": [
                    {
                      "columnName": "application_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.application_name"
                    },
                    {
                      "columnName": "base_fee",
                      "columnType": "DOUBLE",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.base_fee"
                    },
                    {
                      "columnName": "base_product_desc",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.base_product_desc"
                    },
                    {
                      "columnName": "bbid",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.bbid"
                    },
                    {
                      "columnName": "bics_level_1_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.bics_level_1_name"
                    },
                    {
                      "columnName": "bics_level_2_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.bics_level_2_name"
                    },
                    {
                      "columnName": "broker_code",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.broker_code"
                    },
                    {
                      "columnName": "broker_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.broker_name"
                    },
                    {
                      "columnName": "client_country",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.client_country"
                    },
                    {
                      "columnName": "client_industry_code",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.client_industry_code"
                    },
                    {
                      "columnName": "client_ts_type",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.client_ts_type"
                    },
                    {
                      "columnName": "client_user_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.client_user_name"
                    },
                    {
                      "columnName": "client_uuid",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.client_uuid"
                    },
                    {
                      "columnName": "countered",
                      "columnType": "BOOLEAN",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.countered"
                    },
                    {
                      "columnName": "country_of_risk",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.country_of_risk"
                    },
                    {
                      "columnName": "customer_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.customer_name"
                    },
                    {
                      "columnName": "customer_number",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.customer_number"
                    },
                    {
                      "columnName": "dealer_customer",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealer_customer"
                    },
                    {
                      "columnName": "dealer_customer_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealer_customer_name"
                    },
                    {
                      "columnName": "dealer_firm",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealer_firm"
                    },
                    {
                      "columnName": "dealer_firm_number",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealer_firm_number"
                    },
                    {
                      "columnName": "dealer_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealer_name"
                    },
                    {
                      "columnName": "dealer_ts_type",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealer_ts_type"
                    },
                    {
                      "columnName": "dealer_uuid",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealer_uuid"
                    },
                    {
                      "columnName": "dealers_in_competition",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.dealers_in_competition"
                    },
                    {
                      "columnName": "execution_time",
                      "columnType": "TIMESTAMP WITH TIME ZONE",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.execution_time"
                    },
                    {
                      "columnName": "execution_venue",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.execution_venue"
                    },
                    {
                      "columnName": "final_status",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.final_status"
                    },
                    {
                      "columnName": "firm_name",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.firm_name"
                    },
                    {
                      "columnName": "firm_number",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.firm_number"
                    },
                    {
                      "columnName": "id",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.id"
                    },
                    {
                      "columnName": "indicate_axe",
                      "columnType": "BOOLEAN",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.indicate_axe"
                    },
                    {
                      "columnName": "platform",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.platform"
                    },
                    {
                      "columnName": "pnt",
                      "columnType": "BOOLEAN",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.pnt"
                    },
                    {
                      "columnName": "price",
                      "columnType": "DOUBLE",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.price"
                    },
                    {
                      "columnName": "priced",
                      "columnType": "BOOLEAN",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.priced"
                    },
                    {
                      "columnName": "quantity_m",
                      "columnType": "DOUBLE",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.quantity_m"
                    },
                    {
                      "columnName": "security",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.security"
                    },
                    {
                      "columnName": "subscription_product_desc",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "dataResponse.resultData.subscription_product_desc"
                    }
                  ],
                  "parameterColumns": [
                    {
                      "columnName": "startDate",
                      "columnType": "DATE",
                      "nullable": true,
                      "jsonKey": "startDate"
                    },
                    {
                      "columnName": "endDate",
                      "columnType": "DATE",
                      "nullable": true,
                      "jsonKey": "endDate"
                    },
                    {
                      "columnName": "resultsLimit",
                      "columnType": "INTEGER",
                      "nullable": true,
                      "jsonKey": "resultsLimit"
                    },
                    {
                      "columnName": "predicate",
                      "columnType": "VARCHAR",
                      "nullable": true,
                      "jsonKey": "predicate"
                    }
                  ],
                  "requestTemplate": "etsdsp.data_request.json.jinja2",
                  "responseTransforms": {
                    "actions": [
                      {
                        "type": "unnest",
                        "key": "dataResponse.resultData"
                      }
                    ]
                  },
                  "errorDetectors": [
                    {
                      "type": "ifKeyExists",
                      "key": [
                        "errorResponse"
                      ]
                    },
                    {
                      "type": "ifKeyExists",
                      "key": [
                        "errorInfo"
                      ]
                    }
                  ]
                }
              ]
            },
            {
               "name": "mocksvc",
               "serviceInfo": "mocksvc:99999-1.0",
               "functions": [
                 {
                   "name": "date_request",
                   "columns": [
                     {
                       "columnName": "response_date",
                       "columnType": "DATE",
                       "nullable": false,
                       "jsonKey": "dateResponse"
                     }
                   ],
                   "parameterColumns": [
                     {
                       "columnName": "request_date",
                       "columnType": "DATE",
                       "nullable": false,
                       "jsonKey": "date"
                     }
                   ],
                   "requestTemplate": "mocksvc.date_request.json.jinja2",
                   "responseTransforms": {
                     "actions": []
                   },
                   "errorDetectors": []
                 },
                 {
                   "name": "limit_request",
                   "columns": [
                     {
                       "columnName": "data",
                       "columnType": "INT",
                       "nullable": false,
                       "jsonKey": "limitResponse.data"
                     }
                   ],
                   "parameterColumns": [],
                   "requestTemplate": "mocksvc.limit_request.json.jinja2",
                   "responseTransforms": {
                     "actions": [
                        {
                          "type": "unnest",
                          "key": "limitResponse.data"
                        }
                     ]
                   },
                   "errorDetectors": []
                 }
              ]
            }]
            """;

    private static final String CALCRT_FIELDS_REQUEST_TEMPLATE = """
            {
              "doYkRequestTyped": {
                "crRequest": {
                  "userPreferences": {
                    "byUuid": {
                      "uuid": {{ uuid }}
                    }
                  }
                },
                "ykSecuritySpecs": [
                  {% for security in securities %}
                  {
                    "securityId": {
                      "secname": "{{ security }}"
                    },
                    "fieldSpecs": [
                      {% for field in fields %}
                      {
                        "fieldId": {
                          "fieldNameDefault": "{{ field }}"
                        }
                      }{{ ',' if not loop.last or field_mnemonics}}
                      {% endfor %}
                      {% for field_mnemonic in field_mnemonics %}
                      {
                        "fieldId": {
                          "fieldNameMnemonic": "{{ field_mnemonic }}"
                        }
                      }{{ ',' if not loop.last}}
                      {% endfor %}
                    ],
                    "overrideSpecs": [
                      {% for overrideId, overrideVal in overrides.items() %}
                      {
                        "overrideId": {
                          "overrideNameDefault": "{{ overrideId }}"
                        },
                        "overrideValue": {
                          "stringVal": "{{ overrideVal }}"
                        }
                      }{{ ',' if not loop.last or override_mnemonics}}
                      {% endfor %}
                      {% for overrideMnemonic, overrideVal in override_mnemonics.items() %}
                      {
                        "overrideId": {
                          "overrideNameMnemonic": "{{ overrideMnemonic }}"
                        },
                        "overrideValue": {
                          "stringVal": "{{ overrideVal }}"
                        }
                      }{{ ',' if not loop.last}}
                      {% endfor %}
                    ]
                  }{{ ',' if not loop.last }}
                  {% endfor %}
                ]
              }
            }
            """;

    private static final String ETSDSPBISVC_REQUEST_TEMPLATE = """
            {
               "dataRequest": {
                 "startDate": "{{ startDate or '1970-01-01'}}",
                 "endDate": "{{ endDate or '1970-01-01'}}",
                 "resultsLimit": {{
                     [resultsLimit | int, limit | int] | sort | first if (resultsLimit is not none) and (limit is not none)
                     else resultsLimit if (resultsLimit is not none)
                     else limit if (limit is not none)
                     else -1
                 }},
                 "predicate": "{{ predicate if predicate is not none else '' }}"
               }
            }
            """;

    private static final String MOCKSVC_DATE_REQUEST_TEMPLATE = """
            {
                "dateRequest": "{{ date }}"
            }
            """;

    private static final String MOCKSVC_LIMIT_REQUEST_TEMPLATE = """
            {
                "limitRequest": {
                  {% if limit is defined %}
                  "limit": {{ limit }}
                  {% endif %}
                }
            }
            """;

    @TempDir
    static Path temporaryDirectory;

    private QueryRunner runner;
    private TestingBasService<com.bloomberg.codegen.crsrvpx.Request, com.bloomberg.codegen.crsrvpx.Response> testingCalcrtService;
    private TestingBasService<com.bloomberg.codegen.etsdspbisvc.Request, com.bloomberg.codegen.etsdspbisvc.Response> testingEtsdspbisService;
    private TestingBasService<ObjectNode, ObjectNode> testingMockService;
    private TestingBasService<com.bloomberg.codegen.bxsdsvc.Request, com.bloomberg.codegen.bxsdsvc.Response> testingBxsdService;
    private TestingGroupProvider testingGroupProvider;

    @BeforeAll
    void setup(@BasRouterExtension.Dynamic InetSocketAddress serviceAddress, @BasRouterExtension.Client InetSocketAddress clientAddress)
            throws Exception
    {
        String mocksvcSchema = Resources.toString(Resources.getResource("mocksvc-1.0.xsd"), StandardCharsets.UTF_8);

        testingCalcrtService = new TestingBasService<>("crsrvpx:166616-1.30", com.bloomberg.codegen.crsrvpx.Request.class, com.bloomberg.codegen.crsrvpx.Response.class,
                serviceAddress);
        testingEtsdspbisService = new TestingBasService<>("etsdspbisvc:390196-1.0", com.bloomberg.codegen.etsdspbisvc.Request.class,
                com.bloomberg.codegen.etsdspbisvc.Response.class, serviceAddress);
        testingMockService = new TestingBasService<>(CharSource.wrap(mocksvcSchema).asByteSource(StandardCharsets.UTF_8).openStream(), "mocksvc:99999-1.0", ObjectNode.class,
                ObjectNode.class,
                serviceAddress);
        testingBxsdService = new TestingBasService<>("bxsdsvc:171365-1.7", com.bloomberg.codegen.bxsdsvc.Request.class, com.bloomberg.codegen.bxsdsvc.Response.class,
                serviceAddress);

        // The BAS connector dynamically gets schemas from bxsdsvc.
        // The schema for mocksvc needs to be injected since 99999 may be associated with a real service
        // When this request handler throws an exception the Bas Router will go to the next service (the real DEV one)
        testingBxsdService.setRequestHandler(request -> {
            if (!request.getGetDocument().getService().getId().equals(99999)) {
                throw new IllegalArgumentException("Can only handle mocksvc schemas");
            }

            return new com.bloomberg.codegen.bxsdsvc.Response().withGetDocument(new GetDocumentResult().withSuccess(
                    new Document()
                            .withText(mocksvcSchema)));
        });

        Path calcrtFieldsRequestTemplatePath = temporaryDirectory.resolve("calcrt.fields_request.json.jinja2");
        asCharSink(calcrtFieldsRequestTemplatePath, StandardCharsets.UTF_8).write(CALCRT_FIELDS_REQUEST_TEMPLATE);

        Path etsdspbisvcRequestTemplatePath = temporaryDirectory.resolve("etsdsp.data_request.json.jinja2");
        asCharSink(etsdspbisvcRequestTemplatePath, StandardCharsets.UTF_8).write(ETSDSPBISVC_REQUEST_TEMPLATE);

        Path mocksvcRequestTemplatePath = temporaryDirectory.resolve("mocksvc.date_request.json.jinja2");
        asCharSink(mocksvcRequestTemplatePath, StandardCharsets.UTF_8).write(MOCKSVC_DATE_REQUEST_TEMPLATE);

        Path mocksvcLimitRequestTemplatePath = temporaryDirectory.resolve("mocksvc.limit_request.json.jinja2");
        asCharSink(mocksvcLimitRequestTemplatePath, StandardCharsets.UTF_8).write(MOCKSVC_LIMIT_REQUEST_TEMPLATE);

        Path metadataJsonPath = temporaryDirectory.resolve("metadata.json");
        asCharSink(metadataJsonPath, StandardCharsets.UTF_8).write(BAS_CONNECTOR_METADATA);

        runner = DistributedQueryRunner.builder(testSessionBuilder().setIdentity(Identity.forUser("test-user").build()).setCatalog("bsql").setSchema("").build()).build();
        runner.installPlugin(new BasPlugin());
        runner.createCatalog("bsql", "bas", ImmutableMap.of(
                "host", clientAddress.getHostString(),
                "uuid-group-pattern", "employeeid=([0-9]*)",
                "metadata", metadataJsonPath.toString(),
                "generic-uuid", "30827527"));
        testingGroupProvider = new TestingGroupProvider();
        runner.getGroupProvider().setConfiguredGroupProvider(testingGroupProvider);
    }

    @AfterEach
    void clear()
    {
        testingEtsdspbisService.clear();
        testingCalcrtService.clear();
        testingMockService.clear();
    }

    @AfterAll
    void close()
            throws Exception
    {
        Closeables.closeAll(testingCalcrtService, testingEtsdspbisService, testingMockService, testingBxsdService, runner);
    }

    @Test
    void testCalcrtRequest()
    {
        long uuid = 42;
        testingGroupProvider.setUserGroups(ImmutableMap.of("test-user", ImmutableSet.of("employeeid=" + uuid)));

        testingCalcrtService.setRequestHandler(request ->
                new com.bloomberg.codegen.crsrvpx.Response().withCalcrtResponse(new CrsvcResponse().withYkresponse(
                        new YkResponse()
                                .withResponseStatus(DataStatus.DATA_SUCCESS)
                                .withRequestStatus(CrRequestStatus.REQUEST_SUCCESS)
                                .withRcParmcm(0)
                                .withResponseFrom("calcrt")
                                .withResponseDatas(
                                        createYkSecurityData("IBM US Equity", createDoubleYkFieldIdData("PR005", 255.24))))));

        MaterializedResult result = runner.execute("""
                select
                    security_name, calcrt_field_name, calcrt_field_str, calcrt_field_dbl
                from
                    bsql.calcrt.ykdata
                where
                    fields=ARRAY['PR005'] and
                    securities=ARRAY['IBM US Equity']
                """);
        List<MaterializedRow> rows = result.getMaterializedRows();

        assertThat(testingCalcrtService.getRequests()).hasSize(1).first().satisfies(request -> {
            assertThat(request.getDoYkRequestTyped().getCrRequest().getUserPreferences().getByUuid().getUuid()).isEqualTo(uuid);
            List<SecuritySpec> securities = request.getDoYkRequestTyped().getYkSecuritySpecs();
            assertThat(securities).hasSize(1);
            SecuritySpec ibmSecuritySpec = securities.getFirst();
            assertThat(ibmSecuritySpec.getSecurityId().getSecname()).isEqualTo("IBM US Equity");
            assertThat(ibmSecuritySpec.getFieldSpecs()).hasSize(1).first().extracting(FieldSpec::getFieldId).extracting(FieldId::getFieldNameDefault).isEqualTo("PR005");
            assertThat(ibmSecuritySpec.getOverrideSpecs()).isEmpty();
        });
        assertThat(rows).hasSize(1).first().extracting(MaterializedRow::getFields).isEqualTo(ImmutableList.of("IBM US Equity", "PR005", "255.24", "255.24"));
    }

    @Test
    void testCalcrRequestValueEncoding()
    {
        long uuid = 44;
        testingGroupProvider.setUserGroups(ImmutableMap.of("test-user", ImmutableSet.of("employeeid=" + uuid)));

        testingCalcrtService.setRequestHandler(request ->
                new com.bloomberg.codegen.crsrvpx.Response().withCalcrtResponse(new CrsvcResponse().withYkresponse(
                        new YkResponse()
                                .withResponseStatus(DataStatus.DATA_SUCCESS)
                                .withRequestStatus(CrRequestStatus.REQUEST_SUCCESS)
                                .withRcParmcm(0)
                                .withResponseFrom("calcrt")
                                .withResponseDatas(
                                        createYkSecurityData("LUACTRUU Index",
                                                createDoubleYkFieldIdData("VL348", Double.NEGATIVE_INFINITY),
                                                createDoubleYkFieldIdData("RK398", 0.2422359),
                                                createStringYkFieldIdData("DS010", "彭博美国公司债总回报指数(价")),
                                        createYkSecurityData("H29324JP Index",
                                                createDoubleYkFieldIdData("VL348", Double.NEGATIVE_INFINITY),
                                                createDoubleYkFieldIdData("RK398", Double.NaN),
                                                createStringYkFieldIdData("DS010", "Bloomberg EM Local Govt Ex K")),
                                        createYkSecurityData("VCITIV Index",
                                                createDoubleYkFieldIdData("VL348", Double.NEGATIVE_INFINITY),
                                                createDoubleYkFieldIdData("RK398", 0.2243054),
                                                createStringYkFieldIdData("DS010", "先锋中期公司债ETF份额参考净"))))));

        MaterializedResult result = runner.execute("""
                SELECT
                    security_name, calcrt_field_name, calcrt_field_str, calcrt_field_dbl
                FROM
                    bsql.calcrt.ykdata
                WHERE
                    fields=ARRAY['VL348', 'RK398', 'DS010'] AND
                    securities=ARRAY['LUACTRUU Index', 'H29324JP Index', 'VCITIV Index']
                """);
        List<MaterializedRow> rows = result.getMaterializedRows();

        assertThat(testingCalcrtService.getRequests()).hasSize(1).first().satisfies(request -> {
            assertThat(request.getDoYkRequestTyped().getCrRequest().getUserPreferences().getByUuid().getUuid()).isEqualTo(uuid);
            List<SecuritySpec> securities = request.getDoYkRequestTyped().getYkSecuritySpecs();
            assertThat(securities).hasSize(3).satisfies(requestedSecurities -> {
                assertThat(requestedSecurities).extracting(SecuritySpec::getSecurityId).extracting(SecurityId::getSecname).containsExactly("LUACTRUU Index", "H29324JP Index",
                        "VCITIV Index");
                assertThat(requestedSecurities).allSatisfy(requestedSecurity ->
                        assertThat(requestedSecurity).extracting(SecuritySpec::getFieldSpecs).asInstanceOf(InstanceOfAssertFactories.list(FieldSpec.class)).hasSize(3).extracting(FieldSpec::getFieldId).extracting(FieldId::getFieldNameDefault)
                                .containsExactly("VL348", "RK398", "DS010"));
            });
        });
        assertThat(rows).hasSize(9).extracting(MaterializedRow::getFields).containsExactlyInAnyOrder(
                ImmutableList.of("LUACTRUU Index", "VL348", "-Infinity", "-Infinity"),
                ImmutableList.of("LUACTRUU Index", "RK398", "0.2422359", "0.2422359"),
                ImmutableList.of("LUACTRUU Index", "DS010", "彭博美国公司债总回报指数(价", "0.0"),
                ImmutableList.of("H29324JP Index", "VL348", "-Infinity", "-Infinity"),
                ImmutableList.of("H29324JP Index", "RK398", "NaN", "NaN"),
                ImmutableList.of("H29324JP Index", "DS010", "Bloomberg EM Local Govt Ex K", "0.0"),
                ImmutableList.of("VCITIV Index", "VL348", "-Infinity", "-Infinity"),
                ImmutableList.of("VCITIV Index", "RK398", "0.2243054", "0.2243054"),
                ImmutableList.of("VCITIV Index", "DS010", "先锋中期公司债ETF份额参考净", "0.0")
        );
    }

    private static YkSecurityData createYkSecurityData(String tickerName, YkFieldIdData... ykFieldIdDatum)
    {
        return new YkSecurityData()
                .withSecId(new SecurityId().withSectype(SecurityType.SECURITY_TYPE_USER_STRING).withSecname(tickerName))
                .withStatus(SecDataStatus.SECDATA_SUCCESS)
                .withCalcrtRetCode(0)
                .withFieldIdDatas(ykFieldIdDatum)
                .withUsrPtr(BigInteger.ZERO);
    }

    private static YkFieldIdData createDoubleYkFieldIdData(String fieldName, double doubleValue)
    {
        return new YkFieldIdData()
                .withFieldId(new FieldId().withFieldNameDefault(fieldName))
                .withFieldData(new YkFieldData()
                        .withDoubleV(doubleValue)
                        .withStringV(Double.toString(doubleValue))
                        .withValue(new YkFieldDataValue().withDoubleV(doubleValue)))
                .withUsrPtr(BigInteger.ZERO)
                .withPrimaryRcode(CrFieldInfoPrimaryRcode.CR_FIELD_INFO_SUCCESS);
    }

    private static YkFieldIdData createStringYkFieldIdData(String fieldName, String stringValue)
    {
        return new YkFieldIdData()
                .withFieldId(new FieldId().withFieldNameDefault(fieldName))
                .withFieldData(new YkFieldData()
                        .withDoubleV(0)
                        .withStringV(stringValue)
                        .withValue(new YkFieldDataValue().withStringV(stringValue)))
                .withUsrPtr(BigInteger.ZERO)
                .withPrimaryRcode(CrFieldInfoPrimaryRcode.CR_FIELD_INFO_SUCCESS);
    }

    @Test
    void testEtsdspbiRequest()
    {
        int uuid = 47;
        testingGroupProvider.setUserGroups(ImmutableMap.of("test-user", ImmutableSet.of("employeeid=" + uuid)));

        testingEtsdspbisService.setRequestHandler((_) ->
                new com.bloomberg.codegen.etsdspbisvc.Response()
                        .withDataResponse(new DataResponse()
                                .withResultDatas(new ResultData()
                                        .withBaseProductDesc("{abnum:\"3739\", }"))));

        MaterializedResult result = runner.execute("""
                    select base_product_desc from bsql.etsdspbisvc.trade_store_fi_restricted
                    where startDate=date'2024-03-01'
                    and endDate=date'2024-03-31'
                    and resultsLimit=50
                    and predicate='S|V1|select abroker_number as "abnum" from trade_store_fi_restricted where partition_date=''2024-01-01'' limit 1'
                """);
        List<MaterializedRow> rows = result.getMaterializedRows();

        assertThat(testingEtsdspbisService.getRequests()).hasSize(1).first().satisfies(request -> {
            assertThat(request.getDataRequest().getStartDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 3, 1));
            assertThat(request.getDataRequest().getEndDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 3, 31));
            assertThat(request.getDataRequest().getResultsLimit()).isEqualTo(50);
            assertThat(request.getDataRequest().getPredicate()).isEqualToIgnoringWhitespace("""
                    S|V1|select abroker_number as "abnum" from trade_store_fi_restricted where partition_date='2024-01-01' limit 1
                    """);
        });
        assertThat(rows).hasSize(1).first().extracting(MaterializedRow::getFields).isEqualTo(ImmutableList.of("{abnum:\"3739\", }"));
    }

    @Test
    void testEtsdspbiMultilineStringRequest()
    {
        int uuid = 47;
        testingGroupProvider.setUserGroups(ImmutableMap.of("test-user", ImmutableSet.of("employeeid=" + uuid)));

        testingEtsdspbisService.setRequestHandler((_) ->
                new com.bloomberg.codegen.etsdspbisvc.Response()
                        .withDataResponse(new DataResponse()));

        runner.execute("""
                    select base_product_desc from bsql.etsdspbisvc.trade_store_fi_restricted
                    where startDate=date'2024-03-01'
                    and endDate=date'2024-03-31'
                    and resultsLimit=50
                    and predicate='S|V1|
                        select abroker_number as "abnum"
                        from trade_store_fi_restricted
                        where partition_date=''2024-01-01'' limit 1'
                """);

        assertThat(testingEtsdspbisService.getRequests()).hasSize(1).first().satisfies(request ->
                assertThat(request.getDataRequest().getPredicate()).isEqualToIgnoringWhitespace("""
                        S|V1|select abroker_number as "abnum" from trade_store_fi_restricted where partition_date='2024-01-01' limit 1
                        """));
    }

    @Test
    void testEtsdspbiRequestWithLimit()
    {
        int uuid = 47;
        testingGroupProvider.setUserGroups(ImmutableMap.of("test-user", ImmutableSet.of("employeeid=" + uuid)));

        testingEtsdspbisService.setRequestHandler((_) ->
                new com.bloomberg.codegen.etsdspbisvc.Response()
                        .withDataResponse(new DataResponse()));

        // No limit
        runner.execute("""
                    select base_product_desc from bsql.etsdspbisvc.trade_store_fi_restricted
                    where startDate=date'2024-03-01'
                    and endDate=date'2024-03-31'
                    and predicate=''
                """);

        // ResultsLimit
        runner.execute("""
                    select base_product_desc from bsql.etsdspbisvc.trade_store_fi_restricted
                    where startDate=date'2024-03-01'
                    and endDate=date'2024-03-31'
                    and resultsLimit=1337
                    and predicate=''
                """);

        // SQL LIMIT
        runner.execute("""
                    select base_product_desc from bsql.etsdspbisvc.trade_store_fi_restricted
                    where startDate=date'2024-03-01'
                    and endDate=date'2024-03-31'
                    and predicate=''
                    limit 42
                """);

        // Both limits
        runner.execute("""
                    select base_product_desc from bsql.etsdspbisvc.trade_store_fi_restricted
                    where startDate=date'2024-03-01'
                    and endDate=date'2024-03-31'
                    and predicate=''
                    and resultsLimit=1337
                    limit 42
                """);

        assertThat(testingEtsdspbisService.getRequests()).hasSize(4).extracting(request -> request.getDataRequest().getResultsLimit().intValue())
                .containsExactlyInAnyOrder(-1, 1337, 42, 42);
    }

    @Test
    void testMocksvcDateRequest()
    {
        testingMockService.setRequestHandler((_) -> {
            try {
                return new ObjectMapper().readValue("""
                        {"dateResponse": "2025-02-03+01:00"}
                        """, ObjectNode.class);
            }
            catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        MaterializedResult result = runner.execute("""
                    select response_date from bsql.mocksvc.date_request
                    where request_date = date '2025-1-2'
                """);

        assertThat(testingMockService.getRequests()).hasSize(1).first().satisfies(request ->
                assertThat(request.get("dateRequest").asText()).isEqualTo("2025-01-02+13:00"));

        assertThat(result.getMaterializedRows()).hasSize(1).first().extracting(MaterializedRow::getFields).isEqualTo(ImmutableList.of(LocalDate.of(2025, 2, 3)));
    }

    @Test
    void testMocksvcLimitRequestWithLimit()
    {
        testingMockService.setRequestHandler((_) -> {
            try {
                return new ObjectMapper().readValue("""
                        {
                          "limitResponse": {
                            "data": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
                          }
                        }
                        """, ObjectNode.class);
            }
            catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        MaterializedResult result = runner.execute("""
                    select * from bsql.mocksvc.limit_request limit 5
                """);

        assertThat(testingMockService.getRequests()).hasSize(1).first().satisfies(request -> {
            assertThat(request.get("limitRequest").has("limit")).isTrue();
            assertThat(request.get("limitRequest").get("limit").canConvertToLong()).isTrue();
            // Jackson returns 0 for a longValue call on a JsonNode that is not convertable to long, so we do extra checks
            assertThat(request.get("limitRequest").get("limit").longValue()).isEqualTo(5);
        });

        assertThat(result.getMaterializedRows()).hasSize(5);
    }

    @Test
    void testMocksvcLimitRequestWithoutLimit()
    {
        testingMockService.setRequestHandler((_) -> {
            try {
                return new ObjectMapper().readValue("""
                        {
                          "limitResponse": {
                            "data": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
                          }
                        }
                        """, ObjectNode.class);
            }
            catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        MaterializedResult result = runner.execute("""
                    select * from bsql.mocksvc.limit_request
                """);

        assertThat(testingMockService.getRequests()).hasSize(1).first().satisfies(request ->
                assertThat(request.get("limitRequest").has("limit")).isFalse());

        assertThat(result.getMaterializedRows()).hasSize(10).extracting(MaterializedRow::getFields)
                .containsExactlyElementsOf(IntStream.range(0, 10).mapToObj(ImmutableList::<Object>of).collect(toImmutableList()));
    }
}
