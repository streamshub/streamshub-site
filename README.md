# StreamsHub Docs Site Source

This repo holds the source code for the StreamsHub site.

## Pulling dependant sources

This site aggregates documentation from all the StreamsHub components. 
Each component's documentation source and the versions which are included are configured via an entry in the `sources.json` file in the repository root:
```json
{
    "name": "StreamsHub Console",
    "contentsLinkTitle": "Documentation",
    "outputPath": "console/docs",
    "sourceOwner": "streamshub",
    "sourceRepository": "console",
    "developmentBranch": "main",
    "docsFolderPath": "docs/sources",
    "tags": ["0.12.6", "0.13.0"]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Human-readable project name |
| `sourceOwner` | Yes | GitHub organization or user |
| `sourceRepository` | Yes | GitHub repository name |
| `developmentBranch` | Yes | Branch to pull as the in-development docs (e.g. `main`, `dev`) |
| `docsFolderPath` | Yes | Path within the repo to the docs folder |
| `tags` | Yes | List of git tags to pull as versioned release snapshots |
| `outputPath` | No | Where docs are placed under `content/` (defaults to `name`) |
| `contentsLinkTitle` | No | Override for the sidebar link title (defaults to `name`) |
| `skipContentsPageCreation` | No | If `true`, no `_index.md` contents page is generated |

This file is read by the `scripts/docBuilder.java` [jbang](https://www.jbang.dev/) script. 
You will need to install `jbang` locally in order to run the documentation build.

The contents of the `docsFolderPath` in each `<sourceOwner>/<sourceRepository>` GitHub repository at each supplied reference `tag` will be pulled and placed in their own folder under `content/<outputPath>/<tag>`. 
If a folder already exists for the given tag then it will not be pulled.

The contents of the `docsFolderPath` folder on the `developmentBranch` will always be pulled on every build via the `.github/workflows/publish.yaml` GitHub Action.

A contents file will be generated for each entry in `sources.json`. 
This will redirect to the latest available documentation version.
You can skip this by setting `skipContentsPageCreation` to `true`.

To pull the configured sources locally you will need a GitHub access token with permissions to access **all** the configured `<sourceOwner>/<sourceRepository>`.

If you have the [GitHub CLI](https://cli.github.com/) installed and authenticated, you can use it to provide a token automatically:

```shell
jbang scripts/docBuilder.java "$(gh auth token)"
```

Alternatively, you can create a [personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) and pass it directly:

```shell
jbang scripts/docBuilder.java <github-access-token>
```

## CI/CD Workflows

### Caching tagged documentation (`cache-tagged-docs.yaml`)

Tagged documentation versions (those listed in the `tags` array of each `sources.json` entry) are immutable release snapshots that do not change after release.
To avoid re-downloading them on every build, this workflow commits them to the repository.

The workflow runs automatically when `sources.json` is pushed to `main` (e.g. after adding a new release tag) and can also be triggered manually.
It downloads only tagged versions (`--tags-only`) and removes cached folders for any tags that have been deleted from the configuration (`--cleanup`).

If new or removed tags are detected, the workflow:

1. Creates a branch (`ci/cache-tagged-docs`) with the updated cached content
2. Opens a pull request targeting `main` (or updates the existing one if the branch already has an open PR)

Once the PR is merged, the publish workflow automatically triggers and deploys the updated site.

### Publishing the site (`publish.yaml`)

The publish workflow builds and deploys the site to GitHub Pages.
It runs on every push to `main`, on a daily schedule, and on manual dispatch.

Unlike the caching workflow, it fetches development branch documentation on every run (these are not committed to the repository).
Tagged documentation is already present in the repository from the caching workflow, so it does not need to be re-downloaded.

## Building the site

### Prerequisites

The site uses the [hugo](https://gohugo.io/) static site generator. 
You will need to install a [recent release](https://github.com/gohugoio/hugo/releases) (the version in your package manager is probably too old) and the [PostCSS](https://gohugo.io/hugo-pipes/postcss/) packages in order to build the source.

You will also need [asciidoctor](https://asciidoctor.org/) installed to build most of the documentation pages.

### Building the site locally

This site uses a hugo theme installed via a git submodule. 
If you have just cloned the repo then run the following command to pull the theme:

```shell
git submodule update --init --recursive
```

Before building, pull the documentation sources (see [Pulling dependant sources](#pulling-dependant-sources) above):

```shell
jbang scripts/docBuilder.java "$(gh auth token)"
```

You can then build the site by running `hugo` from the repository root.

```shell
hugo build
```

Or run a live preview server by running:

```shell
hugo server --buildDrafts --disableFastRender  
```