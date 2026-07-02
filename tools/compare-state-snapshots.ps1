param(
    [Parameter(Mandatory = $true)]
    [string]$Before,

    [Parameter(Mandatory = $true)]
    [string]$After,

    [int]$Top = 60
)

function Convert-HexToBytes {
    param([string]$Hex)
    if ([string]::IsNullOrWhiteSpace($Hex)) { return @() }
    return $Hex -split '\s+' | Where-Object { $_ } | ForEach-Object {
        [Convert]::ToByte($_, 16)
    }
}

function Read-FirstSnapshot {
    param([string]$Path)
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try {
            $event = $line | ConvertFrom-Json
        } catch {
            continue
        }
        if ($event.eventKind -ne "state_snapshot" -or -not $event.payloadHex) { continue }
        return [pscustomobject]@{
            Timestamp = [int64]$event.timestamp
            ActiveSlot = $event.parsed.activeSlot
            Bytes = @(Convert-HexToBytes $event.payloadHex)
        }
    }
    throw "Nenhum state_snapshot encontrado em $Path"
}

$beforeSnapshot = Read-FirstSnapshot $Before
$afterSnapshot = Read-FirstSnapshot $After
$limit = [Math]::Min($beforeSnapshot.Bytes.Count, $afterSnapshot.Bytes.Count)
$changes = @()

for ($offset = 0; $offset -lt $limit; $offset++) {
    if ($beforeSnapshot.Bytes[$offset] -eq $afterSnapshot.Bytes[$offset]) { continue }
    $changes += [pscustomobject]@{
        Offset = $offset
        BodyOffset = $offset - 8
        Before = ("{0:X2}" -f $beforeSnapshot.Bytes[$offset])
        After = ("{0:X2}" -f $afterSnapshot.Bytes[$offset])
        BeforeDec = $beforeSnapshot.Bytes[$offset]
        AfterDec = $afterSnapshot.Bytes[$offset]
    }
}

Write-Output "Before: $Before"
Write-Output "After : $After"
Write-Output "Snapshots: $($beforeSnapshot.Timestamp) -> $($afterSnapshot.Timestamp)"
Write-Output "Slots: $($beforeSnapshot.ActiveSlot) -> $($afterSnapshot.ActiveSlot)"
Write-Output "Changed offsets: $($changes.Count)"
Write-Output ""
$changes |
    Select-Object -First $Top |
    ForEach-Object {
        "offset={0,4} body={1,4} {2}->{3} ({4}->{5})" -f $_.Offset, $_.BodyOffset, $_.Before, $_.After, $_.BeforeDec, $_.AfterDec
    }
