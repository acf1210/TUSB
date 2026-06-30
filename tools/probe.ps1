. "C:\CODEX\TONEX\tools\pedal-io.ps1"

try { $sp = Open-Pedal "COM4" } catch {
    Write-Output "ERRO ao abrir COM4: $($_.Exception.Message)"; return
}
Write-Output "COM4 aberta. Escutando passivamente 2s (sem enviar nada)..."
$pre = Read-For $sp 2000
Write-Output ("  passivo: {0} bytes" -f $pre.Length)
if ($pre.Length) { Write-Output ("  {0}" -f (Hex $pre[0..([Math]::Min(60,$pre.Length-1))])) }

$hello = Encode-Hdlc ([byte[]]@(0xB9,0x03,0x81,0x03,0x00))
for ($try = 1; $try -le 3; $try++) {
    Write-Output ("-> Hello tentativa {0}" -f $try)
    $sp.Write($hello, 0, $hello.Length)
    $r = Read-For $sp 1200
    Write-Output ("   <- {0} bytes" -f $r.Length)
    if ($r.Length) {
        Write-Output ("   {0}" -f (Hex $r[0..([Math]::Min(80,$r.Length-1))]))
        break
    }
}

# tenta togglar DTR/RTS
Write-Output "-> Toggle DTR off/on + RequestState"
$sp.DtrEnable = $false; Start-Sleep -Milliseconds 200; $sp.DtrEnable = $true; Start-Sleep -Milliseconds 300
$req = Encode-Hdlc ([byte[]]@(0x81,0x06,0x03))
$sp.Write($req, 0, $req.Length)
$r2 = Read-For $sp 1500
Write-Output ("   <- {0} bytes" -f $r2.Length)
if ($r2.Length) { Write-Output ("   {0}" -f (Hex $r2[0..([Math]::Min(80,$r2.Length-1))])) }

$sp.Close()
Write-Output "COM4 fechada."