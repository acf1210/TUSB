param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$line = Get-Content -LiteralPath $Path |
    Where-Object { $_ -match '"eventKind":"state_snapshot"' } |
    Select-Object -First 1

if (-not $line) {
    Write-Error "Nenhum state_snapshot encontrado em $Path"
    exit 1
}

$event = $line | ConvertFrom-Json
$bytes = $event.payloadHex -split '\s+'

Write-Output "Snapshot: $($event.timestamp)"
Write-Output "Slot: $($event.parsed.activeSlot)"
Write-Output "Bytes: $($bytes.Count)"
Write-Output ""

for ($i = 0; $i -lt $bytes.Count; $i++) {
    "{0,4} body={1,4} {2}" -f $i, ($i - 8), $bytes[$i]
}
