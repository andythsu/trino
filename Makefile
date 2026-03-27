# Erik Makefile. This isnt open source!

.PHONY: depends

BASES = rpm

default: $(BASES)

export TRINO_USERNAME_ENC=U2FsdGVkX1+6G6S9TS8MoDidECDtDXKEnoYM0u83WUM=
export TRINO_PASSWORD_ENC=U2FsdGVkX1+UGhC4u5ngl1h0v6uwUCQb6PizZJrWrDpm/F1Vx/IDbnis3dHFtwIT

export TRINO2TRINO_URL=jdbc:trino://sql.dev.bloomberg.com:443/nsd_postgres?explicitPrepare=false
export TRINO_USERNAME:=$(shell echo "$$TRINO_USERNAME_ENC" | openssl enc -d -aes-256-cbc -a -pbkdf2 -iter 69 -pass pass:$$ENCRYPTION_SECRET)
export TRINO_PASSWORD:=$(shell echo "$$TRINO_PASSWORD_ENC" | openssl enc -d -aes-256-cbc -a -pbkdf2 -iter 69 -pass pass:$$ENCRYPTION_SECRET)

guard-%:
	@ if [ "${${*}}" = "" ]; then \
		echo "Environment variable $* not set"; \
		exit 1; \
	fi

pull:
	docker pull artprod.dev.bloomberg.com/dataops/bnef_analyst_base_intellij:latest

compile:
	mvn -e -X compile -DskipTests -pl '!docs' -s artifactory-settings.xml 2>&1 | tee compile.txt

rpm:
	@start=$$(date +%s); \
	rm -f build.txt; \
	rm -rf bloomberg/datalake-build/target; \
	mvn -e -X install -DskipTests -pl '!docs' -s artifactory-settings.xml -T 1C 2>&1 | tee build.txt; \
	end=$$(date +%s); \
	printf "Total time: %d seconds\n" $$((end-start))

package:
	mvn -e -X package -DskipTests -pl '!docs' -s artifactory-settings.xml -T 1C 2>&1 | tee build.txt

# Compiles without the style checks
nocheck:
	rm -f build.txt
	rm -rf bloomberg/datalake-build/target
	mvn -e -X install -DskipTests -Dair.check.skip-all -pl '!docs' -s artifactory-settings.xml 2>&1 | tee build.txt

main:
	rm -f build.txt
	rm -rf core/trino-server-rpm/target
	mvn -e -X install -DskipTests -Dair.check.skip-all -pl '!docs' -rf :trino-main 2>&1 | tee build.txt

# To fix the an annoying issue with the pom.xml files not being sorted
sort:
    mvn sortpom:sort -s artifactory-settings.xml

odbc:
	rm -f build_odbc.txt
	mvn -e -X install -DskipTests -pl core/trino-server-main -s artifactory-settings.xml 2>&1 | tee build_odbc.txt

bloomberg:
	rm -f build_bloomberg.txt
	mvn -e -X install -DskipTests -pl bloomberg -s artifactory-settings.xml 2>&1 | tee build_bloomberg.txt

ranger:
	rm -f build_ranger.txt
	mvn -e -X install -DskipTests -pl plugin/trino-ranger -s artifactory-settings.xml 2>&1 | tee build_ranger.txt

comdb2:
	rm -f build_ranger.txt
	mvn -e -X install -DskipTests -pl plugin/trino-comdb2 -s artifactory-settings.xml >&1 | tee build_comdb2.txt

snowflake:
	rm -f build_snowflake.txt
	mvn -e -X install -DskipTests -pl plugin/trino-snowflake -s artifactory-settings.xml 2>&1 | tee build_snowflake.txt

starburst:
	rm -f build_starburst.txt
	mvn -e -X install -DskipTests -pl starburst/starburst-protocol -s artifactory-settings.xml 2>&1 | tee build_starburst.txt

datalakemain:
	rm -f build_datalakemain.txt
	mvn -e -X install -DskipTests -pl bloomberg/datalake-main -s artifactory-settings.xml 2>&1 | tee build_datalakemain.txt

datalakebuild:
	rm -f build_datalakbuild.txt
	mvn -e -X install -DskipTests -pl bloomberg/datalake-build -s artifactory-settings.xml 2>&1 | tee build_datalakbuild.txt

userattriute:
	rm -f build_userattriute.txt
	mvn -e -X install -DskipTests -pl bloomberg/plugins/datalake-user-attribute-provider -s artifactory-settings.xml 2>&1 | tee build_datalakemain.txt

bas:
	rm -f build_bas.txt
	mvn -e -X install -DskipTests -Dair.check.skip-all -Dmaven.experimental.buildconsumer=false -T 1 -pl bloomberg/lib/datalake-bas-reactor-bom,bloomberg/plugins/datalake-bas -s artifactory-settings.xml 2>&1 | tee build_bas.txt

dependsbas:
	mvn dependency:tree -pl bloomberg/lib/datalake-bas-reactor-bom,bloomberg/plugins/datalake-bas -s artifactory-settings.xml -Dverbose 2>&1 | tee dependencybas.txt

opa:
	rm -f build_opa.txt
	mvn -e -X install -DskipTests -pl plugin/trino-opa -s artifactory-settings.xml 2>&1 | tee build_opa.txt

geo:
	rm -f build_geo.txt
	mvn -e -X install -DskipTests -pl 'core/trino-spi,core/trino-parser,core/trino-grammar,lib/trino-array,lib/trino-cache,lib/trino-matching,lib/trino-geospatial-toolkit,lib/trino-hdfs,lib/trino-filesystem,lib/trino-memory-context,lib/trino-plugin-toolkit,core/trino-main,testing/trino-testing-services,testing/trino-testing-containers,client/trino-client,plugin/trino-exchange-filesystem,plugin/trino-geospatial,plugin/trino-geo-buffer' -s artifactory-settings.xml 2>&1 | tee build_geo.txt

