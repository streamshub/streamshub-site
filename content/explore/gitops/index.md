+++
title = "Managing your infrastructure using GitOps"
description = "Learn how to effectively manage your applications at scale using GitOps and StreamsHub."
category = "tutorial"
weight = 4
+++

This section contains resources to help you manage Apache Kafka infrastructure the GitOps way. Inside you'll find guides that explain the concepts, a hands-on tutorial series that lets you practice real workflows on a local cluster and a library of ready-to-use example configurations covering common Kafka deployment patterns with Strimzi.

## New to GitOps?

If you're just getting started, begin with the **[Introduction to GitOps](../../docs/gitops/main/introduction/_index.md)**. It walks through the problems that manual infrastructure management creates, explains how GitOps solves them and introduces the core ideas and tools you'll see throughout the rest of the documentation.

## Learn by doing

Once you have the concepts down, the **[GitOps Tutorial Series](../../docs/gitops/main/gitops-tutorial/_index.md)** is the best way to build practical skills. Over three hands-on lessons you'll set up a local Kubernetes cluster with Argo CD and work through some real-world scenarios. Each lesson pairs a hands-on lab with a companion guide that explains the concepts behind what you're doing.

## Examples and reference

The **[Kafka Examples](../../docs/gitops/main/kafka-examples/_index.md)** section provides ready-to-deploy configurations for common Kafka scenarios including basic KRaft clusters, cross-cluster replication with MirrorMaker 2 and OAuth-secured setups with Keycloak. Use these as starting points for your own deployments or as reference for how to structure a GitOps repository around Strimzi and Argo CD.
