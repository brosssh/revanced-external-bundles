# 📊 GraphQL Query Examples

## What is GraphQL?

GraphQL is a query language for APIs that gives you the power to ask for exactly what you need and nothing more. Unlike traditional REST APIs where endpoints return fixed data structures, GraphQL lets you specify the precise fields you want in a single request.

### Why GraphQL is Powerful

- **Request exactly what you need**: No over-fetching or under-fetching of data
- **Single endpoint**: All queries go through one endpoint, simplifying API architecture
- **Nested queries**: Fetch related data in a single request instead of multiple API calls
- **Strongly typed**: Self-documenting schema with built-in validation
- **Efficient**: Reduce bandwidth and improve performance by requesting only necessary fields

## 🔗 Interactive Playground

Explore these queries interactively in the [GraphQL Playground](https://cloud.hasura.io/public-graphiql/?endpoint=https%3A%2F%2Frevanced-external-bundles.brosssh.com%2Fhasura%2Fv1%2Fgraphql).

The playground provides:
- Auto-completion and syntax highlighting
- Interactive schema documentation
- Query validation and error messages
- Direct execution against the live API


## 🎯 Basic Queries

### Get All Bundles with Basic Information

```graphql
query BasicBundles {
  bundle {
    id
    bundle_type
    version
    description
    download_url
  }
}
```

### Get Bundles with Source Information

```graphql
query BundlesWithSource {
  bundle {
    id
    bundle_type
    version
    source {
      url
      source_metadatum {
        owner_name
        repo_name
        repo_description
        repo_stars
      }
    }
  }
}
```

## 📦 Complete Snapshot Query

This comprehensive query fetches all available data about bundles, their sources, and patches:

```graphql
query Snapshot {
  bundle {
    id
    bundle_type
    created_at
    description
    download_url
    signature_download_url
    is_prerelease
    version
    source {
      url
      source_metadata: source_metadatum {
        owner_name
        owner_avatar_url
        repo_name
        repo_description
        repo_stars
        repo_pushed_at
        is_repo_archived
      }
    }
    patches {
      name
      description
      patch_packages {
        package {
          name
          version
        }
      }
    }
  }
}
```

## 🔍 Filtering and Searching

### Filter by Bundle Type

```graphql
query FilterByType {
  bundle(where: { bundle_type: { _eq: "patches" } }) {
    id
    bundle_type
    version
    download_url
  }
}
```

### Filter Non-Prerelease Bundles

```graphql
query StableReleases {
  bundle(where: { is_prerelease: { _eq: false } }) {
    id
    version
    is_prerelease
    created_at
  }
}
```

### Search by Description

```graphql
query SearchBundles {
  bundle(where: { description: { _ilike: "%youtube%" } }) {
    id
    description
    version
  }
}
```

## 📄 Pagination

### Limit Results

```graphql
query LimitedBundles {
  bundle(limit: 10) {
    id
    bundle_type
    version
  }
}
```

### Offset and Limit (Page 2, 10 items per page)

```graphql
query PaginatedBundles {
  bundle(limit: 10, offset: 10) {
    id
    bundle_type
    version
    created_at
  }
}
```

### Pagination with Ordering

```graphql
query OrderedPagination {
  bundle(
    limit: 20
    offset: 0
    order_by: { created_at: desc }
  ) {
    id
    version
    created_at
    bundle_type
  }
}
```

## 📊 Sorting

### Sort by Creation Date (Newest First)

```graphql
query NewestBundles {
  bundle(order_by: { created_at: desc }, limit: 10) {
    id
    version
    created_at
  }
}
```

### Sort by Repository Stars

```graphql
query PopularBundles {
  bundle(
    order_by: { source: { source_metadatum: { repo_stars: desc } } }
  ) {
    id
    version
    source {
      source_metadatum {
        repo_name
        repo_stars
      }
    }
  }
}
```

## 🎨 Advanced Queries

### Get Patches for Specific Package

```graphql
query PatchesForPackage {
  patch(
    where: { 
      patch_packages: { 
        package: { 
          name: { _eq: "com.google.android.youtube" } 
        } 
      } 
    }
  ) {
    name
    description
    patch_packages {
      package {
        name
        version
      }
    }
  }
}
```

### Bundles with Patch Count

```graphql
query BundlesWithPatchCount {
  bundle {
    id
    version
    bundle_type
    patches_aggregate {
      aggregate {
        count
      }
    }
  }
}
```

### Complex Filtering with Multiple Conditions

```graphql
query ComplexFilter {
  bundle(
    where: {
      _and: [
        { is_prerelease: { _eq: false } }
        { bundle_type: { _eq: "patches" } }
        { source: { source_metadatum: { repo_stars: { _gte: 100 } } } }
      ]
    }
    order_by: { created_at: desc }
    limit: 5
  ) {
    id
    version
    created_at
    source {
      source_metadatum {
        repo_name
        repo_stars
      }
    }
  }
}
```

## 🛠️ Practical Use Cases

### Latest Stable Bundle for Each Type

```graphql
query LatestStableBundles {
  bundle(
    where: { is_prerelease: { _eq: false } }
    order_by: { created_at: desc }
    distinct_on: bundle_type
  ) {
    id
    bundle_type
    version
    download_url
    created_at
  }
}
```

### Active (Non-Archived) Repositories Only

```graphql
query ActiveBundles {
  bundle(
    where: { 
      source: { 
        source_metadatum: { 
          is_repo_archived: { _eq: false } 
        } 
      } 
    }
  ) {
    id
    version
    source {
      source_metadatum {
        repo_name
        is_repo_archived
        repo_pushed_at
      }
    }
  }
}
```

## 💡 Tips

- Use field aliases (like `bundleId: id`) to rename fields in the response
- Combine `limit`, `offset`, and `order_by` for efficient pagination
- Use `_aggregate` queries to get counts without fetching all data
- The `where` clause supports various operators: `_eq`, `_neq`, `_gt`, `_gte`, `_lt`, `_lte`, `_like`, `_ilike`, `_in`, etc.
- Use `distinct_on` to get unique results based on specific fields