hudi:
	rm -f build_hudi.txt
	mvn -e -X install -DskipTests -pl plugin/trino-hudi -s artifactory-settings.xml 2>&1 | tee build_hudi.txt

datalakeserver:
	rm -f build_datalakeserver.txt
	mvn -e -X install -DskipTests -pl bloomberg/datalake-server-dev -s artifactory-settings.xml 2>&1 | tee build_datalakeserver.txt

opa:
	rm -f build_opa.txt
	mvn -e -X install -DskipTests -pl plugin/trino-opa -s artifactory-settings.xml 2>&1 | tee build_opa.txt

opensearch:
	rm -f build_opensearch.txt
	mvn -e -X install -DskipTests -pl plugin/trino-opensearch -s artifactory-settings.xml 2>&1 | tee build_opensearch.txt

resumehudi:
	rm -f build_hudi.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :trino-hudi -s artifactory-settings.xml 2>&1 | tee build_hudi.txt

resumesnow:
	rm -f build_snowflake.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :trino-snowflake -s artifactory-settings.xml 2>&1 | tee build_snowflake.txt

resumeranger:
	rm -f build_ranger.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :trino-ranger -s artifactory-settings.xml 2>&1 | tee build_ranger.txt

resumeopa:
	rm -f build_opa.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :trino-opa -s artifactory-settings.xml 2>&1 | tee build_opa.txt

resumecomdb2:
	rm -f build_comdb2.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :trino-comdb2 -s artifactory-settings.xml 2>&1 | tee build_comdb2.txt

resumet2t:
	rm -f build_t2t.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :trino-trino -s artifactory-settings.xml 2>&1 | tee build_t2t.txt

resumehive:
	rm -f build_hive.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :trino-hive -s artifactory-settings.xml 2>&1 | tee build_hive.txt

resumedatalakeserver:
	rm -f build_datalakeserver.txt
	mvn -e -X install -DskipTests -pl '!docs' -rf :datalake-server-dev -s artifactory-settings.xml 2>&1 | tee build_datalakeserver.txt

resumepackaging:
	rm -f build_packaging.txt
	mvn -e -X install -DskipTests -Dair.check.skip-all -pl '!docs' -rf :datalake-build -s artifactory-settings.xml 2>&1 | tee build_datalakeserver.txt

resumebas:
	rm -f build_bas.txt
	mvn -e -X install -DskipTests -Dair.check.skip-all -pl '!docs' -T 1 -rf :datalake-bas -s artifactory-settings.xml 2>&1 | tee build_bas.txt

resumeuserattribute:
	rm -f build_userattriute.txt
	mvn -e -X install -DskipTests -Dair.check.skip-all -pl '!docs' -T 1 -rf :datalake-user-attribute-provider -s artifactory-settings.xml 2>&1 | tee build_userattriute.txt

# This command requires xmlstarlet to be installed
clean:
	mvn -e -X clean -s artifactory-settings.xml
	rm -rf ~/.m2/repository/com/bloomberg/bas-reactor
	find . -type d -name target -exec rm -rf {} \;
	@version=$$( \
    	    xmlstarlet sel \
    	      -N pom="http://maven.apache.org/POM/4.0.0" \
    	      -t \
    	      -v "/pom:project/pom:version" \
    	      -n \
    	      pom.xml \
    	) && \
    	echo "Looking for Maven cache dirs for version $$version…" && \
    	find ~/.m2/repository/com/bloomberg ~/.m2/repository/io/trino \
    	     -type d -name "$$version" -exec rm -rf {} \;

attach:
	docker exec -it ${USER}-intellij bash

bash:
	/usr/local/bin/startup_intellij.sh bash

ui:
	/usr/local/bin/startup_intellij.sh

depends:
	@pattern=$(filter-out depends,$(MAKECMDGOALS)); \
	if [ -z "$$pattern" ]; then \
	  echo "No pattern: running full dependency tree…"; \
	  mvn dependency:tree \
	      -s artifactory-settings.xml \
	      -Dverbose \
	      2>&1 | tee dependency.txt; \
	else \
	  echo "Filtering on includes=*:$$pattern…"; \
	  mvn dependency:tree \
	      -s artifactory-settings.xml \
	      -Dincludes=*:$$pattern \
	      -Dverbose \
	      2>&1 | tee dependency.txt; \
	fi


# Tries to figure out what the dependencies of a plugin is and copy to the plugin/"connectorname"/target/dependency directory
dependst2t:
	mvn dependency:copy-dependencies -pl :trino-trino -s artifactory-settings.xml -Dverbose 2>&1 | tee dependencyt2t.txt

testhive:
	rm -f testhive.txt
	mvn test -pl ':trino-hive' 2>&1 | tee testhive.txt

testhive30:
	rm -f testhive30.txt
	mvn test -pl ':trino-hive' -Dtest="TestHive3OnDataLake" 2>&1 | tee testhive30.txt

# When already inside the intellij docker container, run this to bring up IntelliJ
intellij:
	/opt/intellij/bin/idea.sh

cli:
	client/trino-cli/target/trino-cli-374-executable.jar  --server=http://localhost:8081 --user ${USER}


