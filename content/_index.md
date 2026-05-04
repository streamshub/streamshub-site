+++
linkTitle = "Home"
layout = "landing"
+++

<div class="book-hero">
<div class="book-hero-container">
  <img class="book-hero-icon" src="img/streamshub_icon_default_512px.png"/>
  <div class="book-hero-content">
    <h1>Open source resources for building real-time event-driven services and data-pipelines on Kubernetes.</h1>
    <br>
    <p>StreamsHub provides a curated set of open source projects, templates, and tools to support event-driven architectures, data pipelines, real-time data queries and more.<p>
    {{<button href="/explore/" class="btn-full btn-large" >}}Explore{{</button>}}
    {{<button href="/docs/" class="btn-large" >}}Documentation{{</button>}}
  </div>
</div>
</div>

<section class="page-section">
<h1>StreamsHub Projects</h1>
{{% columns %}}
- {{< card title="Card" image="" class="project-card" >}}
  # [StreamsHub Console](/docs/StreamsHub-Console/)
  UI for administrating [Apache Kafka](https://kafka.apache.org/) clusters.

  Delivers real-time insights for monitoring, managing, and optimizing each cluster.
  {{< /card >}}

- {{< card title="Card" image="" class="project-card" >}}
  # [Flink SQL Runner](/docs/Flink-SQL-Runner/)
  Wrapper application and container image for use with [Flink Kubernetes Operator's](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/) `FlinkDeployment` custom resource.

  Allows you to specify your SQL queries as arguments.
  {{< /card >}}

- {{< card title="Card" image="" class="project-card" >}}
  # [Flink SQL Tutorials](/docs/Flink-SQL-Tutorials/main/)
  Collection of tutorials covering many aspects of using Flink SQL.

  Based on the StreamsHub [Flink SQL Examples](https://github.com/streamshub/flink-sql-examples) repository.
  {{< /card >}}
{{% /columns %}}

<section>