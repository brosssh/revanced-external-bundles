# 🚀 ReVanced external bundles

![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/brosssh/revanced-external-bundles/release.yml)
![AGPLv3 License](https://img.shields.io/badge/License-AGPL%20v3-yellow.svg)

A centralized collection and API service for ReVanced external patches bundles, providing automated updates and multiple query interfaces.

> [!WARNING]  
> This project is not affiliated with or endorsed by the official ReVanced project. It is an independent community initiative for managing external patches bundles.

## 💪 Features

### 🔄 Automatic Bundle & Patch Updates
Scheduled jobs continuously monitor and update all bundles and their patches, ensuring you always have access to the latest versions without manual intervention.

### 🔌 REST API with Swagger Documentation
Comprehensive RESTful endpoints with interactive Swagger/OpenAPI documentation for easy integration and testing. Browse and query bundles, patches, and their metadata through well-documented HTTP endpoints.

### 🎯 GraphQL Query Interface
Powerful GraphQL endpoint allowing you to fetch exactly the data you need with flexible, nested queries. Check out the [GraphQL examples documentation](docs/graphql-examples.md).

### 🌐 Interactive Web Interface
User-friendly website to explore available bundles and their patches visually. Built on top of the REST API, providing an intuitive way to discover and understand patch compatibility and features.
> [!WARNING]  
> The Web Interface is **NOT** the primary goal of this project. Better Web Interfaces are welcome, I personally have no interest in designing a proper website.


## 🚀 How to get started

A Java Development Kit (JDK) and Git must be installed.

1. Run `git clone git@github.com:brosssh/revanced-external-bundles.git` to clone the repository
2. Copy [.env.example](.env.example) to `.env` and fill in the required values
3. Run `docker compose up -d` to create the infrastructure
4. Run `gradlew run` to start the server

The API will be available at `http://localhost:8080` by default.

### Quick Access

- **REST API Documentation**: http://localhost:8080/swagger
- **GraphQL Playground**: http://localhost:8080/hasura/console
- **Web Interface**: http://localhost:8080

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## 📜 License

ReVanced External Bundles is licensed under the AGPLv3 license. Please see the [LICENSE](LICENSE) file for more information.

[tl;dr](https://www.tldrlegal.com/license/gnu-affero-general-public-license-v3-agpl-3-0): You may copy, distribute, and modify ReVanced External Bundles as long as you track changes/dates in source files. Any modifications must also be made available under the AGPL, along with build and installation instructions.