define ARTIFACTORY_CONFIG
<?xml version="1.0" encoding="UTF-8"?>
<settings xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd" xmlns="http://maven.apache.org/SETTINGS/1.1.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <proxies>
    <proxy>
      <id>internal-http</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>proxy.bloomberg.com</host>
      <port>81</port>
      <nonProxyHosts>*.bloomberg.com</nonProxyHosts>
    </proxy>
    <proxy>
      <id>internal-https</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>proxy.bloomberg.com</host>
      <port>81</port>
      <nonProxyHosts>*.bloomberg.com</nonProxyHosts>
    </proxy>
  </proxies>
  <mirrors>
     <mirror>
        <mirrorOf>*</mirrorOf>
        <name>repo</name>
        <url>http://artprod.dev.bloomberg.com/artifactory/root-repos</url>
        <id>repo</id>
     </mirror>
  </mirrors>
  <profiles>
    <profile>
      <repositories>
        <repository>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
          <id>central</id>
          <name>libs-release</name>
          <url>http://artprod.dev.bloomberg.com/artifactory/libs-release</url>
        </repository>
        <repository>
          <snapshots />
          <id>snapshots</id>
          <name>libs-snapshot</name>
          <url>http://artprod.dev.bloomberg.com/artifactory/libs-snapshot</url>
        </repository>
        <repository>
          <snapshots />
          <id>snapshots</id>
          <name>java-local-remotes</name>
          <url>http://artprod.dev.bloomberg.com/artifactory/java-local-remotes</url>
        </repository>
        <repository>
          <id>confluent</id>
          <name>confluent_io_maven</name>
          <url>https://artprod.dev.bloomberg.com/artifactory/remote-repos</url>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
          <id>central</id>
          <name>plugins-release</name>
          <url>http://artprod.dev.bloomberg.com/artifactory/plugins-release</url>
        </pluginRepository>
        <pluginRepository>
          <snapshots />
          <id>snapshots</id>
          <name>plugins-release</name>
          <url>http://artprod.dev.bloomberg.com/artifactory/plugins-release</url>
        </pluginRepository>
      </pluginRepositories>
      <id>artifactory</id>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>artifactory</activeProfile>
  </activeProfiles>
</settings>
endef

define TRINO_CONFIG
node.id=ffffffff-ffff-ffff-ffff-ffffffffffff
node.environment=test
node.internal-address=localhost
experimental.concurrent-startup=true
http-server.http.port=8080

discovery.uri=http://localhost:8080

exchange.http-client.max-connections=1000
exchange.http-client.max-connections-per-server=1000
exchange.http-client.connect-timeout=1m
exchange.http-client.idle-timeout=1m

scheduler.http-client.max-connections=1000
scheduler.http-client.max-connections-per-server=1000
scheduler.http-client.connect-timeout=1m
scheduler.http-client.idle-timeout=1m

query.client.timeout=5m
query.min-expire-age=30m

plugin.bundles= \\
  ../../plugin/trino-resource-group-managers/pom.xml, \\
  ../../plugin/trino-password-authenticators/pom.xml, \\
  ../../plugin/trino-exchange-filesystem/pom.xml, \\
  ../../plugin/trino-iceberg/pom.xml, \\
  ../../plugin/trino-thrift/pom.xml, \\
  ../../plugin/trino-ranger/pom.xml, \\
  ../../plugin/trino-snowflake/pom.xml, \\
  ../../plugin/trino-hive-hadoop2/pom.xml, \\
  ../../plugin/trino-postgresql/pom.xml, \\
  ../../plugin/trino-bas/pom.xml, \\
  ../../plugin/trino-hudi/pom.xml

node-scheduler.include-coordinator=true
# Disable ranger below
#access-control.config-files=${PWD}/testing/trino-server-dev/etc/access-control-ranger.properties
endef

define ICEBERG_PROP
connector.name=iceberg
hive.metastore.uri=$${ENV:THRIFT_URL}
hive.parquet.use-column-names=true
hive.recursive-directories=true
hive.s3.aws-access-key=$${ENV:MINIO_ACCESS_KEY}
hive.s3.aws-secret-key=$${ENV:MINIO_SECRET_KEY}
hive.s3.endpoint=$${ENV:MINIO_ENDPOINT}
hive.max-partitions-per-writers=10000
hive.collect-column-statistics-on-write=true
endef

define HIVE_PROP
connector.name=hive
hive.metastore.uri=$${ENV:THRIFT_URL}
hive.parquet.use-column-names=true
hive.recursive-directories=true
hive.s3.aws-access-key=$${ENV:MINIO_ACCESS_KEY}
hive.s3.aws-secret-key=$${ENV:MINIO_SECRET_KEY}
hive.s3.endpoint=$${ENV:MINIO_ENDPOINT}
hive.allow-add-column=true
hive.allow-drop-column=true
hive.allow-drop-table=true
hive.allow-rename-table=true
hive.allow-comment-table=true
hive.allow-comment-column=true
hive.allow-rename-column=true
hive.max-partitions-per-writers=10000
hive.collect-column-statistics-on-write=true
endef

define POSTGRES_PROP
connector.name=postgresql
connection-url=$${ENV:POSTGRES_URL}
connection-user=$${ENV:POSTGRES_USER}
connection-password=$${ENV:POSTGRES_PASS}
endef

define BSQL_PROP
connector.name=bas
metadata=bas_configs/metadata.json
host=bas-web-dev.bdns.bloomberg.com
generic-uuid=6834118
uuid-group-pattern=employeeID=([0-9]*)
endef

