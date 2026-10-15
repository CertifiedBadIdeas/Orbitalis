# Roadmap Operations

Read project values from the repository's fenced `yaml roadmap` block. Never copy IDs from another
repository. Required keys are `owner`, `repo`, `project_number`, `project_id`, `status_field_id`, and
the configured status option IDs.

## Select or create

Fetch the complete project item set before selecting or resolving an issue. Start with a limit comfortably
above the current project size and compare the returned count with `totalCount`:

```bash
gh project item-list <project_number> --owner <owner> --limit 1000 --format json \
  --jq '{returned: (.items | length), total: .totalCount}'
```

If `returned` is smaller than `total`, increase the limit and repeat. Treat a partial item list as an error;
do not infer that an issue is absent from it.

List active project items from the complete result:

```bash
gh project item-list <project_number> --owner <owner> --limit 1000 --format json \
  --jq '.items[] | select(.status == "Now" or .status == "Next" or .status == "Backlog" or .status == "Inbox") | "#\(.content.number) [\(.status)] \(.content.title)"'
```

Use an existing issue only for the same deliverable. When there is no exact match, create an issue
using the issue template, add it to the project, and retain the returned project item ID:

```bash
gh issue create --repo <repo> --title "<title>" --label "<labels>" --body-file <body-file>
gh project item-add <project_number> --owner <owner> --url <issue-url> --format json --jq .id
```

If issue creation succeeds but a later project operation fails, resume from the created issue URL. Search
for that exact URL before adding it again; never create a replacement issue merely because the item ID was
lost.

Infer a concise title and labels from repository conventions when they are unambiguous. Ask when the
choice would materially change scope or ownership.

## Track status

Set a configured status:

```bash
gh project item-edit \
  --project-id <project_id> \
  --field-id <status_field_id> \
  --id <item_id> \
  --single-select-option-id <status_option_id>
```

Resolve an unknown item ID from the complete project result using the full issue URL:

```bash
gh project item-list <project_number> --owner <owner> --limit 1000 --format json \
  --jq '.items[] | select(.content.url == "https://github.com/<repo>/issues/<issue_number>") | .id'
```

Require exactly one matching item. An issue number alone is not an identity because one project can contain
issues from multiple repositories; use the full URL or node ID for all lookups and comparisons.

Move qualifying work to `Now` before implementation. Leave the issue open while integration or
required manual checks remain. After integration and verification, close it and confirm `Done`:

```bash
gh api -X PATCH /repos/<repo>/issues/<issue_number> \
  -f state=closed -f state_reason=completed
```

If work is abandoned, close it as `not_planned` and use the configured `Dropped` status. Do not close
or drop merely related issues.

## Keep the issue current

Before changing scope, re-read the issue:

```bash
gh issue view <issue_number> --repo <repo> --json number,title,body,state,labels,url
```

Update the exact issue when architecture, scope, acceptance criteria, or verification expectations
materially change. Preserve useful context and links. If a nearby issue remains independently useful,
keep both and add parent/sub-task or related links; ask only when that relationship is genuinely
ambiguous.

For an architecture spec or plan, use:

```text
.agents/tmp/specs/YYYY-MM-DD-issue-N-<topic>-design.md
.agents/tmp/plans/YYYY-MM-DD-issue-N-<topic>.md
```

Repository instructions override these defaults. Put this immediately below the document title:

```markdown
> Issue: [#N](https://github.com/<owner>/<repo>/issues/N)
```
