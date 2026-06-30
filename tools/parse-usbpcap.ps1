# Parser minimo de USBPcap (LinkType 249) -> extrai transfers com payload.
# Foco: bulk (transfer=3) do ToneX One para achar comando host->device de troca de preset.
param(
    [string]$Path = "C:\CODEX\TONEX\tonexfinal_official.pcap",
    [int]$TransferFilter = -1,   # -1 = todos; 3 = bulk; 2 = control; 1 = interrupt
    [int]$MinData = 1
)

$b = [System.IO.File]::ReadAllBytes($Path)
$pos = 24  # pula global header
$records = New-Object System.Collections.ArrayList

while ($pos + 16 -le $b.Length) {
    $inclLen = [BitConverter]::ToUInt32($b, $pos + 8)
    $recStart = $pos + 16
    if ($recStart + $inclLen -gt $b.Length) { break }

    # USBPCAP_BUFFER_PACKET_HEADER
    $headerLen = [BitConverter]::ToUInt16($b, $recStart + 0)
    $irpId     = [BitConverter]::ToUInt64($b, $recStart + 2)
    $status    = [BitConverter]::ToUInt32($b, $recStart + 10)
    $function  = [BitConverter]::ToUInt16($b, $recStart + 14)
    $info      = $b[$recStart + 16]
    $bus       = [BitConverter]::ToUInt16($b, $recStart + 17)
    $device    = [BitConverter]::ToUInt16($b, $recStart + 19)
    $endpoint  = $b[$recStart + 21]
    $transfer  = $b[$recStart + 22]
    $dataLen   = [BitConverter]::ToUInt32($b, $recStart + 23)

    $payStart = $recStart + $headerLen
    $payload = $null
    if ($dataLen -gt 0 -and ($payStart + $dataLen) -le ($recStart + $inclLen)) {
        $payload = $b[$payStart..($payStart + $dataLen - 1)]
    }

    $dir = if ($endpoint -band 0x80) { "IN " } else { "OUT" }
    $ttype = switch ($transfer) { 0 {"isoc"} 1 {"intr"} 2 {"ctrl"} 3 {"bulk"} default {"?$transfer"} }

    [void]$records.Add([pscustomobject]@{
        Dev=$device; Ep=("0x{0:X2}" -f $endpoint); Dir=$dir; Type=$ttype
        TypeNum=$transfer; DataLen=$dataLen; Payload=$payload
    })
    $pos = $recStart + $inclLen
}

Write-Output ("Total de records: {0}" -f $records.Count)
Write-Output "=== Resumo por device/endpoint/tipo (com payload) ==="
$records | Where-Object { $_.DataLen -ge $MinData } |
    Group-Object Dev, Ep, Dir, Type |
    Sort-Object Count -Descending |
    Select-Object Count, Name | Format-Table -AutoSize

Write-Output ""
Write-Output "=== Transfers com payload (filtro tipo=$TransferFilter) ==="
$n = 0
foreach ($r in $records) {
    if ($r.DataLen -lt $MinData) { continue }
    if ($TransferFilter -ge 0 -and $r.TypeNum -ne $TransferFilter) { continue }
    $hex = ($r.Payload | ForEach-Object { $_.ToString('X2') }) -join ' '
    Write-Output ("#{0,-4} dev={1} ep={2} {3} {4} len={5}" -f $n, $r.Dev, $r.Ep, $r.Dir, $r.Type, $r.DataLen)
    Write-Output ("      {0}" -f $hex)
    $n++
}
Write-Output ("`nTransfers exibidos: {0}" -f $n)