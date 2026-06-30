# Linha do tempo: comandos OUT (host->device) intercalados com notificacoes IN
# que carregam nome de preset (tipo 0x0304). Confirma qual byte do comando troca o preset.
$b = [System.IO.File]::ReadAllBytes("C:\CODEX\TONEX\tonex_full_session.pcap")
$pos = 24
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
        if (-not $isIn) {
            # OUT: mostra os 2 bytes "uteis" antes do CRC
            $hex = ($pl | ForEach-Object { $_.ToString('X2') }) -join ' '
            Write-Output ("OUT  len={0,-3}  {1}" -f $dataLen, $hex)
        } else {
            # IN: se for tipo 0x0304 (preset detail), extrai nome ASCII apos 'BC <len>'
            # header B9 03 81 [tipo LE] -> bytes 4,5 = tipo. Aqui payload comeca com 7E.
            $type = -1
            if ($dataLen -ge 7 -and $pl[0] -eq 0x7E -and $pl[1] -eq 0xB9 -and $pl[3] -eq 0x81) {
                $type = $pl[4] -bor ($pl[5] -shl 8)
            }
            if ($type -eq 0x0304) {
                # acha BC <len> e le os <len> bytes ASCII
                $name = ""
                for ($i = 6; $i -lt ($pl.Length - 1); $i++) {
                    if ($pl[$i] -eq 0xBC) {
                        $len = $pl[$i+1]
                        $sb = New-Object System.Text.StringBuilder
                        for ($j = $i+2; $j -lt ($i+2+$len) -and $j -lt $pl.Length; $j++) {
                            $c = $pl[$j]
                            if ($c -ge 0x20 -and $c -le 0x7E) { [void]$sb.Append([char]$c) }
                        }
                        $name = $sb.ToString().Trim()
                        break
                    }
                }
                Write-Output ("  <- IN 0x0304 PRESET: '{0}'  (len={1})" -f $name, $dataLen)
            }
        }
    }
    $pos = $recStart + $inclLen
}