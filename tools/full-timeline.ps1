# Linha do tempo completa: cada OUT (comando) seguido das respostas IN ate o proximo OUT.
# Objetivo: confirmar se o sweep de presetId 0..19 e enumeracao de biblioteca (cada ARM/COMMIT
# seguido de um 0x0304 com nome) ou outra coisa.
$b = [System.IO.File]::ReadAllBytes("C:\CODEX\TONEX\tonex_isolated_switch.pcap")
$pos = 24
$events = New-Object System.Collections.ArrayList
while ($pos + 16 -le $b.Length) {
    $inclLen  = [BitConverter]::ToUInt32($b, $pos + 8)
    $recStart = $pos + 16
    if ($recStart + $inclLen -gt $b.Length) { break }
    $headerLen = [BitConverter]::ToUInt16($b, $recStart + 0)
    $endpoint  = $b[$recStart + 21]
    $transfer  = $b[$recStart + 22]
    $dataLen   = [BitConverter]::ToUInt32($b, $recStart + 23)
    $payStart  = $recStart + $headerLen
    if ($transfer -eq 3 -and $dataLen -gt 0 -and ($payStart + $dataLen) -le ($recStart + $inclLen)) {
        $pl = $b[$payStart..($payStart + $dataLen - 1)]
        $isIn = [bool]($endpoint -band 0x80)
        [void]$events.Add(@{ IsIn = $isIn; Payload = $pl; Len = $dataLen })
    }
    $pos = $recStart + $inclLen
}

$idx = 0
foreach ($e in $events) {
    $pl = $e.Payload
    if (-not $e.IsIn) {
        $hex = ($pl | ForEach-Object { $_.ToString('X2') }) -join ' '
        Write-Output ("[$idx] OUT len=$($e.Len)  $hex")
    } else {
        $type = -1
        if ($pl.Length -ge 7 -and $pl[0] -eq 0x7E -and $pl[1] -eq 0xB9 -and $pl[3] -eq 0x81) {
            $type = $pl[4] -bor ($pl[5] -shl 8)
        }
        $tag = "0x{0:X4}" -f $type
        $extra = ""
        if ($type -eq 0x0304) {
            for ($k = 6; $k -lt ($pl.Length - 2); $k++) {
                if ($pl[$k] -eq 0xBC) {
                    $len = $pl[$k+1]; $sb = New-Object System.Text.StringBuilder
                    for ($j = $k+2; $j -lt ($k+2+$len) -and $j -lt $pl.Length; $j++) {
                        $c = $pl[$j]; if ($c -ge 0x20 -and $c -le 0x7E) { [void]$sb.Append([char]$c) }
                    }
                    $extra = " NOME='$($sb.ToString().Trim())'"; break
                }
            }
        }
        Write-Output ("[$idx]   <- IN type=$tag len=$($e.Len)$extra")
    }
    $idx++
}