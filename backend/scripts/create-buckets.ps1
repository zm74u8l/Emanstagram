<#
.SYNOPSIS
    Creates the Supabase Storage buckets the app expects.

.DESCRIPTION
    posts, avatars and stories are PUBLIC so the CDN can serve them
    directly without a signed request. messages is PRIVATE, because
    chat attachments must only be reachable by participants via
    short-lived signed URLs.

    Idempotent: existing buckets are left untouched.

.PARAMETER SupabaseUrl
    Project URL, e.g. https://abcdefgh.supabase.co

.PARAMETER ServiceRoleKey
    The sb_secret_ (or legacy service_role) key. NEVER commit this.

.EXAMPLE
    .\scripts\create-buckets.ps1 -SupabaseUrl https://abc.supabase.co -ServiceRoleKey sb_secret_xxx
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SupabaseUrl,
    [Parameter(Mandatory = $true)][string]$ServiceRoleKey
)

$ErrorActionPreference = 'Stop'

# Public buckets are served straight from the CDN, so the public bucket
# flag is what makes avatarUrl/post images resolvable without a request
# to this backend on every page load.
$buckets = @(
    @{ name = 'posts';    public = $true  }
    @{ name = 'avatars';  public = $true  }
    @{ name = 'stories';  public = $true  }
    # Chat attachments are private: reachable only via a signed URL.
    @{ name = 'messages'; public = $false }
)

$headers = @{
    'Authorization' = "Bearer $ServiceRoleKey"
    'apikey'        = $ServiceRoleKey
    'Content-Type'  = 'application/json'
}

$base = $SupabaseUrl.TrimEnd('/')

foreach ($bucket in $buckets) {
    $url = "$base/storage/v1/bucket/$($bucket.name)"

    try {
        $existing = Invoke-RestMethod -Uri $url -Headers $headers -Method Get -ErrorAction Stop
        Write-Host "  exists   $($bucket.name) (public=$($existing.public))"
        continue
    } catch {
        # 404 means absent, which is the case we want to handle.
    }

    $body = @{
        id     = $bucket.name
        name   = $bucket.name
        public = $bucket.public
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri "$base/storage/v1/bucket" `
            -Headers $headers -Method Post -Body $body -ErrorAction Stop | Out-Null
        $visibility = if ($bucket.public) { 'public' } else { 'private' }
        Write-Host "  created  $($bucket.name) ($visibility)"
    } catch {
        Write-Host "  FAILED   $($bucket.name): $($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

Write-Host ''
Write-Host 'All buckets ready.' -ForegroundColor Green
