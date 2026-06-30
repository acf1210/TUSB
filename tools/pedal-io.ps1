# Biblioteca de I/O com o ToneX One via COM (CDC ACM) + HDLC.
# Reproduz HdlcCodec.kt (flag 7E, CRC-16/X-25, byte-stuffing) em PowerShell.

function Get-Crc16X25([byte[]]$data) {
    $crc = 0xFFFF
    foreach ($b in $data) {
        $crc = $crc -bxor ($b -band 0xFF)
        for ($i = 0; $i -lt 8; $i++) {
            if ($crc -band 1) { $crc = (($crc -shr 1) -bxor 0x8408) -band 0xFFFF }
            else { $crc = ($crc -shr 1) -band 0xFFFF }
        }
    }
    return ($crc -bxor 0xFFFF) -band 0xFFFF
}

function Encode-Hdlc([byte[]]$payload) {
    $crc = Get-Crc16X25 $payload
    $body = @($payload) + @([byte]($crc -band 0xFF), [byte](($crc -shr 8) -band 0xFF))
    $out = New-Object System.Collections.ArrayList
    [void]$out.Add([byte]0x7E)
    foreach ($b in $body) {
        $v = $b -band 0xFF
        if ($v -eq 0x7E -or $v -eq 0x7D) {
            [void]$out.Add([byte]0x7D); [void]$out.Add([byte]($v -bxor 0x20))
        } else { [void]$out.Add([byte]$v) }
    }
    [void]$out.Add([byte]0x7E)
    return [byte[]]$out.ToArray()
}

function Hex([byte[]]$d) { ($d | ForEach-Object { $_.ToString('X2') }) -join ' ' }

function Open-Pedal([string]$PortName = "COM4") {
    $sp = New-Object System.IO.Ports.SerialPort $PortName, 115200, None, 8, One
    $sp.ReadTimeout = 800
    $sp.WriteTimeout = 800
    $sp.DtrEnable = $true
    $sp.RtsEnable = $true
    $sp.Open()
    Start-Sleep -Milliseconds 150
    return $sp
}

# Le tudo que estiver no buffer por ~$Ms milissegundos.
function Read-For($sp, [int]$Ms = 1000) {
    $buf = New-Object System.Collections.ArrayList
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.ElapsedMilliseconds -lt $Ms) {
        try {
            while ($sp.BytesToRead -gt 0) { [void]$buf.Add([byte]$sp.ReadByte()) }
        } catch {}
        Start-Sleep -Milliseconds 30
    }
    return [byte[]]$buf.ToArray()
}

# Quebra um stream em frames HDLC (entre flags 7E) e faz unstuffing (sem checar CRC).
function Split-Frames([byte[]]$stream) {
    $frames = New-Object System.Collections.ArrayList
    $cur = New-Object System.Collections.ArrayList
    $inFrame = $false
    foreach ($b in $stream) {
        if ($b -eq 0x7E) {
            if ($inFrame -and $cur.Count -gt 0) {
                # unstuff
                $u = New-Object System.Collections.ArrayList
                for ($i = 0; $i -lt $cur.Count; $i++) {
                    if ($cur[$i] -eq 0x7D) { $i++; [void]$u.Add([byte]($cur[$i] -bxor 0x20)) }
                    else { [void]$u.Add([byte]$cur[$i]) }
                }
                if ($u.Count -ge 3) { [void]$frames.Add([byte[]]$u.ToArray()) }
            }
            $cur.Clear(); $inFrame = $true
        } elseif ($inFrame) { [void]$cur.Add([byte]$b) }
    }
    return $frames
}

function Get-MsgType([byte[]]$frame) {
    # frame ja sem flags, com payload+CRC. header B9 03 81 [lo] [hi]
    if ($frame.Length -ge 5 -and $frame[0] -eq 0xB9 -and $frame[2] -eq 0x81) {
        return ($frame[3] -bor ($frame[4] -shl 8))
    }
    return -1
}