define BAS_METADATA_CONFIG
[
  {
    "name": "tickerplant",
    "serviceInfo": "tadatasv:44663-1.41",
    "functions": [
      {
        "name": "tickdata",
        "columns": [
          {
            "columnName": "securityName",
            "columnType": "VARCHAR",
            "jsonKey": "timeSeriesDataResponse.resultData.resultDataItems.securityName",
            "nullable": false
          },
          {
            "columnName": "datetimetz",
            "columnType": "TIMESTAMP WITH TIME ZONE",
            "jsonKey": "timeSeriesDataResponse.resultData.resultDataItems.resultTimeSeriesDataInfo.resultTimeSeriesDataColumns.timeStamp.dateTimeTz",
            "nullable": false
          },
          {
            "columnName": "calcrt_field_name",
            "columnType": "VARCHAR",
            "jsonKey": "timeSeriesDataResponse.resultData.resultDataItems.resultTimeSeriesDataInfo.resultTimeSeriesDataColumns.timeSeriesDataColumn.fieldName"
          },
          {
            "columnName": "calcrt_field",
            "columnType": "DOUBLE",
            "jsonKey": "timeSeriesDataResponse.resultData.resultDataItems.resultTimeSeriesDataInfo.resultTimeSeriesDataColumns.timeSeriesDataColumn.typedValues.doubleValueList.doubleValues"
          }
        ],
        "parameterColumns": [
          {
            "columnName": "periodicity",
            "columnType": "INTEGER",
            "nullable": true,
            "jsonKey": "periodicity"
          },
          {
            "columnName": "businessDays",
            "columnType": "INTEGER",
            "nullable": true,
            "jsonKey": "numberOfMostRecentBusinessDays"
          },
          {
            "columnName": "startDate",
            "columnType": "TIMESTAMP WITH TIME ZONE",
            "nullable": true,
            "jsonKey": "startDate"
          },
          {
            "columnName": "endDate",
            "columnType": "TIMESTAMP WITH TIME ZONE",
            "nullable": true,
            "jsonKey": "endDate"
          },
          {
            "columnName": "pointsPerDay",
            "columnType": "INTEGER",
            "nullable": true,
            "jsonKey": "pointsPerDay"
          },
          {
            "columnName": "requestedFields",
            "columnType": "ARRAY(VARCHAR)",
            "jsonKey": "requestedFields"
          },
          {
            "columnName": "requestedSecurities",
            "columnType": "ARRAY(VARCHAR)",
            "jsonKey": "requestedSecurities"
          }
        ],
        "requestTemplate": "templates/tadata_fields_request_json.jinja2",
        "responseTransforms": {
          "actions": [
            {
              "type": "unnest",
              "key": "timeSeriesDataResponse.resultData.resultDataItems"
            },
            {
              "type": "unnest",
              "key": "timeSeriesDataResponse.resultData.resultDataItems.resultTimeSeriesDataInfo.resultTimeSeriesDataColumns.timeSeriesDataColumn"
            },
            {
              "type": "zip",
              "zippers": [
                {
                  "type": "singleKey",
                  "key": "timeSeriesDataResponse.resultData.resultDataItems.resultTimeSeriesDataInfo.resultTimeSeriesDataColumns.timeStamp.dateTimeTz"
                },
                {
                  "type": "singleKey",
                  "key": "timeSeriesDataResponse.resultData.resultDataItems.resultTimeSeriesDataInfo.resultTimeSeriesDataColumns.timeSeriesDataColumn.typedValues.doubleValueList.doubleValues"
                }
              ]
            }
          ]
        }
      }
    ]
  }
]
endef

define BAS_TADATA_JINJA
{
  "timeSeriesDataRequest":
  {
      "requestId": {{ range(1, 100000) | random }},
      "userInfo": {
        "uuid": {{ uuid }},
        "serialNum": 1344955,
        "workstationNum": 0,
        "serialNumMode": "USE_SERIAL_NUM"
      },
      "securityInfoList": {
        "list": [
      {% for security in requestedSecurities %}
          {
            "securityName": "{{ security }}",
            "timeSeriesFieldsList": {
              "list": {{ requestedFields | tojson }},
              "dataValuesFormat": "DATA_VALUE_VARIANT"
            },
            "dataRequestDateTimeInformation": {
              "startDateTimeInfo": {
                "dateTimeTz": "{{ startDate | bas_datetime or '0001-01-01T24:00:00.000+00:00' }}",
                "offsetPeriodicity": 0,
                "offsetPeriod": 0
              },
              "endDateTimeInfo": {
                "dateTimeTz": "{{ endDate | bas_datetime or '0001-01-01T24:00:00.000+00:00' }}",
                "offsetPeriodicity": 0,
                "offsetPeriod": 0
              },
            {% if periodicity %}
              "periodicity": {{ periodicity }},
            {% endif %}
              "sessionTimeStampInfo": "TIME_STAMP_START_END_DATE_TIME_TZ",
              "dataTimeStampInfo": "TIME_STAMP_DATE_TIME_TZ",
              "prepend": 0,
              "sessionInfo": {
                "sessionType": "SESSION_REQUEST_TYPE_REGULAR",
                "alwaysIncludeDay0Session": true,
                "previousCloseSessionType": "SESSION_REQUEST_TYPE_REGULAR",
                "filterNonTradedDates": false
              },
              "retrieveTradeDates": false,
              "completePeriod": true,
              "requestPrevPoint": false,
              "datesOverride": "ACTIVE",
          {% if pointsPerDay %}
              "useNumberOfPointsPerDayToComputePeriodicity": true,
              "numberOfPointsPerDay": {{ pointsPerDay }},
          {% else %}
              "useNumberOfPointsPerDayToComputePeriodicity": false,
          {% endif %}
          {% if numberOfMostRecentBusinessDays %}
              "computeStartEndDatesFromMostRecent": true,
              "mostRecentBusinessDaysInfo": {
                "numberOfMostRecentBusinessDays": {{ numberOfMostRecentBusinessDays }}
              },
          {% else %}
              "computeStartEndDatesFromMostRecent": false,
          {% endif %}
              "placeholderForMissingIntradayBar": false,
              "limitDaysToMaxIntervals": 0
            },
            "fetchSessions": true,
            "overrides": {
              "securityPcs": "",
              "currency": "Local CCY",
              "genericOverrides": {},
              "timeFrame": "NONE",
              "genericOverridesForSessions": {}
            },
            "subRequestId": {{ range(1, 100000) | random }},
            "dataSource": "DEFAULT",
            "fetchData": true,
            "securityNameIsQueryUniverse": false,
            "securityNameMode": "AS_ID"
          }{{ ',' if not loop.last }}
      {% endfor %}
        ]
      },
      "attributeList": {},
      "usePartialResponse": true,
      "appInfo": {
        "WhoAmI": "TRINO"
      },
      "returnErrorWithSecurity": true
    }
}
endef

