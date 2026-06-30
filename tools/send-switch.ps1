. "C:\CODEX\TONEX\tools\pedal-io.ps1"

# Monta o comando de troca de preset descoberto na captura:
# B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 [PRESET] [PHASE]
function Switch-Payload([int]$preset, [int]$phase) {
    return [byte[]]@(0xB9,0x03,0x81,0x00,0x03, 0x82,0x06,0x00, 0x80,0x0B,0x03, 0xB9,0x04, 0x0B,0x01, [byte]$preset, [byte]$phase)
}

try { $sp = Open-Pedal "COM4" } catch { Write-Output "ERRO COM4: $($_.Exception.Message)"; return }
Write-Output "COM4 aberta. Enviando trocas (observe o LED/preset do pedal)..."
Start-Sleep -Milliseconds 800

foreach ($preset in @(0x0C, 0x08, 0x07)) {
    Write-Output ("`n>>>>> ENVIANDO TROCA PARA O PRESET 0x{0:X2} - OLHE O PEDAL AGORA <<<<<" -f $preset)
    foreach ($phase in @(0x00, 0x01)) {
        $frame = Encode-Hdlc (Switch-Payload $preset $phase)
        $sp.Write($frame, 0, $frame.Length)
        Write-Output ("  -> preset=0x{0:X2} phase=0x{1:X2}  | {2}" -f $preset, $phase, (Hex $frame))
        Start-Sleep -Milliseconds 400
    }
    Write-Output "     (mantendo 4s neste preset...)"
    Start-Sleep -Milliseconds 4000
}

$sp.Close()
Write-Output "COM4 fechada. O LED/preset do pedal mudou em algum momento?"