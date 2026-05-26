param(
    [string]$SupabaseUrl = "https://unhoulkujbtsypccpirc.supabase.co",
    [string]$PublishableKey = "sb_publishable_A0LxL11fjdQGehmIPnyPZQ_6ty7T8lK"
)

$headers = @{
    apikey = $PublishableKey
    Authorization = "Bearer $PublishableKey"
}

$tables = @(
    "lotterynet_kv",
    "lotterynet_users_state",
    "lotterynet_master_state",
    "lotterynet_results_by_day",
    "lotterynet_tickets_by_owner",
    "lotterynet_recharges_by_owner"
)

foreach ($table in $tables) {
    try {
        $url = "{0}/rest/v1/{1}?select=*&limit=1" -f $SupabaseUrl.TrimEnd("/"), $table
        $null = Invoke-RestMethod -Uri $url -Headers $headers -Method Get -TimeoutSec 15
        Write-Output "$table OK"
    } catch {
        $status = $_.Exception.Response
        if ($status) {
            Write-Output "$table FAIL $([int]$status.StatusCode)"
        } else {
            Write-Output "$table FAIL $($_.Exception.Message)"
        }
    }
}