define TRINO_LOG
io=DEBUG
com=DEBUG
org.apache=DEBUG
endef

define ACCESS_CONTROL
access-control.name=ranger
endef

define ACCESS_CONTROL_RANGER
access-control.name=ranger
ranger.use_ugi=true
ranger.service_name=presto-dev-bloomberg-com
ranger.hadoop_config=${PWD}/testing/trino-server-dev/etc/trino-ranger-site.xml
ranger.audit_resource=${PWD}/testing/trino-server-dev/etc/trino-ranger-audit.xml
ranger.security_resource=${PWD}/testing/trino-server-dev/etc/trino-ranger-security.xml
ranger.policy_manager_ssl_resource=${PWD}/testing/trino-server-dev/etc/trino-ranger-policymgr-ssl.xml
endef

define RANGER_SITE
<configuration>
  <property>
    <name>hadoop.security.group.mapping</name>
    <value>org.apache.hadoop.security.LdapGroupsMapping</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.bind.user</name>
    <value>CN=AD Query,OU=Enabled Accounts,DC=addev,DC=bloomberg,DC=com</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.bind.password</name>
    <value>Look4what</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.url</name>
    <value>ldap://addev.bloomberg.com:389</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.base</name>
    <value>DC=addev,DC=bloomberg,DC=com</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.search.filter.user</name>
    <value>(&amp;(objectclass=user)(|(sAMAccountName={0})(mail={0}@bloomberg.net)))</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.search.filter.group</name>
    <value>(cn=PVFX_DATA_31_*)</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.search.attr.member</name>
    <value>member</value>
  </property>
  <property>
    <name>hadoop.security.group.mapping.ldap.search.attr.group.name</name>
    <value>cn</value>
  </property>
</configuration>
endef

define RANGER_AUDIT
<configuration xmlns:xi="http://www.w3.org/2001/XInclude">
    <property>
        <name>xasecure.audit.is.enabled</name>
        <value>true</value>
    </property>
    <!-- DB audit provider configuration -->
    <property>
        <name>xasecure.audit.db.is.enabled</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.db.is.async</name>
        <value>true</value>
    </property>
    <property>
        <name>xasecure.audit.db.async.max.queue.size</name>
        <value>10240</value>
    </property>
    <property>
        <name>xasecure.audit.db.async.max.flush.interval.ms</name>
        <value>30000</value>
    </property>
    <property>
        <name>xasecure.audit.db.batch.size</name>
        <value>100</value>
    </property>
    <property>
        <name>xasecure.audit.jpa.javax.persistence.jdbc.url</name>
        <value>jdbc:mysql://localhost:3306/ranger_audit</value>
    </property>
    <property>
        <name>xasecure.audit.jpa.javax.persistence.jdbc.user</name>
        <value>rangerlogger</value>
    </property>
    <property>
        <name>xasecure.audit.jpa.javax.persistence.jdbc.password</name>
        <value>none</value>
    </property>
    <property>
        <name>xasecure.audit.jpa.javax.persistence.jdbc.driver</name>
        <value>com.mysql.jdbc.Driver</value>
    </property>
    <property>
        <name>xasecure.audit.credential.provider.file</name>
        <value>jceks://file/etc/ranger/trinodev/auditcred.jceks</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.is.enabled</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.is.async</name>
        <value>true</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.async.max.queue.size</name>
        <value>1048576</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.async.max.flush.interval.ms</name>
        <value>30000</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.encoding</name>
        <value/>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.destination.directory</name>
        <value>hdfs://__REPLACE__NAME_NODE_HOST:8020/ranger/audit/%app-type%/%time:yyyyMMdd%</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.destination.file</name>
        <value>%hostname%-audit.log</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.destination.flush.interval.seconds</name>
        <value>900</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.destination.rollover.interval.seconds</name>
        <value>86400</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.destination.open.retry.interval.seconds</name>
        <value>60</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.local.buffer.directory</name>
        <value>__REPLACE__LOG_DIR/trino/audit</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.local.buffer.file</name>
        <value>%time:yyyyMMdd-HHmm.ss%.log</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.local.buffer.file.buffer.size.bytes</name>
        <value>8192</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.local.buffer.flush.interval.seconds</name>
        <value>60</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.local.buffer.rollover.interval.seconds</name>
        <value>600</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.local.archive.directory</name>
        <value>__REPLACE__LOG_DIR/trino/audit/archive</value>
    </property>
    <property>
        <name>xasecure.audit.hdfs.config.local.archive.max.file.count</name>
        <value>10</value>
    </property>
    <property>
        <name>xasecure.audit.log4j.is.enabled</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.log4j.is.async</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.log4j.async.max.queue.size</name>
        <value>10240</value>
    </property>
    <property>
        <name>xasecure.audit.log4j.async.max.flush.interval.ms</name>
        <value>30000</value>
    </property>
    <property>
        <name>xasecure.audit.trino.is.enabled</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.trino.async.max.queue.size</name>
        <value>1</value>
    </property>
    <property>
        <name>xasecure.audit.trino.async.max.flush.interval.ms</name>
        <value>1000</value>
    </property>
    <property>
        <name>xasecure.audit.trino.broker_list</name>
        <value>localhost:9092</value>
    </property>
    <property>
        <name>xasecure.audit.trino.topic_name</name>
        <value>ranger_audits</value>
    </property>
    <property>
        <name>xasecure.audit.solr.is.enabled</name>
        <value>true</value>
    </property>
    <property>
        <name>xasecure.audit.solr.async.max.queue.size</name>
        <value>1</value>
    </property>
    <property>
        <name>xasecure.audit.solr.async.max.flush.interval.ms</name>
        <value>1000</value>
    </property>
    <property>
        <name>xasecure.audit.solr.solr_url</name>
        <value>http://solr:8983/solr/ranger_audits</value>
    </property>
    <property>
        <name>xasecure.audit.provider.summary.enabled</name>
        <value>true</value>
    </property>
    <property>
        <name>xasecure.audit.destination.solr</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.destination.solr.urls</name>
        <value>http://solr:8983/solr/ranger_audits</value>
    </property>
    <property>
        <name>xasecure.audit.destination.solr.user</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.solr.password</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.solr.zookeepers</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.solr.batch.filespool.dir</name>
        <value>/var/log/trino/audit/solr/spool</value>
    </property>
    <property>
        <name>xasecure.audit.destination.elasticsearch</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.destination.elasticsearch.urls</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.elasticsearch.user</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.elasticsearch.password</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.elasticsearch.index</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.elasticsearch.port</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.elasticsearch.protocol</name>
        <value>NONE</value>
    </property>
    <property>
        <name>xasecure.audit.destination.hdfs</name>
        <value>false</value>
    </property>
    <property>
        <name>xasecure.audit.destination.hdfs.batch.filespool.dir</name>
        <value>/var/log/presto/audit/hdfs/spool</value>
    </property>
    <property>
        <name>xasecure.audit.destination.hdfs.dir</name>
        <value>hdfs://__REPLACE__NAME_NODE_HOST:8020/ranger/audit</value>
    </property>
    <property>
        <name>xasecure.audit.destination.hdfs.config.fs.azure.shellkeyprovider.script</name>
        <value>__REPLACE_AZURE_SHELL_KEY_PROVIDER</value>
    </property>
    <property>
        <name>xasecure.audit.destination.hdfs.config.fs.azure.account.key.__REPLACE_AZURE_ACCOUNT_NAME.blob.core.windows.net</name>
        <value>__REPLACE_AZURE_ACCOUNT_KEY</value>
    </property>
    <property>
        <name>xasecure.audit.destination.hdfs.config.fs.azure.account.keyprovider.__REPLACE_AZURE_ACCOUNT_NAME.blob.core.windows.net</name>
        <value>__REPLACE_AZURE_ACCOUNT_KEY_PROVIDER</value>
    </property>
    <property>
        <name>xasecure.audit.destination.log4j</name>
        <value>true</value>
    </property>
    <property>
        <name>xasecure.audit.destination.log4j.logger</name>
        <value>xaaudit</value>
    </property>
