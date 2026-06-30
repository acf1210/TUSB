# Histograma de tipos de mensagem nos frames IN + todos os nomes de preset (0x0304) na ordem.
$b = [System.IO.File]::ReadAllBytes("C:\CODEX\TONEX\tonex_full_session.pcap")
$pos = 24
$types = @{}
$names = New-Object System.Collections.ArrayList
while ($pos + 16 -le $b.Length) {
    $inclLen  = [BitConverter]::ToUInt32($b, $pos + 8)
    $recStart = $pos + 16
    if ($recStart + $inclLen -gt $b.Length) { break }
    $headerLen = [BitConverter]::ToUInt16($b, $recStart + 0)
    $endpoint  = $b[$recStart + 21]
    $transfer  = $b[$recStart + 22]
    $dataLen   = [BitConverter]::ToUInt32($b, $recStart + 23)
    $payStart  = $recStart + $headerLen
    if ($transfer -eq 3 -and ($endpoint -band 0x80) -and $dataLen -ge 7 -and ($payStart + $dataLen) -le ($recStart + $inclLen)) {
        $pl = $b[$payStart..($payStart + $dataLen - 1)]
        if ($pl[0] -eq 0x7E -and $pl[1] -eq 0xB9 -and $pl[3] -eq 0x81) {
            $type = $pl[4] -bor ($pl[5] -shl 8)
            $key = "0x{0:X4}" -f $type
            $types[$key] = 1 + ($types[$key] | ForEach-Object { $_ })
            if ($type -eq 0x0304) {
                for ($i = 6; $i -lt ($pl.Length - 1); $i++) {
                    if ($pl[$i] -eq 0xBC) {
                        $len = $pl[$i+1]
                        $sb = New-Object System.Text.StringBuilder
                        for ($j = $i+2; $j -lt ($i+2+$len) -and $j -lt $pl.Length; $j++) {
                            $c = $pl[$j]; if ($c -ge 0x20 -and $c -le 0x7E) { [void]$sb.Append([char]$c) }
                        }
                        [void]$names.Add($sb.ToString().Trim()); break
                    }
                }
            }
        }
    }
    $pos = $recStart + $inclLen
}
Write-Output "=== Tipos de mensagem (IN) ==="
$types.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Output ("  {0} : {1}" -f $_.Key, $_.Value) }
Write-Output "`n=== Nomes de preset (0x0304) na ordem de chegada ==="
$i = 0; foreach ($n in $names) { Write-Output ("  [{0}] '{1}'" -f $i, $n); $i++ }