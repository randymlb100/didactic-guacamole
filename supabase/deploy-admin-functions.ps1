param(
    [string]$ProjectRef = "unhoulkujbtsypccpirc"
)

$ErrorActionPreference = "Stop"

$functions = @(
    "admin-users-state",
    "lotterynet-users-state",
    "admin-master-state",
    "admin-cashier-limits",
    "admin-manual-results-override",
    "results-server-refresh"
)

Write-Host "Deploying Supabase Edge Functions for project $ProjectRef..."

foreach ($fn in $functions) {
    Write-Host " -> $fn"
    supabase functions deploy $fn --project-ref $ProjectRef
}

Write-Host ""
Write-Host "Remember to set LOTTERYNET_ADMIN_SHARED_SECRET in Supabase and Render."
