. "C:\CODEX\TONEX\tools\pedal-io.ps1"
$hello = Encode-Hdlc ([byte[]]@(0xB9,0x03,0x81,0x03,0x00))

function Try-Combo([bool]$dtr, [bool]$rts, [int]$warmup) {
    Write-Output ("--- DTR={0} RTS={1} warmup={2}ms ---" -f $dtr,$rts,$warmup)
    try {
        $sp = New-Object System.IO.Ports.SerialPort "COM4", 115200, None, 8, One
        $sp.ReadTimeout = 500; $sp.WriteTimeout = 500
        $sp.DtrEnable = $dtr; $sp.RtsEnable = $rts
        $sp.Open()
    } catch { Write-Output "  ERRO open: $($_.Exception.Message)"; return }
    Start-Sleep -Milliseconds $warmup
    # passivo curto
    $p = Read-For $sp 400
    if ($p.Length) { Write-Output ("  passivo: {0} bytes -> {1}" -f $p.Length, (Hex $p[0..([Math]::Min(40,$p.Length-1))])) }
    $sp.Write($hello,0,$hello.Length)
    $r = Read-For $sp 2500
    if ($r.Length) {
        Write-Output ("  <- {0} bytes" -f $r.Length)
        Write-Output ("  {0}" -f (Hex $r[0..([Math]::Min(80,$r.Length-1))]))
    } else { Write-Output "  <- 0 bytes" }
    $sp.Close()
    Start-Sleep -Milliseconds 300
}

Try-Combo $true  $true  1500
Try-Combo $true  $false 1500
Try-Combo $false $false 1500