</configuration>
endef

define RANGER_POLICYMANAGER
<configuration xmlns:xi="http://www.w3.org/2001/XInclude">
    <!--  The following properties are used for 2-way SSL client server validation -->
    <property>
        <name>xasecure.policymgr.clientssl.keystore</name>
        <value>/etc/hadoop/conf/ranger-plugin-keystore.jks</value>
        <description>
            Java Keystore files
        </description>
    </property>
    <property>
        <name>xasecure.policymgr.clientssl.truststore</name>
        <value>/etc/hadoop/conf/ranger-plugin-truststore.jks</value>
        <description>
            java truststore file
        </description>
    </property>
    <property>
        <name>xasecure.policymgr.clientssl.keystore.credential.file</name>
        <value>jceks://file/etc/ranger/presto-dev-bloomberg-com/cred.jceks</value>
        <description>
            java  keystore credential file
        </description>
    </property>
    <property>
        <name>xasecure.policymgr.clientssl.truststore.credential.file</name>
        <value>jceks://file/etc/ranger/presto-dev-bloomberg-com/cred.jceks</value>
        <description>
            java  truststore credential file
        </description>
    </property>
</configuration>
endef

define RANGER_SECURITY
<configuration>
    <property>
        <name>ranger.plugin.trino.service.name</name>
        <value>presto-dev-bloomberg-com</value>
        <description>
      Name of the Ranger service containing policies for this Trino instance
    </description>
    </property>
    <property>
        <name>ranger.plugin.trino.policy.source.impl</name>
        <value>org.apache.ranger.admin.client.RangerAdminRESTClient</value>
        <description>
      Class to retrieve policies from the source
    </description>
    </property>
    <property>
        <name>ranger.plugin.trino.policy.rest.url</name>
        <value>https://ranger-prestodev.dev.bloomberg.com</value>
        <description>
      URL to Ranger Admin
    </description>
    </property>
    <property>
        <name>ranger.plugin.trino.policy.rest.ssl.config.file</name>
        <value>${PWD}/testing/trino-server-dev/etc/ranger-policymgr-ssl.xml</value>
        <description>
      Path to the file containing SSL details to contact Ranger Admin
    </description>
    </property>
    <property>
        <name>ranger.plugin.trino.policy.pollIntervalMs</name>
        <value>30000</value>
        <description>
      How often to poll for changes in policies?
    </description>
    </property>
    <property>
        <name>ranger.plugin.trino.policy.rest.client.connection.timeoutMs</name>
        <value>120000</value>
        <description>
      S3 Plugin RangerRestClient Connection Timeout in Milli Seconds
    </description>
    </property>
    <property>
        <name>ranger.plugin.trino.policy.rest.client.read.timeoutMs</name>
        <value>30000</value>
        <description>
      S3 Plugin RangerRestClient read Timeout in Milli Seconds
    </description>
    </property>
    <property>
        <name>ranger.plugin.trino.policy.cache.dir</name>
        <value>/etc/ranger/presto-dev-bloomberg-com/policycache</value>
    </property>
</configuration>
endef

