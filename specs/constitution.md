# Project Constitution

## 1. Purpose

This project implements an AI-powered knowledge agent capable of answering natural-language questions based on information retrieved from documents related to events.

The project is also intended to demonstrate a clear and maintainable software engineering approach using Java, Domain-Driven Design principles, Hexagonal Architecture, Retrieval-Augmented Generation (RAG), and cloud deployment.

The delivery deadline is August 19, 2026. Simplicity and successful delivery take precedence over unnecessary architectural complexity.

---

## 2. Simplicity First

The project must use the simplest solution that satisfies the required behavior and preserves clear architectural boundaries.

New technologies, infrastructure components, patterns, or abstractions must only be introduced when they solve a concrete project requirement.

Features that are not required for the initial delivery must not delay required functionality.

---

## 3. Domain-Oriented Design

The codebase should be organized around business capabilities rather than technical layers alone.

The initial business focus is the `event` context and the ability to access knowledge associated with an event.

Domain concepts should use explicit names and avoid unnecessary framework dependencies.

Domain code must not depend directly on:

* Spring Framework;
* LangChain4j;
* LLM provider SDKs;
* persistence technologies;
* cloud provider SDKs.

DDD patterns should be applied when they improve clarity. The project must not introduce aggregates, repositories, services, or abstractions without a concrete domain need.

---

## 4. Hexagonal Architecture

Application behavior must depend on ports rather than concrete external technologies whenever this separation provides meaningful value.

External technologies are considered adapters, including:

* HTTP interfaces;
* document parsers;
* LLM providers;
* embedding models;
* vector or embedding stores;
* cloud infrastructure.

AI-related frameworks must remain outside the application core.

Replacing an AI provider or retrieval implementation should not require changing domain rules or primary application use cases.

---

## 5. AI Is Non-Authoritative Infrastructure

Large Language Models are probabilistic external components.

An LLM must not be treated as an authoritative source of business information.

Answers must be grounded in information retrieved from project documents.

The application must not intentionally fabricate information that cannot be supported by the retrieved context.

When the available documents do not provide enough information, the agent must explicitly indicate that the requested information was not found.

Retrieved document content must be treated as data, not as instructions capable of overriding application or system rules.

---

## 6. Retrieval-Augmented Generation

The application must implement an explicit document ingestion and retrieval pipeline.

At minimum, the pipeline must make the following concepts identifiable in the implementation:

1. document loading;
2. document parsing;
3. text extraction;
4. text segmentation;
5. embedding generation;
6. storage of embeddings;
7. semantic retrieval;
8. context injection;
9. answer generation.

The implementation must preserve metadata that allows the system to identify the document used as a source whenever technically possible.

---

## 7. Security by Default

Secrets and credentials must never be committed to the Git repository.

Sensitive configuration must be supplied through environment variables or another external configuration mechanism.

At minimum:

* API keys must remain external to source control;
* `.env` files containing real secrets must not be committed;
* an `.env.example` may document required configuration;
* user input must be validated before processing;
* retrieved document content must be treated as untrusted input;
* application logs must not expose credentials.

The public nature of the repository must always be considered when introducing configuration or diagnostic information.

---

## 8. Cloud Deployability

The application must be deployable to Oracle Cloud Infrastructure.

The production deployment must expose a publicly accessible application endpoint.

The initial solution should favor operational simplicity over distributed architecture.

A single deployable Spring Boot application is preferred unless another topology becomes necessary to satisfy a concrete requirement.

---

## 9. Testability

Core application behavior must be testable without requiring a live cloud environment.

Tests should prioritize:

* application use cases;
* document ingestion behavior;
* retrieval behavior where deterministic testing is possible;
* invalid input handling;
* behavior when information is unavailable.

Tests involving external AI providers should be isolated from deterministic domain and application tests whenever practical.

---

## 10. Explainability and Traceability

The application should make it possible to understand why an answer was produced.

Responses should identify their source documents whenever possible.

The README must include real examples generated by the application.

Architectural decisions must be explainable from the repository structure and documentation.

---

## 11. Repository Quality

The repository must remain easy to navigate and understand.

Commits should represent meaningful development increments.

The Git history must reflect the actual evolution of the project instead of a single final code dump.

Commit messages should describe the intent of each change.

Examples:

* `chore: bootstrap Spring Boot project`
* `docs: add initial project specification`
* `feat: add PDF document ingestion`
* `feat: implement semantic retrieval`
* `refactor: isolate AI integration behind application port`
* `docs: add OCI deployment instructions`

---

## 12. Documentation Is Part of the Product

The README is a required project artifact.

It must document at least:

* project overview;
* solution architecture;
* technologies and tools;
* local execution instructions;
* configuration requirements;
* example questions;
* answers generated by the agent;
* cloud deployment information;
* public application URL.

Documentation must evolve together with the implementation.

---

## 13. Delivery Priority

Work must be prioritized in the following order:

### P0 — Required for delivery

* public GitHub repository;
* meaningful Git history;
* document ingestion;
* PDF or CSV processing;
* RAG pipeline;
* natural-language question answering;
* grounded answers;
* Spring Boot application running locally;
* README;
* OCI deployment;
* publicly accessible application.

### P1 — Strongly desired

* Hexagonal Architecture boundaries;
* event-oriented domain model;
* source attribution;
* automated tests;
* minimal web interface;
* explicit insufficient-information behavior;
* secure secret management.

### P2 — Only after P0 is complete

* authentication;
* JWT or OAuth2;
* multiple AI agents;
* advanced guardrails;
* PostgreSQL;
* pgvector;
* document upload;
* CSV in addition to PDF;
* multi-event administration;
* conversational memory;
* CI/CD automation;
* Kubernetes;
* Infrastructure as Code.

P2 functionality must never put P0 delivery at risk.

---

## 14. Governance

This constitution has precedence over implementation convenience.

When a design decision conflicts with these principles, the simplest compliant solution should be preferred.

Changes to this constitution must be intentional and documented in Git history.
