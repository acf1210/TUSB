. "C:\CODEX\TONEX\tools\pedal-io.ps1"

try {
    $sp = Open-Pedal "COM4"
} catch {
    Write-Output "ERRO ao abrir COM4: $($_.Exception.Message)"
    Write-Output ">> Provavelmente o app oficial do ToneX ainda esta aberto. Feche-o e tente de novo."
    return
}
Write-Output "COM4 aberta (115200 8N1, DTR/RTS on)."

# Hello
$hello = Encode-Hdlc ([byte[]]@(0xB9,0x03,0x81,0x03,0x00))
$sp.Write($hello, 0, $hello.Length)
Write-Output ("-> Hello: {0}" -f (Hex $hello))
Start-Sleep -Milliseconds 200

# RequestState (0x81 0x06 0x03)
$req = Encode-Hdlc ([byte[]]@(0x81,0x06,0x03))
$sp.Write($req, 0, $req.Length)
Write-Output ("-> RequestState: {0}" -f (Hex $req))

$resp = Read-For $sp 1500
Write-Output ("<- Recebidos {0} bytes." -f $resp.Length)
$frames = Split-Frames $resp
Write-Output ("<- {0} frames HDLC." -f $frames.Count)

$i = 0
foreach ($f in $frames) {
    $t = Get-MsgType $f
    $tHex = if ($t -ge 0) { "0x{0:X4}" -f $t } else { "?" }
    $head = ($f[0..([Math]::Min(23, $f.Length-1))] | ForEach-Object { $_.ToString('X2') }) -join ' '
    Write-Output ("  frame[{0}] tipo={1} len={2}  {3}..." -f $i, $tHex, $f.Length, $head)
    # coleção de slots (BC 06 ...) dentro de um 0x0306
    if ($t -eq 0x0306) {
        for ($k = 0; $k -lt ($f.Length - 8); $k++) {
            if ($f[$k] -eq 0xBC -and $f[$k+1] -eq 0x06) {
                $ids = @()
                for ($s = 0; $s -lt 3; $s++) {
                    $lo = $f[$k+2+$s*2]; $hi = $f[$k+3+$s*2]
                    $ids += ("0x{0:X4}" -f ($lo -bor ($hi -shl 8)))
                }
                Write-Output ("      -> coleção de slots BC 06: presetIds = {0}" -f ($ids -join ', '))
                break
            }
        }
    }
    # nome de preset (0x0304)
    if ($t -eq 0x0304) {
        for ($k = 5; $k -lt ($f.Length - 2); $k++) {
            if ($f[$k] -eq 0xBC) {
                $len = $f[$k+1]; $sb = New-Object System.Text.StringBuilder
                for ($j = $k+2; $j -lt ($k+2+$len) -and $j -lt $f.Length; $j++) {
                    $c = $f[$j]; if ($c -ge 0x20 -and $c -le 0x7E) { [void]$sb.Append([char]$c) }
                }
                Write-Output ("      -> NOME do preset ativo: '{0}'" -f $sb.ToString().Trim()); break
            }
        }
    }
    $i++
}

$sp.Close()
Write-Output "COM4 fechada."