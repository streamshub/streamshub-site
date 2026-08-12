+++
linkTitle = "Home"
layout = "landing"

title = "Open source resources for building real-time event-driven services and data-pipelines on Kubernetes."
description = "StreamsHub provides a curated set of open source projects, templates, and tools to support event-driven architectures, data pipelines, real-time data queries and more."
+++

<section class="page-section">
<h1>What is StreamsHub?</h1>
<p>StreamsHub provides a set of open source projects which support event-driven architectures running on Kubernetes.</p>
<img src="img/streamshub-architecture-v2-dark-03.png" alt="" class="streamshub-architecture">
</section>

<section class="page-section">
<h1>Open source stack for Kafka</h1>
<p>StreamsHub projects give open source options for working with Event-Driven architectures with Apache Kafka. 
StreamsHub combines with other open-source projects like <a href="https://strimzi.io/">Strimzi</a> and <a href="https://kroxylicious.io/">Kroxylicious</a> to provide tools for running Kafka proxies and the UI console in Kubernetes.</p>
<h2>Integrate with Apicurio registry for schema management</h2>
<p>The Apicurio registry enforces message syntax and format via schemas and integrates with both the Console and Kroxylicious proxy. Filters can be applied to messages using Kroxylicious to add security, resilience and reliability to your event-driven applications.</p>
</section>

<section class="page-section">
<h1>StreamsHub Projects</h1>
{{% columns %}}

- {{< card title="Card" image="" class="project-card" >}}
  # [StreamsHub Console](/console/)
  UI for administrating [Apache Kafka](https://kafka.apache.org/) clusters.

  Delivers real-time insights for monitoring, managing, and optimizing each cluster.
  {{< /card >}}

- {{< card title="Card" image="" class="project-card" >}}
  # [Flink SQL Runner](/flink-sql-runner/)
  Wrapper application and container image for use with [Flink Kubernetes Operator's](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/) `FlinkDeployment` custom resource.

  Allows you to specify your SQL queries as arguments.
  {{< /card >}}

- {{< card title="Card" image="" class="project-card" >}}
  # [MCP Server for Strimzi](/strimzi-mcp-server/)
  MCP server for Strimzi to make deploying, managing and developing with Strimzi quicker and easier.
  {{< /card >}}
{{% /columns %}}
</section>

<section class="page-section">
<h1>Explore StreamsHub</h1>
<p>Take a look at some guides, tutorials and walk-throughs showing what is possible using StreamsHub. <a href="/explore/">View all</a></p>
{{% columns %}}
- {{< card href="/explore/secure-authentication/" image="img/icons/pf-icon-private.png" class="project-card card-gradient card-with-icon" >}}
  # Secure authentication with StreamsHub
  Learn how to set up secure authentication for you applications and users with StreamsHub
  {{< /card >}}

- {{< card href="/explore/strimzi-mcp/" image="img/icons/fa-microchip.png" class="project-card card-gradient card-with-icon" >}}
  # Using the MCP Server for Strimzi
  Learn how to make the most of Strimzi in your app development with the MCP server for Strimzi.
  {{< /card >}}

- {{< card href="/explore/prometheus-metrics/" image="img/icons/pf-icon-monitoring.png" class="project-card card-gradient card-with-icon" >}}
  # Displaying metrics using Prometheus
  Learn how to collect and expose metrics using Prometheus and StreamsHub.
  {{< /card >}}
{{% /columns %}}
</section>
