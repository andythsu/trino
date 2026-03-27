# External HTTP Group Provider

This plugin queries a central group provider endpoint that consolidates groups from multiple sub external group providers and returns a unified list of groups for a given user.


Example Plugin Properties:
```
group-provider.name=externalhttpgroupprovider
externalhttpgroups.cache-ttl=1m
externalhttpgroups.uri=http://localhost:53000
```
`externalhttpgroups.cache-ttl` How long to cache the groups for a user
`externalhttpgroups.uri` Base URI to the central group provider service


## How It Works

The plugin sends a `GET` request to `{externalhttpgroups.uri}/getGroups/<userName>` to retrieve groups for the specified user. The central group provider service is responsible for querying all sub external group providers and returning a consolidated list.

The service should return a JSON response in the following format:
```json
{
  "groupList": ["GROUP_1", "GROUP_2", "GROUP_3", ...]
}
```

The returned groups are cached according to the `externalhttpgroups.cache-ttl` configuration to reduce HTTP calls.

## Features

- **Automatic Retry**: Retries failed requests up to 3 times with exponential backoff
- **Response Caching**: Caches group lists to reduce load on the central provider
- **Graceful Error Handling**: Returns an empty set of groups if all retries fail 