define SNOWFLAKE_CONFIG
connector.name=snowflake
connection-url=jdbc:snowflake://pyb10012.us-east-1.snowflakecomputing.com
connection-user=$${ENV:SNOWFLAKE_USER}
connection-password=$${ENV:SNOWFLAKE_PASS}
snowflake.account=$${ENV:SNOWFLAKE_ACCOUNT}
snowflake.database=TRINO_CONNECTOR_TEST_DATA
snowflake.role=ACCOUNTADMIN
snowflake.warehouse=COMPUTE_WH
snowflake.httpProxy=http://erik:foo@proxy.bloomberg.com:81
endef

define NSD_POSTGRES
connector.name=postgresql
connection-url=jdbc:postgresql://pg_pgdev4_dev_ro_ar.bdns.bloomberg.com:4332/appsdb
connection-user=$${ENV:NSD_USER}
connection-password=$${ENV:NSD_PASS}
endef

# RULES_JSON and FILE_ACCESS_CONTROL_CONFIG are outdated approaches but please keep for historical references
define RULES_JSON
{
	"queries": [
		{
			"user": "datalake",
			"allow": ["execute", "kill", "view"]
		},
		{
			"user": "cna_presto",
			"allow": ["execute", "kill", "view"]
		},
		{
			"allow": ["execute"]
		}
	]
}
endef

define FILE_ACCESS_CONTROL_CONFIG
access-control.name=file
security.config-file=${PWD}/testing/trino-server-dev/etc/rules.properties
endef

export ARTIFACTORY_CONFIG
export TRINO_CONFIG
export ICEBERG_PROP
export HIVE_PROP
export POSTGRES_PROP
export BSQL_PROP
export BAS_METADATA_CONFIG
export BAS_TADATA_JINJA
export TRINO_LOG
export ACCESS_CONTROL
export ACCESS_CONTROL_RANGER
export RANGER_SITE
export RANGER_AUDIT
export RANGER_POLICYMANAGER
export RANGER_SECURITY
export SNOWFLAKE_CONFIG
export NSD_POSTGRES
export RULES_JSON
export FILE_ACCESS_CONTROL_CONFIG

# This runs the class version of trino (compiled from source).
run:
	mvn -s artifactory-settings.xml exec:java -pl :datalake-server-dev -Dconfig=${PWD}/bloomberg/datalake-server-dev/etc/config.properties -Dlog.levels-file=${PWD}/etc/log.properties -Djdk.attach.allowAttachSelf=true -Dexec.mainClass=com.bloomberg.datalake.trino.server.DatalakeDevelopmentTrinoServer 2>&1 | tee ../../run.txt

# Not really that useful but runs the non-dev version of the Trino server. runbuilt is more better.
runmain:
	mvn -s artifactory-settings.xml exec:java -pl :datalake-server-main -Dconfig=${PWD}/bloomberg/datalake-server-dev/etc/config-main.properties -Dlog.levels-file=${PWD}/etc/log.properties -Djdk.attach.allowAttachSelf=true -Dexec.mainClass=com.bloomberg.datalake.trino.server.DatalakeTrinoServer 2>&1 | tee ../../run.txt

# Similar to the run targer above but it runs the packaged JAR version of Trino. This is the same way the prod version is ran.0
# Make sure you setup your trino configs under /etc/trino
runbuilt:
	@version=$$( \
	    xmlstarlet sel \
	      -N pom="http://maven.apache.org/POM/4.0.0" \
	      -t \
	      -v "/pom:project/pom:version" \
	      -n \
	      pom.xml \
	) && \
	echo "Starting Datalake Trino Server version $$version…" && \
	cd bloomberg/datalake-build/target/datalake-build-$$version && \
	exec bin/launcher run --etc-dir /etc/trinomain

runt2t: guard-ENCRYPTION_SECRET
	cd ${PWD}/testing/trino-server-dev && mvn -s ../../artifactory-settings.xml exec:java -Dconfig=${PWD}/testing/trino-server-dev/etc/config.properties -Dlog.levels-file=etc/log.properties -Djdk.attach.allowAttachSelf=true -Dexec.mainClass=io.trino.server.DevelopmentServer 2>&1 | tee ../../run.txt

rules:
	printf '%s\n' "$${RULES_JSON}" > ${PWD}/testing/trino-server-dev/etc/rules.properties
	printf '%s\n' "$${FILE_ACCESS_CONTROL_CONFIG}" > ${PWD}/testing/trino-server-dev/etc/access-control-file-based.properties

