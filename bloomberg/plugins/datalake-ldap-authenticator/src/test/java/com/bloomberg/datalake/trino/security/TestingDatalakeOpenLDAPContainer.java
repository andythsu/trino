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
package com.bloomberg.datalake.trino.security;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

import java.util.Map;
import java.util.UUID;

import static io.trino.plugin.base.jndi.JndiUtils.createDirContext;
import static java.lang.String.format;

public class TestingDatalakeOpenLDAPContainer
        extends GenericContainer<TestingDatalakeOpenLDAPContainer>
{
    public static final int LDAP_PORT = 1389;

    public TestingDatalakeOpenLDAPContainer()
    {
        super("artprod.dev.bloomberg.com/datalake/openldap");
        addEnv("LDAP_BASE_ORG", "dc=addev,dc=bloomberg,dc=com");
        addEnv("LDAP_USER_UNIT", "Enabled Accounts");
        addEnv("LDAP_USERS_AND_PASSWORDS", "john:snow"); // This user is not used but setting this ENV is required
        setExposedPorts(ImmutableList.of(LDAP_PORT));
        setWaitStrategy(new DockerHealthcheckWaitStrategy());
    }

    public String getLdapUrl()
    {
        return format("ldap://%s:%s", getHost(), getMappedPort(LDAP_PORT));
    }

    public void createUser(String userName, int employeeID, String password)
    {
        createSubcontext(getAttributesForUser(userName, employeeID, password));
    }

    public void createUser(String userName, String password)
    {
        createSubcontext(getAttributesForUser(userName, password));
    }

    private Attributes getAttributesForUser(String userName, int employeeID, String password)
    {
        Attributes attributes = getAttributesForUser(userName, password);
        attributes.put(new BasicAttribute("employeeID", Integer.toString(employeeID)));
        return attributes;
    }

    private Attributes getAttributesForUser(String userName, String password)
    {
        int uuid = UUID.randomUUID().hashCode();
        Attributes attributes = new BasicAttributes();
        Attribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("person");
        objectClass.add("inetOrgPerson");
        objectClass.add("datalakeAdAccount");
        objectClass.add("posixAccount");
        objectClass.add("shadowAccount");
        attributes.put(objectClass);
        attributes.put(new BasicAttribute("cn", userName));
        attributes.put(new BasicAttribute("sn", "Subject_" + userName));
        attributes.put(new BasicAttribute("userPassword", password));
        attributes.put(new BasicAttribute("uid", userName));
        attributes.put(new BasicAttribute("uidNumber", Integer.toString(uuid)));
        attributes.put(new BasicAttribute("gidNumber", Integer.toString(uuid + 1000)));
        attributes.put(new BasicAttribute("homeDirectory", "/home/" + userName));
        attributes.put(new BasicAttribute("sAMAccountName", userName));
        attributes.put(new BasicAttribute("mail", userName + "@bloomberg.net"));
        return attributes;
    }

    private void createSubcontext(Attributes attributes)
    {
        Map<String, String> environment = ImmutableMap.<String, String>builder()
                .put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
                .put(Context.PROVIDER_URL, getLdapUrl())
                .put(Context.SECURITY_AUTHENTICATION, "simple")
                .put(Context.SECURITY_PRINCIPAL, "cn=admin,dc=addev,dc=bloomberg,dc=com")
                .put(Context.SECURITY_CREDENTIALS, "admin")
                .buildOrThrow();
        try {
            String userName = attributes.get("cn").get().toString();
            createDirContext(environment).createSubcontext(String.format("cn=%s,ou=Enabled Accounts,dc=addev,dc=bloomberg,dc=com", userName), attributes);
        }
        catch (NamingException e) {
            throw new RuntimeException("Connection to LDAP server failed", e);
        }
    }
}
