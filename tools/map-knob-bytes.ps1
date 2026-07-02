param(
    [Parameter(Mandatory = $true)]
    [string]$Path,

    [int]$Top = 40
)

function Convert-HexToBytes {
    param([string]$Hex)
    if ([string]::IsNullOrWhiteSpace($Hex)) { return @() }
    return $Hex -split '\s+' | Where-Object { $_ } | ForEach-Object {
        [Convert]::ToByte($_, 16)
    }
}

$snapshots = @()
Get-Content -LiteralPath $Path | ForEach-Object {
    if ([string]::IsNullOrWhiteSpace($_)) { return }
    try {
        $event = $_ | ConvertFrom-Json
    } catch {
        return
    }
    if ($event.eventKind -ne "state_snapshot" -or -not $event.payloadHex) { return }
    $snapshots += [pscustomobject]@{
        Timestamp = [int64]$event.timestamp
        ActiveSlot = $event.parsed.activeSlot
        Bytes = @(Convert-HexToBytes $event.payloadHex)
    }
}

if ($snapshots.Count -lt 2) {
    Write-Error "Preciso de pelo menos 2 state_snapshot no JSONL. Encontrados: $($snapshots.Count)"
    exit 1
}

$changes = @{}
$transitions = @()
for ($i = 1; $i -lt $snapshots.Count; $i++) {
    $before = $snapshots[$i - 1]
    $after = $snapshots[$i]
    $limit = [Math]::Min($before.Bytes.Count, $after.Bytes.Count)
    for ($offset = 0; $offset -lt $limit; $offset++) {
        if ($before.Bytes[$offset] -eq $after.Bytes[$offset]) { continue }
        $key = $offset.ToString()
        if (-not $changes.ContainsKey($key)) {
            $changes[$key] = [ordered]@{
                Offset = $offset
                Count = 0
                Values = New-Object System.Collections.Generic.HashSet[string]
            }
        }
        $changes[$key].Count++
        [void]$changes[$key].Values.Add(("{0:X2}->{1:X2}" -f $before.Bytes[$offset], $after.Bytes[$offset]))
        $transitions += [pscustomobject]@{
            Pair = "$($i - 1)->$i"
            Timestamp = $after.Timestamp
            Offset = $offset
            Before = ("{0:X2}" -f $before.Bytes[$offset])
            After = ("{0:X2}" -f $after.Bytes[$offset])
            ActiveSlot = "$($before.ActiveSlot)->$($after.ActiveSlot)"
        }
    }
}

Write-Output "Snapshots: $($snapshots.Count)"
Write-Output "Offsets que mudaram: $($changes.Count)"
Write-Output ""
Write-Output "=== Offsets mais instaveis ==="
$changes.Values |
    Sort-Object -Property @{ Expression = "Count"; Descending = $true }, @{ Expression = "Offset"; Descending = $false } |
    Select-Object -First $Top |
    ForEach-Object {
        $values = ($_.Values | Sort-Object) -join ", "
        "{0,4}  count={1,-3} values={2}" -f $_.Offset, $_.Count, $values
    }

Write-Output ""
Write-Output "=== Transicoes ==="
$transitions |
    Sort-Object Timestamp, Offset |
    ForEach-Object {
        "pair={0,-5} t={1} offset={2,4} {3}->{4} slot={5}" -f $_.Pair, $_.Timestamp, $_.Offset, $_.Before, $_.After, $_.ActiveSlot
    }
