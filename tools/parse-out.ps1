# Mostra apenas transfers bulk OUT (host->device) = comandos do app oficial.
$b = [System.IO.File]::ReadAllBytes("C:\CODEX\TONEX\tonex_full_session.pcap")
$pos = 24
$idx = 0
while ($pos + 16 -le $b.Length) {
    $inclLen = [BitConverter]::ToUInt32($b, $pos + 8)
    $recStart = $pos + 16
    if ($recStart + $inclLen -gt $b.Length) { break }
    $headerLen = [BitConverter]::ToUInt16($b, $recStart + 0)
    $endpoint  = $b[$recStart + 21]
    $transfer  = $b[$recStart + 22]
    $dataLen   = [BitConverter]::ToUInt32($b, $recStart + 23)
    $payStart  = $recStart + $headerLen
    if ($transfer -eq 3 -and -not ($endpoint -band 0x80) -and $dataLen -gt 0 `
        -and ($payStart + $dataLen) -le ($recStart + $inclLen)) {
        $pl = $b[$payStart..($payStart + $dataLen - 1)]
        $hex = ($pl | ForEach-Object { $_.ToString('X2') }) -join ' '
        Write-Output ("=== OUT #{0}  ep=0x{1:X2}  len={2} ===" -f $idx, $endpoint, $dataLen)
        Write-Output $hex
        $idx++
    }
    $pos = $recStart + $inclLen
}
Write-Output ("`nTotal OUT: {0}" -f $idx)