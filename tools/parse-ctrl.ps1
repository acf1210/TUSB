# Mostra os control transfers (setup packets) da captura -> revela como o app inicializa.
$b = [System.IO.File]::ReadAllBytes("C:\CODEX\TONEX\tonexfinal_official.pcap")
$pos = 24; $n = 0
while ($pos + 16 -le $b.Length) {
    $inclLen  = [BitConverter]::ToUInt32($b, $pos + 8)
    $recStart = $pos + 16
    if ($recStart + $inclLen -gt $b.Length) { break }
    $headerLen = [BitConverter]::ToUInt16($b, $recStart + 0)
    $endpoint  = $b[$recStart + 21]
    $transfer  = $b[$recStart + 22]
    $dataLen   = [BitConverter]::ToUInt32($b, $recStart + 23)
    # control: USBPcap inclui o setup (8 bytes) no header estendido OU nos primeiros bytes
    if ($transfer -eq 2) {
        # o setup de 8 bytes fica logo apos os 27 bytes base do header
        $setupOff = $recStart + 27
        if ($headerLen -ge 35 -and ($setupOff + 8) -le ($recStart + $inclLen)) {
            $bmRequestType = $b[$setupOff + 0]
            $bRequest      = $b[$setupOff + 1]
            $wValue        = [BitConverter]::ToUInt16($b, $setupOff + 2)
            $wIndex        = [BitConverter]::ToUInt16($b, $setupOff + 4)
            $wLength       = [BitConverter]::ToUInt16($b, $setupOff + 6)
            $dir = if ($endpoint -band 0x80) { "IN" } else { "OUT" }
            $reqName = switch ($bRequest) {
                0x20 {"SET_LINE_CODING"} 0x21 {"GET_LINE_CODING"} 0x22 {"SET_CONTROL_LINE_STATE"}
                0x06 {"GET_DESCRIPTOR"} 0x09 {"SET_CONFIG"} 0x0B {"SET_INTERFACE"} default {"req0x{0:X2}" -f $bRequest}
            }
            $line = "CTRL ep=0x{0:X2} {1} bmReqType=0x{2:X2} bRequest=0x{3:X2}({4}) wValue=0x{5:X4} wIndex=0x{6:X4} wLen={7}" -f $endpoint,$dir,$bmRequestType,$bRequest,$reqName,$wValue,$wIndex,$wLength
            Write-Output $line
            # SET_LINE_CODING traz 7 bytes de dados: baud(4) stopbits(1) parity(1) databits(1)
            if ($bRequest -eq 0x20 -and $dataLen -ge 7) {
                $dOff = $recStart + $headerLen
                if (($dOff+7) -le ($recStart+$inclLen)) {
                    $baud = [BitConverter]::ToUInt32($b, $dOff)
                    Write-Output ("     -> LINE_CODING baud={0} stop={1} parity={2} data={3}" -f $baud,$b[$dOff+4],$b[$dOff+5],$b[$dOff+6])
                }
            }
            $n++
        }
    }
    $pos = $recStart + $inclLen
}
Write-Output ("`nTotal control transfers: {0}" -f $n)