+++
linkTitle = "Home"
layout = "landing"
+++

<div class="book-hero">

# StreamsHub {anchor=false}
Open source resources for building real-time event-driven services and data-pipelines on Kubernetes.

{{< badge style="default" title="License" value="Apache-2.0" >}}

<br/>
<br/>

{{<button href="/docs/Flink-SQL-Tutorials/main/">}}Explore{{</button>}}

</div>

There is a rich ecosystem of open source projects providing event-driven and real time data infrastructure.
The Apache Foundation alone has a wide selection of projects in this space including; Kafka, Flink, Spark, Pulsar, Beam, Paimon, Pinot to name a few.
The Linux and Cloud Native Computing Foundations also have their own open source offerings.
Navigating this ecosystem is often difficult and knowing how to deploy, run and combine these projects can be daunting.

StreamsHub aims to provide a curated set of open source projects, templates and tools to help infrastructure engineers create services that support event-driven architectures (EDA), running on Kubernetes, and for data-scientists and analysts to create data-pipelines and query real-time data.
Where there is a gap in the current open source offering, that would benefit from being filled, StreamsHub will aim to host a project to fill it (for example the Flink SQL runner allowing Standalone SQL query deployments), with the aim of pushing the solution up to the main projects.

<br/>

{{% columns %}}
- {{< card title="Card" image="" >}}
  # [StreamsHub Console](/docs/StreamsHub-Console/)
  UI for administrating [Apache Kafka](https://kafka.apache.org/) clusters.

  Delivers real-time insights for monitoring, managing, and optimizing each cluster.
  {{< /card >}}

- {{< card title="Card" image="" >}}
  # [Flink SQL Runner](/docs/Flink-SQL-Runner/)
  Wrapper application and container image for use with [Flink Kubernetes Operator's](https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-main/) `FlinkDeployment` custom resource.

  Allows you to specify your SQL queries as arguments.
  {{< /card >}}

- {{< card title="Card" image="" >}}
  # [Flink SQL Tutorials](/docs/Flink-SQL-Tutorials/main/)
  Collection of tutorials covering many aspects of using Flink SQL.

  Based on the StreamsHub [Flink SQL Examples](https://github.com/streamshub/flink-sql-examples) repository.
  {{< /card >}}
{{% /columns %}}