# Write out the config files required for local development. The below sed replaces the output \\ with a single slash
# Example: make bash    followed by make configfiles
configfiles:
	rm -f ${PWD}/artifactory-settings.xml
	rm -f ${PWD}/testing/trino-server-dev/etc/config.properties
	rm -f ${PWD}/testing/trino-server-dev/etc/log.properties
	rm -f ${PWD}/testing/trino-server-dev/etc/access-control.properties
	rm -f ${PWD}/testing/trino-server-dev/etc/access-control-ranger.properties
	rm -f ${PWD}/testing/trino-server-dev/etc/trino-ranger*.xml
	rm -f ${PWD}/testing/trino-server-dev/etc/catalog/*.properties
	rm -rf /var/presto/*
	mkdir -p /var/presto/data/bas_configs/templates
	mkdir -p ${PWD}/testing/trino-server-dev/bas_configs/templates
	printf '%s\n' "$${ARTIFACTORY_CONFIG}" > ${PWD}/artifactory-settings.xml
	printf '%s\n' "$${TRINO_CONFIG}" | sed 's/\\\\/\\/' > ${PWD}/testing/trino-server-dev/etc/config.properties
	printf '%s\n' "$${TRINO_LOG}" | sed 's/\\\\/\\/' > ${PWD}/testing/trino-server-dev/etc/log.properties
	printf '%s\n' "$${RULES_JSON}" > ${PWD}/testing/trino-server-dev/etc/rules.properties
	printf '%s\n' "$${FILE_ACCESS_CONTROL_CONFIG}" > ${PWD}/testing/trino-server-dev/etc/access-control-file-based.properties
	printf '%s\n' "$${SNOWFLAKE_CONFIG}" > ${PWD}/testing/trino-server-dev/etc/catalog/snowflake.properties
	printf '%s\n' "$${ICEBERG_PROP}" > ${PWD}/testing/trino-server-dev/etc/catalog/localdev_iceberg.properties
	printf '%s\n' "$${HIVE_PROP}" > ${PWD}/testing/trino-server-dev/etc/catalog/localdev_hive_playground.properties
	printf '%s\n' "$${POSTGRES_PROP}" > ${PWD}/testing/trino-server-dev/etc/catalog/localdev_postgres.properties
	printf '%s\n' "$${NSD_POSTGRES}" > ${PWD}/testing/trino-server-dev/etc/catalog/nsd_postgres.properties
	printf '%s\n' "$${BSQL_PROP}" > ${PWD}/testing/trino-server-dev/etc/catalog/bsql.properties
	printf '%s\n' "$${BAS_METADATA_CONFIG}" > ${PWD}/testing/trino-server-dev/bas_configs/metadata.json
	printf '%s\n' "$${BAS_TADATA_JINJA}" > ${PWD}/testing/trino-server-dev/bas_configs/templates/tadata_fields_request_json.jinja2
# Disable ranger. Comment out 2 lines below
	printf '%s\n' "$${ACCESS_CONTROL}" > ${PWD}/testing/trino-server-dev/etc/access-control.properties
	printf '%s\n' "$${ACCESS_CONTROL_RANGER}" > ${PWD}/testing/trino-server-dev/etc/access-control-ranger.properties
	printf '%s\n' "$${RANGER_SITE}" > ${PWD}/testing/trino-server-dev/etc/trino-ranger-site.xml
	printf '%s\n' "$${RANGER_AUDIT}" > ${PWD}/testing/trino-server-dev/etc/trino-ranger-audit.xml
	printf '%s\n' "$${RANGER_POLICYMANAGER}" > ${PWD}/testing/trino-server-dev/etc/trino-ranger-policymgr-ssl.xml
	printf '%s\n' "$${RANGER_SECURITY}" > ${PWD}/testing/trino-server-dev/etc/trino-ranger-security.xml

intellijconfig:
	yes=$(xmlstarlet sel -t -v "count(/project/component[@name='MavenImportPreferences'])" .idea/workspace.xml) && [ "\$yes" = "0" ] && \
	xmlstarlet ed --inplace -s /project --type elem -name componentTMP -v "" \
	-i //componentTMP -t attr -n "name" -v "MavenImportPreferences" \
	-s //componentTMP --type elem -name "option" -v "" \
	-i //componentTMP/option -t attr -n "name" -v "generalSettings" \
	-s //componentTMP/option --type elem -name "MavenGeneralSettings" -v "" \
	-s //componentTMP/option/MavenGeneralSettings --type elem -name "option" -v "" \
	-i //componentTMP/option/MavenGeneralSettings/option -t attr -n "name" -v "userSettingsFile" \
	-i //componentTMP/option/MavenGeneralSettings/option -t attr -n "value" -v "\$$PROJECT_DIR\$$/artifactory-settings.xml" \
	-r //componentTMP -v component \
	workspace.xml
	yes=$(xmlstarlet sel -t -v "count(/project/component[@name='RunManager'])" .idea/workspace.xml) && [ "$yes" = "0" ] && \
	xmlstarlet ed --inplace -s /project --type elem -name componentTMP -v "" \
	-i //componentTMP -t attr -n "name" -v "RunManager" \
	-s //componentTMP --type elem -name "configuration" -v "" \
	-i //componentTMP/configuration -t attr -n "name" -v "TrinoServer" \
	-i //componentTMP/configuration -t attr -n "type" -v "Application" \
	-i //componentTMP/configuration -t attr -n "factoryName" -v "Application" \
	-s //componentTMP/configuration --type elem -name "optionTMP1" -v "" \
	-i //optionTMP1 -t attr -n "name" -v "MAIN_CLASS_NAME" \
	-i //optionTMP1 -t attr -n "value" -v "io.trino.server.DevelopmentServer" \
	-s //componentTMP/configuration --type elem -name "moduleTMP1" -v "" \
	-i //moduleTMP1 -t attr -n "name" -v "trino-server-dev" \
	-s //componentTMP/configuration --type elem -name "optionTMP2" -v "" \
	-i //optionTMP2 -t attr -n "name" -v "VM_PARAMETERS" \
	-i //optionTMP2 -t attr -n "value" -v "-ea -Dconfig=etc/config.properties -Dlog.levels-file=etc/log.properties -Djdk.attach.allowAttachSelf=true" \
	-s //componentTMP/configuration --type elem -name "optionTMP3" -v "" \
	-i //optionTMP3 -t attr -n "name" -v "WORKING_DIRECTORY" \
	-i //optionTMP3 -t attr -n "value" -v "\$MODULE_DIR\$" \
	-s //componentTMP/configuration --type elem -name "methodTMP1" -v "" \
	-i //methodTMP1 -t attr -n "v" -v "2" \
	-s //methodTMP1 --type elem -name "optionTMP4" -v "" \
	-i //optionTMP4 -t attr -n "name" -v "Make" \
	-i //optionTMP4 -t attr -n "enabled" -v "true" \
	-r //componentTMP -v component \
	-r //optionTMP1 -v option \
	-r //optionTMP2 -v option \
	-r //optionTMP3 -v option \
	-r //optionTMP4 -v option \
	-r //moduleTMP1 -v module \
	-r //methodTMP1 -v method \
	workspace.xml
