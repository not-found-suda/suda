param(
    [string]$ApiBase = "https://lab.ssafy.com/api/v4",
    [string]$ProjectPath = "s14-final/S14P31A404",
    [string]$GitHubRemote = "https://github.com/skywalkbee300/suda.git",
    [string]$OutDir = ".migration\mr-audit",
    [int]$MaxPages = 50
)

$ErrorActionPreference = "Stop"

function ConvertTo-PlainText {
    param([System.Security.SecureString]$SecureString)

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Invoke-GitLines {
    param([string[]]$GitArgs)

    $output = & git @GitArgs 2>$null
    if ($LASTEXITCODE -ne 0) {
        return @()
    }
    @($output)
}

function Get-RemoteHeads {
    param([string]$Remote)

    $heads = @{}
    foreach ($line in Invoke-GitLines -GitArgs @("ls-remote", "--heads", $Remote)) {
        if ($line -match "^(?<sha>[0-9a-f]{40})\s+refs/heads/(?<name>.+)$") {
            $heads[$Matches.name] = $Matches.sha
        }
    }
    $heads
}

function Get-MrRefs {
    param([string]$Kind)

    $refs = @{}
    foreach ($line in Invoke-GitLines -GitArgs @("ls-remote", "origin", "refs/merge-requests/*/$Kind")) {
        if ($line -match "^(?<sha>[0-9a-f]{40})\s+refs/merge-requests/(?<iid>\d+)/$Kind$") {
            $refs[[int]$Matches.iid] = $Matches.sha
        }
    }
    $refs
}

function Test-LocalCommit {
    param([string]$Sha)

    if ([string]::IsNullOrWhiteSpace($Sha)) {
        return $false
    }

    $quotedSha = "$Sha^{commit}"
    $null = & cmd /c "git cat-file -e $quotedSha 2>nul"
    $LASTEXITCODE -eq 0
}

function Get-AllMergeRequests {
    param(
        [string]$ApiBase,
        [string]$ProjectId,
        [hashtable]$Headers
    )

    $all = New-Object System.Collections.Generic.List[object]
    $seenIids = @{}
    $page = 1
    while ($page -le $MaxPages) {
        $uri = "$ApiBase/projects/$ProjectId/merge_requests?state=all&scope=all&per_page=100&page=$page"
        Write-Host "Fetching MR page $page..."

        $response = Invoke-WebRequest -UseBasicParsing -Headers $Headers -Uri $uri -Method Get
        $parsed = $response.Content | ConvertFrom-Json
        $items = @()
        if ($parsed -is [System.Array]) {
            $items = $parsed
        } elseif ($null -ne $parsed) {
            $items = @($parsed)
        }
        if ($items.Count -eq 1 -and $null -eq $items[0]) {
            $items = @()
        }

        if ($items.Count -eq 0) {
            break
        }

        $newItems = 0
        foreach ($item in $items) {
            $iid = [int]$item.iid
            if (-not $seenIids.ContainsKey($iid)) {
                $seenIids[$iid] = $true
                $all.Add($item)
                $newItems++
            }
        }

        if ($newItems -eq 0) {
            Write-Host "No new MRs on page $page; stopping pagination."
            break
        }

        $nextPage = [string]$response.Headers["X-Next-Page"]
        if ([string]::IsNullOrWhiteSpace($nextPage)) {
            break
        }

        $page = [int]$nextPage
    }

    if ($page -gt $MaxPages) {
        Write-Warning "Stopped after MaxPages=$MaxPages. Increase -MaxPages if needed."
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

New-Item -ItemType Directory -Force $OutDir | Out-Null

$projectId = [uri]::EscapeDataString($ProjectPath)
$headers = @{ "PRIVATE-TOKEN" = $token }

$mrs = Get-AllMergeRequests -ApiBase $ApiBase -ProjectId $projectId -Headers $headers

Write-Host "Fetching GitHub branch list..."
$githubHeads = Get-RemoteHeads $GitHubRemote
Write-Host "Fetching GitLab MR refs..."
$mrHeadRefs = Get-MrRefs "head"
$mrMergeRefs = Get-MrRefs "merge"
Write-Host "Found GitHub heads: $($githubHeads.Count)"
Write-Host "Found GitLab MR head refs: $($mrHeadRefs.Count)"
Write-Host "Found GitLab MR merge refs: $($mrMergeRefs.Count)"

$rows = foreach ($mr in ($mrs | Sort-Object iid)) {
    $headSha = $null
    if ($mr.diff_refs -and $mr.diff_refs.head_sha) {
        $headSha = [string]$mr.diff_refs.head_sha
    } elseif ($mr.sha) {
        $headSha = [string]$mr.sha
    }

    $sourceBranch = [string]$mr.source_branch
    $recoveredBranch = "recovered/gitlab-mr-$($mr.iid)"

    $githubSourceBranchExists = $false
    if (-not [string]::IsNullOrWhiteSpace($sourceBranch)) {
        $githubSourceBranchExists = $githubHeads.ContainsKey($sourceBranch)
    }

    $githubRecoveredBranchExists = $githubHeads.ContainsKey($recoveredBranch)
    $gitlabHeadRefExists = $mrHeadRefs.ContainsKey([int]$mr.iid)
    $gitlabMergeRefExists = $mrMergeRefs.ContainsKey([int]$mr.iid)
    $headShaExistsLocally = Test-LocalCommit $headSha

    $classification = if ($githubSourceBranchExists) {
        "github-source-branch"
    } elseif ($githubRecoveredBranchExists) {
        "github-recovered-branch"
    } elseif ($gitlabHeadRefExists) {
        "gitlab-head-ref"
    } elseif ($headShaExistsLocally) {
        "local-head-sha"
    } elseif ($gitlabMergeRefExists) {
        "gitlab-merge-ref-only"
    } else {
        "metadata-or-patch-only"
    }

    [pscustomobject]@{
        iid = [int]$mr.iid
        state = [string]$mr.state
        title = [string]$mr.title
        source_branch = $sourceBranch
        target_branch = [string]$mr.target_branch
        head_sha = $headSha
        gitlab_head_ref = $gitlabHeadRefExists
        gitlab_merge_ref = $gitlabMergeRefExists
        github_source_branch = $githubSourceBranchExists
        github_recovered_branch = $githubRecoveredBranchExists
        local_head_sha = $headShaExistsLocally
        classification = $classification
        web_url = [string]$mr.web_url
    }
}

$csvPath = Join-Path $OutDir "gitlab-mr-audit.csv"
$jsonPath = Join-Path $OutDir "gitlab-mr-audit.json"
$mdPath = Join-Path $OutDir "gitlab-mr-audit-summary.md"

$rows | Export-Csv -NoTypeInformation -Encoding UTF8 $csvPath
$rows | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonPath

$summary = $rows |
    Group-Object classification |
    Sort-Object Name |
    ForEach-Object {
        [pscustomobject]@{
            classification = $_.Name
            count = $_.Count
        }
    }

$missing = $rows |
    Where-Object { $_.classification -eq "metadata-or-patch-only" } |
    Select-Object iid, state, source_branch, target_branch, title, web_url

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# GitLab MR Migration Audit")
$lines.Add("")
$lines.Add("Total merge requests: $($rows.Count)")
$lines.Add("")
$lines.Add("## Classification Counts")
$lines.Add("")
$lines.Add("| Classification | Count |")
$lines.Add("| --- | ---: |")
foreach ($item in $summary) {
    $lines.Add("| $($item.classification) | $($item.count) |")
}
$lines.Add("")
$lines.Add("## Metadata Or Patch Only")
$lines.Add("")
if ($missing.Count -eq 0) {
    $lines.Add("No MRs fell into this bucket.")
} else {
    $lines.Add("| MR | State | Source | Target | Title |")
    $lines.Add("| ---: | --- | --- | --- | --- |")
    foreach ($item in $missing) {
        $title = ([string]$item.title).Replace("|", "\|")
        $lines.Add("| !$($item.iid) | $($item.state) | $($item.source_branch) | $($item.target_branch) | [$title]($($item.web_url)) |")
    }
}

$lines | Set-Content -Encoding UTF8 $mdPath

Write-Host ""
Write-Host "Wrote:"
Write-Host "  $csvPath"
Write-Host "  $jsonPath"
Write-Host "  $mdPath"
Write-Host ""
Write-Host "Summary:"
$summary | Format-Table -AutoSize
