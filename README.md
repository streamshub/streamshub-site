# StreamsHub Docs Site Source

This repo holds the source code for the StreamsHub site.

## Pulling dependant sources

This site aggregates documentation from all the StreamsHub components. 
Each component's documentation source and the versions which are included are configured via an entry in the `sources.json` file in the repository root:
```yaml
{
    "name": "Flink SQL Runner",
    "sourceOwner": "tomncooper",
    "sourceRepository": "flink-sql", 
    "developmentBranch": "docs-dev",
    "docsFolderPath": "docs",
    "tags":["0.1.0-doc"] 
}
```
This file is read by the `scripts/docBuilder.java` [jbang](https://www.jbang.dev/) script. 
You will need to install `jbang` locally in order to run the documentation build.

The contents of the `docsFolderPath` in each `<sourceOwner>/<sourceRespoitory>` GitHub repository at each supplied reference `tag` will be pulled and placed in their own folder under `content/docs/<name>/<tag>`. 
If a folder already exists for the given tag then it will not be pulled.

The contents of the `docsFolderPath` folder on the `devlopmentBranch` will always be pulled on every build via the `.github/workflows/publish.yaml` GitHub Action.

To pull the configured sources you will need a [GitHub access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) with permissions to access **all** the configured `<sourceOwner>/<sourceRespoitory>`:

```shell
./scripts/docBuilder.java <github-access-token>
```

## Building the site

The site uses the [hugo](https://gohugo.io/) static site generator. 
You will need to install a [recent release](https://github.com/gohugoio/hugo/releases) (the version in your package manager is probably too old) and the [PostCSS](https://gohugo.io/hugo-pipes/postcss/) packages in order to build the source.

You can build the site by running `hugo` from the repository root.
Or run a live preview server by running:
```shell
hugo server --buildDrafts --disableFastRender  
```