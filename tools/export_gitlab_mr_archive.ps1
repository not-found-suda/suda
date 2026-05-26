param(
    [string]$ApiBase = "https://lab.ssafy.com/api/v4",
    [string]$ProjectPath = "s14-final/S14P31A404",
    [string]$AuditCsv = ".migration\mr-audit\gitlab-mr-audit.csv",
    [string]$OutDir = "docs\gitlab-mr-archive",
    [ValidateSet("All", "MissingOnly")]
    [string]$Scope = "All",
    [switch]$IncludeNotes,
    [switch]$IncludeDiff
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function ConvertTo-PlainText {
    param([System.Security.SecureString]$SecureString)

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Escape-MarkdownCell {
    param([string]$Text)

    if ($null -eq $Text) {
        return ""
    }
    $Text.Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Escape-CodeFence {
    param([string]$Text)

    if ($null -eq $Text) {
        return ""
    }
    $Text.Replace('```', '`` `')
}

function Invoke-GitLabTextResponse {
    param(
        [string]$Uri,
        [hashtable]$Headers
    )

    $client = New-Object System.Net.WebClient
    try {
        foreach ($key in $Headers.Keys) {
            $client.Headers[[string]$key] = [string]$Headers[$key]
        }
        $bytes = $client.DownloadData($Uri)
        [pscustomobject]@{
            Text = [System.Text.Encoding]::UTF8.GetString($bytes)
            Headers = $client.ResponseHeaders
        }
    } finally {
        $client.Dispose()
    }
}

function Invoke-GitLabJson {
    param(
        [string]$Uri,
        [hashtable]$Headers
    )

    $response = Invoke-GitLabTextResponse -Uri $Uri -Headers $Headers
    $response.Text | ConvertFrom-Json
}

function Save-MergeRequestDiff {
    param(
        [string]$ApiBase,
        [string]$ProjectId,
        [int]$Iid,
        [hashtable]$Headers,
        [string]$OutFile
    )

    $payload = Invoke-GitLabJson -Uri "$ApiBase/projects/$ProjectId/merge_requests/$Iid/changes" -Headers $Headers
    $lines = New-Object System.Collections.Generic.List[string]

    if ($payload.changes) {
        foreach ($change in $payload.changes) {
            $oldPath = [string]$change.old_path
            $newPath = [string]$change.new_path
            $oldDisplay = if ($change.new_file) { "/dev/null" } else { "a/$oldPath" }
            $newDisplay = if ($change.deleted_file) { "/dev/null" } else { "b/$newPath" }

            $lines.Add("diff --git a/$oldPath b/$newPath")
            $lines.Add("--- $oldDisplay")
            $lines.Add("+++ $newDisplay")
            if ($change.diff) {
                foreach ($line in ([string]$change.diff -split "`n")) {
                    $lines.Add($line.TrimEnd("`r"))
                }
            }
            $lines.Add("")
        }
    } else {
        $lines.Add("# No diff returned by GitLab changes API for MR !$Iid.")
    }

    $lines | Set-Content -Encoding UTF8 $OutFile
}

function Get-AllPages {
    param(
        [string]$Uri,
        [hashtable]$Headers
    )

    $all = New-Object System.Collections.Generic.List[object]
    $page = 1
    while ($true) {
        $pagedUri = "$Uri&page=$page"
        $response = Invoke-GitLabTextResponse -Uri $pagedUri -Headers $Headers
        $parsed = $response.Text | ConvertFrom-Json
        $items = @()
        if ($parsed -is [System.Array]) {
            $items = $parsed
        } elseif ($null -ne $parsed) {
            $items = @($parsed)
        }

        foreach ($item in $items) {
            $all.Add($item)
        }

        $nextPage = [string]$response.Headers["X-Next-Page"]
        if ([string]::IsNullOrWhiteSpace($nextPage)) {
            break
        }
        $page = [int]$nextPage
    }

    $all
}

$token = $env:GITLAB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $secureToken = Read-Host "GitLab token (input hidden)" -AsSecureString
    $token = ConvertTo-PlainText $secureToken
}

if ([string]::IsNullOrWhiteSpace($token)) {
    throw "GITLAB_TOKEN is empty."
}

$headers = @{ "PRIVATE-TOKEN" = $token }
$projectId = [uri]::EscapeDataString($ProjectPath)

if (-not (Test-Path $AuditCsv)) {
    throw "Audit CSV not found: $AuditCsv. Run tools/audit_gitlab_mrs.ps1 first."
}

$auditRows = Import-Csv $AuditCsv
if ($Scope -eq "MissingOnly") {
    $auditRows = $auditRows | Where-Object { $_.classification -eq "metadata-or-patch-only" }
}
$auditByIid = @{}
foreach ($row in $auditRows) {
    $auditByIid[[int]$row.iid] = $row
}

New-Item -ItemType Directory -Force $OutDir | Out-Null
$mrDir = Join-Path $OutDir "mrs"
$diffDir = Join-Path $OutDir "diffs"
New-Item -ItemType Directory -Force $mrDir | Out-Null
if ($IncludeDiff) {
    New-Item -ItemType Directory -Force $diffDir | Out-Null
}

Write-Host "Fetching merge requests..."
$mrs = Get-AllPages -Uri "$ApiBase/projects/$projectId/merge_requests?state=all&scope=all&per_page=100" -Headers $headers |
    Where-Object { $auditByIid.ContainsKey([int]$_.iid) } |
    Sort-Object iid

$indexRows = New-Object System.Collections.Generic.List[object]

foreach ($mr in $mrs) {
    $iid = [int]$mr.iid
    $audit = $auditByIid[$iid]
    $fileName = "MR-{0:D4}.md" -f $iid
    $filePath = Join-Path $mrDir $fileName
    $diffPath = Join-Path $diffDir ("MR-{0:D4}.diff" -f $iid)

    Write-Host "Archiving MR !$iid..."

    $notes = @()
    if ($IncludeNotes) {
        $notesUri = "$ApiBase/projects/$projectId/merge_requests/$iid/notes?per_page=100"
        $notes = Get-AllPages -Uri $notesUri -Headers $headers | Sort-Object created_at
    }

    if ($IncludeDiff) {
        Save-MergeRequestDiff -ApiBase $ApiBase -ProjectId $projectId -Iid $iid -Headers $headers -OutFile $diffPath
    }

    $labels = ""
    if ($mr.labels) {
        $labels = ($mr.labels -join ", ")
    }

    $assignees = ""
    if ($mr.assignees) {
        $assignees = (($mr.assignees | ForEach-Object { $_.username }) -join ", ")
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# GitLab MR !$iid - $($mr.title)")
    $lines.Add("")
    $lines.Add("| Field | Value |")
    $lines.Add("| --- | --- |")
    $lines.Add("| GitLab URL | $($mr.web_url) |")
    $lines.Add("| State | $($mr.state) |")
    $lines.Add("| Source | $($mr.source_branch) |")
    $lines.Add("| Target | $($mr.target_branch) |")
    $lines.Add("| Author | $($mr.author.username) |")
    $lines.Add("| Assignees | $assignees |")
    $lines.Add("| Labels | $labels |")
    $lines.Add("| Created | $($mr.created_at) |")
    $lines.Add("| Updated | $($mr.updated_at) |")
    $lines.Add("| Merged | $($mr.merged_at) |")
    $lines.Add("| Closed | $($mr.closed_at) |")
    $lines.Add("| Head SHA | $($audit.head_sha) |")
    $lines.Add("| Migration Classification | $($audit.classification) |")
    $lines.Add("| GitHub Source Branch | $($audit.github_source_branch) |")
    $lines.Add("| GitHub Recovered Branch | $($audit.github_recovered_branch) |")
    if ($IncludeDiff) {
        $relativeDiff = "../diffs/" + ("MR-{0:D4}.diff" -f $iid)
        $lines.Add("| Diff Archive | [$relativeDiff]($relativeDiff) |")
    }
    $lines.Add("")
    $lines.Add("## Description")
    $lines.Add("")
    if ([string]::IsNullOrWhiteSpace($mr.description)) {
        $lines.Add("_No description._")
    } else {
        $lines.Add($mr.description)
    }
    $lines.Add("")

    if ($IncludeNotes) {
        $lines.Add("## Notes")
        $lines.Add("")
        if ($notes.Count -eq 0) {
            $lines.Add("_No notes._")
        } else {
            foreach ($note in $notes) {
                $kind = if ($note.system) { "system" } else { "comment" }
                $lines.Add("### $($note.author.username) at $($note.created_at) ($kind)")
                $lines.Add("")
                $lines.Add('```text')
                $lines.Add((Escape-CodeFence $note.body))
                $lines.Add('```')
                $lines.Add("")
            }
        }
    }

    $lines | Set-Content -Encoding UTF8 $filePath

    $indexRows.Add([pscustomobject]@{
        iid = $iid
        state = $mr.state
        classification = $audit.classification
        source = $mr.source_branch
        target = $mr.target_branch
        title = $mr.title
        file = "mrs/$fileName"
        url = $mr.web_url
    })
}

$indexPath = Join-Path $OutDir "index.md"
$summary = $indexRows | Group-Object classification | Sort-Object Name
$index = New-Object System.Collections.Generic.List[string]
$index.Add("# GitLab MR Archive")
$index.Add("")
$index.Add("Project: ``$ProjectPath``")
$index.Add("")
$index.Add("Total archived MRs: $($indexRows.Count)")
$index.Add("")
$index.Add("## Classification Counts")
$index.Add("")
$index.Add("| Classification | Count |")
$index.Add("| --- | ---: |")
foreach ($item in $summary) {
    $index.Add("| $($item.Name) | $($item.Count) |")
}
$index.Add("")
$index.Add("## Merge Requests")
$index.Add("")
$index.Add("| MR | State | Classification | Source | Target | Title |")
$index.Add("| ---: | --- | --- | --- | --- | --- |")
foreach ($row in $indexRows) {
    $title = Escape-MarkdownCell $row.title
    $source = Escape-MarkdownCell $row.source
    $target = Escape-MarkdownCell $row.target
    $index.Add("| !$($row.iid) | $($row.state) | $($row.classification) | $source | $target | [$title]($($row.file)) |")
}
$index | Set-Content -Encoding UTF8 $indexPath

$indexCsv = Join-Path $OutDir "index.csv"
$indexRows | Export-Csv -NoTypeInformation -Encoding UTF8 $indexCsv

Write-Host ""
Write-Host "Wrote:"
Write-Host "  $indexPath"
Write-Host "  $indexCsv"
Write-Host "  $mrDir"
if ($IncludeDiff) {
    Write-Host "  $diffDir"
}
